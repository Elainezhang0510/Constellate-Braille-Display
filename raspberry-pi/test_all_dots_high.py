"""Pulse all six physical-PCB solenoid outputs HIGH at the same time."""

import argparse
import math
import signal
import time

from braille_cell import SingleCell
from config import DOT_PINS, DOT_PHYSICAL_PINS


def positive_bounded_seconds(value):
    try:
        seconds = float(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("seconds must be a number") from exc
    if not math.isfinite(seconds) or not 0.05 <= seconds <= 120.0:
        raise argparse.ArgumentTypeError("seconds must be between 0.05 and 120")
    return seconds


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--seconds", type=positive_bounded_seconds, default=0.5,
        help="one-shot HIGH duration, 0.05-120 seconds (default: 0.5)",
    )
    args = parser.parse_args()

    import RPi.GPIO as GPIO

    def stop_on_sigterm(*_):
        raise KeyboardInterrupt

    signal.signal(signal.SIGTERM, stop_on_sigterm)
    GPIO.setmode(GPIO.BCM)
    cell = None
    try:
        cell = SingleCell(GPIO, DOT_PINS, max_on=args.seconds, cooldown=2.0)
        if args.seconds > 2.0:
            print("WARNING: long simultaneous activation has no verified thermal rating; monitor the coils and power supply.", flush=True)
        print("J1–J6 physical pins:", DOT_PHYSICAL_PINS, flush=True)
        print("J1–J6 BCM GPIOs:", DOT_PINS, flush=True)
        print("All outputs LOW. HIGH pulse starts in 3 seconds.", flush=True)
        time.sleep(3)

        cell.request((1, 1, 1, 1, 1, 1), time.monotonic())
        print(f"All six outputs HIGH for up to {args.seconds:g} seconds.", flush=True)
        end = time.monotonic() + args.seconds + 0.05
        while time.monotonic() < end:
            cell.tick(time.monotonic())
            time.sleep(0.005)
        print("All outputs LOW.", flush=True)
    except KeyboardInterrupt:
        print("Interrupted; switching all outputs LOW.", flush=True)
    finally:
        if cell is not None:
            cell.close()
        GPIO.cleanup()


if __name__ == "__main__":
    main()
