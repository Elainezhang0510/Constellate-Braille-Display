"""Pi 4B port of refreshable_braille_display.ino: a single six-dot cell."""
from __future__ import annotations
import argparse
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import random
import queue
import signal
import string
import time

from braille_cell import SingleCell, encode, normalize_text
from board_protocol import board_command
from buttons import Buttons
from config import (BASE_DIR, BOARD_ID, BUTTON_PINS, DOT_PINS, FIREBASE_URL,
                    INTERNET_LED_PIN, MAX_ON_SECONDS, COOLDOWN_SECONDS)
from firebase_client import FirebaseClient
from internetLED import InternetMonitor
from speech import Speech


class Reader:
    def __init__(self, text):
        self.text = normalize_text(text)
        self.mode, self.index = "text", 0

    @property
    def sequence(self):
        return self.text if self.mode == "text" else string.ascii_lowercase

    @property
    def character(self):
        return self.sequence[self.index]

    def reload(self, text):
        self.text = normalize_text(text)
        self.index = 0

    def action(self, name):
        if name == "Next":
            self.index = (self.index + 1) % len(self.sequence)
        elif name == "Previous":
            self.index = (self.index - 1) % len(self.sequence)
        elif name == "Change":
            self.mode = "alphabet" if self.mode == "text" else "text"
            self.index = 0
        elif name == "Random":
            self.mode = "alphabet"
            self.index = random.randrange(26)
        return self.character


def load_text(path):
    return normalize_text(Path(path).read_text(encoding="utf-8-sig"))


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group()
    source.add_argument("--text", help="English text, e.g. hello")
    source.add_argument("--text-file", type=Path, help="UTF-8 text; default text.txt")
    parser.add_argument("--firebase", action="store_true", help="Sync Android words at startup/Refresh")
    parser.add_argument("--board-id", default=BOARD_ID)
    parser.add_argument("--simulate", action="store_true", help="Print cells without GPIO/network/audio")
    parser.add_argument("--mute", action="store_true")
    parser.add_argument("--bluetooth", action="store_true", help="Start paired Bluetooth SPP control and Wi-Fi setup")
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    path = args.text_file or BASE_DIR / "text.txt"
    initial = args.text if args.text is not None else load_text(path)
    reader = Reader(initial)
    if args.simulate:
        for char in reader.text:
            print(f"{char!r}: {''.join(map(str, encode(char)))} [dots 1,2,3,4,5,6]")
        return

    import RPi.GPIO as GPIO
    GPIO.setmode(GPIO.BCM)
    cell = monitor = bluetooth = None
    speaker = Speech(enabled=not args.mute)
    executor = ThreadPoolExecutor(max_workers=1)
    pending = None
    wifi_pending = None
    stop = False

    def request_stop(*_):
        nonlocal stop
        stop = True

    signal.signal(signal.SIGTERM, request_stop)
    signal.signal(signal.SIGINT, request_stop)
    try:
        cell = SingleCell(GPIO, DOT_PINS, MAX_ON_SECONDS, COOLDOWN_SECONDS)
        buttons = Buttons(GPIO, BUTTON_PINS)
        GPIO.setup(INTERNET_LED_PIN, GPIO.OUT, initial=GPIO.LOW)
        monitor = InternetMonitor()
        monitor.start()
        firebase = FirebaseClient(FIREBASE_URL)
        if args.bluetooth:
            from bluetooth_profile import BluetoothProfile
            bluetooth = BluetoothProfile()
            bluetooth.start()

        def show():
            char = reader.character
            cell.request(encode(char), time.monotonic())
            print(f"{reader.mode} {reader.index + 1}/{len(reader.sequence)}: {char!r}", flush=True)

        def refresh():
            nonlocal pending
            if args.firebase:
                if pending is None:
                    pending = executor.submit(firebase.fetch_text, args.board_id)
                    speaker.say("Refreshing")
            else:
                try:
                    reader.reload(args.text if args.text is not None else load_text(path))
                    show()
                    speaker.say("Text reloaded")
                except (OSError, ValueError) as exc:
                    print(f"Refresh failed: {exc}", flush=True)
                    speaker.say("Refresh failed")

        show()
        speaker.say("Braille display ready")
        if args.firebase:
            refresh()
        while not stop:
            now = time.monotonic()
            cell.tick(now)
            GPIO.output(INTERNET_LED_PIN, monitor.online)
            if pending is not None and pending.done():
                try:
                    text = pending.result()
                    reader.reload(text)
                    try:
                        (BASE_DIR / "firebase_words.txt").write_text(text + "\n", encoding="utf-8")
                    except OSError as exc:
                        print(f"Cache not saved: {exc}", flush=True)
                    show()
                    speaker.say("Words loaded")
                except Exception as exc:
                    print(f"Firebase refresh failed; keeping current text: {exc}", flush=True)
                    speaker.say("Refresh failed")
                pending = None
            if wifi_pending is not None and wifi_pending[0].done():
                future, client = wifi_pending
                try:
                    bluetooth.send(future.result(), client)
                except Exception as exc:
                    print(f"Wi-Fi setup failed: {type(exc).__name__}", flush=True)
                    bluetooth.send({"result": "FAIL", "IP": "<Not Set>"}, client)
                wifi_pending = None
            if bluetooth is not None:
                for _ in range(8):
                    try:
                        client, message = bluetooth.inbox.get_nowait()
                    except queue.Empty:
                        break
                    if not bluetooth.is_current(client):
                        continue
                    if "SSID" in message or "PWD" in message:
                        if wifi_pending is not None:
                            bluetooth.send({"result": "FAIL", "IP": "<Not Set>"}, client)
                        else:
                            from wifi_config import configure_wifi
                            wifi_pending = (executor.submit(configure_wifi, message), client)
                    else:
                        try:
                            reply = board_command(message, reader, cell, now)
                            # A phone-selected word wins over an older in-flight cloud refresh.
                            pending = None
                            bluetooth.send(reply, client)
                            print(f"Bluetooth {reply['cmd']}: {reply['char']!r}", flush=True)
                        except ValueError as exc:
                            bluetooth.send({"ok": False, "error": str(exc)}, client)
            for name in buttons.poll(now):
                if name == "Refresh":
                    refresh()
                elif name == "Voice":
                    show()
                    speaker.say("space" if reader.character == " " else reader.character)
                else:
                    reader.action(name)
                    show()
            time.sleep(0.01)
    finally:
        if bluetooth is not None:
            bluetooth.close()
        if cell is not None:
            cell.close()
        if monitor is not None:
            monitor.close()
        speaker.close()
        executor.shutdown(wait=False, cancel_futures=True)
        GPIO.cleanup()


if __name__ == "__main__":
    main()
