"""Optional Android SPP Wi-Fi provisioning, separate from GPIO control.

Requires a working /dev/rfcomm0, pyserial, NetworkManager, and permission to
configure wlan0. BlueZ pairing/SPP setup remains a system prerequisite.
"""
import argparse
import subprocess
import time
from wifi_protocol import JSONStream, credentials
from wifi_config import configure_wifi


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", default="/dev/rfcomm0")
    parser.add_argument("--interface", default="wlan0")
    args = parser.parse_args()
    import serial
    while True:
        try:
            with serial.Serial(args.port, baudrate=9600, timeout=0.2) as port:
                stream = JSONStream()
                last_data = time.monotonic()
                while True:
                    data = port.read(max(1, min(port.in_waiting, 4096)))
                    if not data:
                        if time.monotonic() - last_data > 5:
                            stream = JSONStream()
                        continue
                    last_data = time.monotonic()
                    try:
                        messages = stream.feed(data)
                    except ValueError:
                        stream = JSONStream()
                        messages = [None]
                    for value in messages:
                        try:
                            reply = configure_wifi(value, args.interface)
                        except (ValueError, OSError, subprocess.SubprocessError):
                            reply = {"result": "FAIL", "IP": "<Not Set>"}
                        port.write((json.dumps(reply) + "\n").encode("utf-8"))
                        print("Wi-Fi configuration:", reply["result"], flush=True)
        except serial.SerialException:
            print("Waiting for RFCOMM connection", flush=True)
            time.sleep(1)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
