"""Explicit hardware test: short single-dot pulses, followed by input/LED checks."""
import argparse
import time
from braille_cell import SingleCell
from buttons import Buttons
from config import DOT_PINS, BUTTON_PINS, INTERNET_LED_PIN


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--outputs", action="store_true", help="Enable six 0.2s single-dot pulses")
    args = parser.parse_args()
    import RPi.GPIO as GPIO
    GPIO.setmode(GPIO.BCM)
    cell = None
    try:
        cell = SingleCell(GPIO, DOT_PINS, max_on=0.2, cooldown=2.0)
        buttons = Buttons(GPIO, BUTTON_PINS)
        GPIO.setup(INTERNET_LED_PIN, GPIO.OUT, initial=GPIO.LOW)
        for _ in range(3):
            GPIO.output(INTERNET_LED_PIN, GPIO.HIGH)
            time.sleep(0.2)
            GPIO.output(INTERNET_LED_PIN, GPIO.LOW)
            time.sleep(0.2)
        if args.outputs:
            for dot in range(6):
                print(f"Dot {dot + 1}, BCM {DOT_PINS[dot]}", flush=True)
                cell.request(tuple(int(i == dot) for i in range(6)), time.monotonic())
                until = time.monotonic() + 2.3
                while time.monotonic() < until:
                    cell.tick(time.monotonic())
                    time.sleep(0.005)
        print("Press all six buttons within 30 seconds.", flush=True)
        seen = set()
        until = time.monotonic() + 30
        while time.monotonic() < until and len(seen) < len(BUTTON_PINS):
            for name in buttons.poll(time.monotonic()):
                seen.add(name)
                print(name, flush=True)
            time.sleep(0.01)
        print("Missing:", sorted(set(BUTTON_PINS) - seen))
    finally:
        if cell:
            cell.close()
        GPIO.cleanup()


if __name__ == "__main__":
    main()
