"""Host-side regression tests: no GPIO package or physical outputs required."""
import io
import importlib.util
import json
from pathlib import Path
import re
import sys
import unittest
import types
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from Braille import Reader, main
from braille_cell import SingleCell, encode, normalize_text
from board_protocol import LineMessages, board_command, reply_bytes
from buttons import Buttons
from config import DOT_PINS, BUTTON_PINS, INTERNET_LED_PIN, MAX_ON_SECONDS, COOLDOWN_SECONDS
from firebase_client import FirebaseClient
from wifi_protocol import JSONStream, credentials


class FakeGPIO:
    OUT, IN, LOW, HIGH, PUD_OFF = "out", "in", 0, 1, "off"
    def __init__(self):
        self.values = {}
    def setup(self, pins, mode, initial=0, **kwargs):
        for pin in (pins if isinstance(pins, tuple) else (pins,)):
            self.values.setdefault(pin, initial)
    def output(self, pins, values):
        if isinstance(pins, int):
            self.values[pins] = values
        else:
            self.values.update(zip(pins, values))
    def input(self, pin):
        return self.values.get(pin, 0)
    def state(self):
        return tuple(self.values[p] for p in DOT_PINS)


class PortTests(unittest.TestCase):
    def test_bluetooth_lines_and_commands(self):
        payload = b'{"cmd":"load","text":"hello"}\n{"cmd":"load","text":"world"}\n'
        for cut in range(1, len(payload)):
            stream = LineMessages()
            messages = stream.feed(payload[:cut]) + stream.feed(payload[cut:])
            self.assertEqual(len(messages), 2)
        gpio = FakeGPIO()
        cell = SingleCell(gpio, DOT_PINS)
        reader = Reader("abc")
        first = board_command(messages[0], reader, cell, 0)
        self.assertEqual((first["char"], first["dots"]), ("h", "110010"))
        self.assertEqual(gpio.state(), encode("h"))
        second = board_command(messages[1], reader, cell, 0.1)
        self.assertEqual((second["char"], second["state"]), ("w", "requested"))
        self.assertEqual(reply_bytes(second)[-1:], b"\n")
        reader.action("Next")
        self.assertEqual(reader.character, "o")
        cell.close()
        self.assertEqual(gpio.state(), (0,) * 6)
        for value in ({"cmd": "gpio", "pin": 17}, {"cmd": "next"},
                      {"cmd": "load", "text": "12"}, {"cmd": "load", "text": "x" * 129}):
            with self.assertRaises(ValueError):
                board_command(value, reader, cell, 3)
        with self.assertRaises(ValueError):
            LineMessages().feed(b"x" * 513)

    def test_supplied_arduino_all_letters(self):
        source = Path(__file__).resolve().parents[1] / "reference" / "refreshable_braille_display.ino"
        rows = re.findall(r"\{([01],[01],[01],[01],[01],[01])\}", source.read_text())
        self.assertEqual(len(rows), 26)
        for index, row in enumerate(rows):
            self.assertEqual(encode(chr(97 + index)), tuple(map(int, row.split(","))))

    def test_text_validation_and_blank(self):
        self.assertEqual(normalize_text(" HeLLo\n world "), "hello world")
        self.assertEqual(encode(" "), (0,) * 6)
        for bad in ("", "123", "你好", "abc!"):
            with self.assertRaises(ValueError):
                normalize_text(bad)
        with self.assertRaises(ValueError):
            encode("?")

    def test_reader_wrap_and_modes(self):
        r = Reader("hello")
        self.assertEqual(r.action("Previous"), "o")
        self.assertEqual(r.action("Next"), "h")
        self.assertEqual(r.action("Change"), "a")
        self.assertEqual(r.action("Previous"), "z")
        r.reload("world")
        self.assertEqual(r.character, "a")
        self.assertEqual(r.action("Change"), "w")

    def test_pin_exclusivity(self):
        pins = list(DOT_PINS) + list(BUTTON_PINS.values()) + [INTERNET_LED_PIN]
        self.assertEqual(len(pins), len(set(pins)))

    def test_no_retrigger_extension_and_cooldown(self):
        gpio = FakeGPIO()
        cell = SingleCell(gpio, DOT_PINS)
        cell.request(encode("a"), 10)
        cell.request(encode("b"), 11.9)
        cell.tick(12)
        self.assertEqual(gpio.state(), (0,) * 6)
        cell.request(encode("c"), 12.1)
        self.assertEqual(gpio.state(), (0,) * 6)
        cell.tick(14)
        self.assertEqual(gpio.state(), encode("c"))
        cell.tick(16)
        self.assertEqual(gpio.state(), (0,) * 6)
        cell.tick(100)
        self.assertEqual(gpio.state(), (0,) * 6)

    def test_configured_ninety_second_limit_and_cooldown(self):
        self.assertEqual(MAX_ON_SECONDS, 90.0)
        gpio = FakeGPIO()
        cell = SingleCell(gpio, DOT_PINS, MAX_ON_SECONDS, COOLDOWN_SECONDS)
        cell.request(encode("a"), 10)
        cell.tick(99.999)
        self.assertEqual(gpio.state(), encode("a"))
        cell.request(encode("b"), 99.999)
        cell.tick(100)
        self.assertEqual(gpio.state(), (0,) * 6)
        cell.request(encode("c"), 101)
        self.assertEqual(gpio.state(), (0,) * 6)
        cell.tick(102)
        self.assertEqual(gpio.state(), encode("c"))
        cell.close()
        self.assertEqual(gpio.state(), (0,) * 6)

    def test_blank_cancels_pending_and_close_releases(self):
        gpio = FakeGPIO()
        cell = SingleCell(gpio, DOT_PINS)
        cell.request(encode("a"), 0)
        cell.tick(2)
        cell.request(encode("b"), 2.1)
        cell.request(encode(" "), 2.2)
        cell.tick(4)
        self.assertEqual(gpio.state(), (0,) * 6)
        cell.request(encode("c"), 5)
        cell.close()
        self.assertEqual(gpio.state(), (0,) * 6)

    def test_long_stall_requires_real_off_interval(self):
        gpio = FakeGPIO()
        cell = SingleCell(gpio, DOT_PINS)
        cell.request(encode("a"), 0)
        cell.request(encode("b"), 20)
        self.assertEqual(gpio.state(), (0,) * 6)
        cell.tick(21)
        self.assertEqual(gpio.state(), (0,) * 6)
        cell.tick(22)
        self.assertEqual(gpio.state(), encode("b"))

    def test_button_bounce_hold_release(self):
        gpio = FakeGPIO()
        buttons = Buttons(gpio, {"Next": 26})
        gpio.values[26] = 1
        self.assertEqual(buttons.poll(1), [])
        gpio.values[26] = 0
        self.assertEqual(buttons.poll(1.01), [])
        gpio.values[26] = 1
        buttons.poll(1.02)
        self.assertEqual(buttons.poll(1.07), ["Next"])
        self.assertEqual(buttons.poll(10), [])
        gpio.values[26] = 0
        buttons.poll(11)
        buttons.poll(11.05)
        gpio.values[26] = 1
        buttons.poll(12)
        self.assertEqual(buttons.poll(12.05), ["Next"])

    def test_held_at_start_does_not_fire(self):
        gpio = FakeGPIO()
        gpio.values[26] = 1
        buttons = Buttons(gpio, {"Next": 26})
        self.assertEqual(buttons.poll(1), [])

    def test_firebase_compatible_and_invalid_entries(self):
        payload = io.BytesIO(json.dumps({"cat": "cat", "dog": "dog", "bad": 42}).encode())
        with patch("urllib.request.urlopen", return_value=payload) as request:
            self.assertEqual(FirebaseClient("https://example.invalid").fetch_text("1"), "cat dog")
            self.assertIn("/Boards/1/words.json", request.call_args.args[0])
        with patch("urllib.request.urlopen", return_value=io.BytesIO(b"null")):
            with self.assertRaises(ValueError):
                FirebaseClient("https://example.invalid").fetch_text("1")

    def test_simulation_without_hardware(self):
        with patch("sys.stdout", new_callable=io.StringIO) as output:
            main(["--simulate", "--text", "hello"])
        self.assertIn("'h': 110010", output.getvalue())
        self.assertEqual(len(output.getvalue().splitlines()), 5)

    def test_main_exception_clears_outputs(self):
        gpio = FakeGPIO()
        gpio.BCM = 'BCM'
        gpio.setmode = lambda _: None
        cleaned = []
        gpio.cleanup = lambda: cleaned.append(True)
        rpi = types.ModuleType('RPi')
        rpi.GPIO = gpio
        with patch.dict(sys.modules, {'RPi': rpi, 'RPi.GPIO': gpio}), \
             patch('Braille.signal.signal'), patch('Braille.InternetMonitor') as monitor, \
             patch('Braille.time.sleep', side_effect=RuntimeError('stop-test')), \
             patch('sys.stdout', new_callable=io.StringIO):
            monitor.return_value.online = False
            with self.assertRaisesRegex(RuntimeError, 'stop-test'):
                main(['--text', 'hello', '--mute'])
        self.assertEqual(gpio.state(), (0,) * 6)
        self.assertTrue(cleaned)

    def test_wifi_long_message_every_split_boundary(self):
        value = {'SSID': '家里的无线网络', 'PWD': 'long-secret-' * 4}
        payload = json.dumps(value, ensure_ascii=False).encode('utf-8')
        self.assertGreater(len(payload), 50)
        for cut in range(1, len(payload)):
            stream = JSONStream()
            self.assertEqual(stream.feed(payload[:cut]), [])
            self.assertEqual(stream.feed(payload[cut:]), [value])

    def test_wifi_concatenated_messages_and_bad_input(self):
        stream = JSONStream()
        data = b'{"SSID":"one","PWD":""}\n{"SSID":"two","PWD":"password"}'
        self.assertEqual(len(stream.feed(data)), 2)
        for payload in (b'echo hello', b'{'+b'x'*4096, b'\xff'):
            with self.assertRaises(ValueError):
                JSONStream().feed(payload)
        for invalid in (None, {}, {'SSID': 'x', 'PWD': 1}, {'SSID': 'x'*33, 'PWD': ''}):
            with self.assertRaises(ValueError):
                credentials(invalid)

    def test_wifi_result_and_no_shell(self):
        path = Path(__file__).resolve().parents[1] / 'bluetooth-RPi-wifi.py'
        spec = importlib.util.spec_from_file_location('wifi_test', path)
        wifi = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(wifi)
        address = '[{"addr_info":[{"scope":"global","local":"192.168.1.23"}]}]'
        with patch.object(wifi.subprocess, 'run', side_effect=[
                types.SimpleNamespace(returncode=0), types.SimpleNamespace(stdout=address)]) as run:
            self.assertEqual(wifi.configure_wifi({'SSID': 'test', 'PWD': 'pass'}),
                             {'result': 'SUCCESS', 'IP': '192.168.1.23'})
            self.assertIsInstance(run.call_args_list[0].args[0], list)
            self.assertFalse(run.call_args_list[0].kwargs.get('shell', False))
        with patch.object(wifi.subprocess, 'run', return_value=types.SimpleNamespace(returncode=10)):
            self.assertEqual(wifi.configure_wifi({'SSID': 'test', 'PWD': ''})['result'], 'FAIL')


if __name__ == "__main__":
    unittest.main()
