"""Pi 4B: hold BCM17 at a steady level for an unloaded multimeter test.

Disconnect BCM17 from the driver circuit before running. This script controls
only BCM17 (physical pin 11); it does not measure voltage.
"""
import argparse
import signal
import time


PIN = 17  # BCM numbering; physical header pin 11, NOT physical pin 17.


def stop(_signum, _frame):
    raise SystemExit(0)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--level", choices=("high", "low"), default="high",
        help="Steady output level until Ctrl+C (default: high)",
    )
    args = parser.parse_args()

    import RPi.GPIO as GPIO

    signal.signal(signal.SIGTERM, stop)
    GPIO.setmode(GPIO.BCM)
    configured = False
    try:
        GPIO.setup(PIN, GPIO.OUT, initial=GPIO.LOW)
        configured = True
        GPIO.output(PIN, GPIO.HIGH if args.level == "high" else GPIO.LOW)
        print("BCM17 空载测试：应已断开 GPIO 到驱动器的连线。", flush=True)
        print("万用表直流电压档：红表笔接物理第 11 脚，黑表笔接 GND（第 6 脚）。", flush=True)
        expected = "约 3.3V" if args.level == "high" else "接近 0V"
        print(f"持续输出 {args.level.upper()}，预期{expected}；按 Ctrl+C 结束。", flush=True)
        print("程序只设置电平，实际电压请读万用表。", flush=True)
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        pass
    finally:
        if configured:
            try:
                GPIO.output(PIN, GPIO.LOW)
            finally:
                GPIO.cleanup(PIN)
            print("已拉低并释放 BCM17（恢复输入模式）。", flush=True)


if __name__ == "__main__":
    main()
