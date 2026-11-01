#!/bin/bash
# Raspberry Pi OS on Pi 4B. Use distro packages, not pip in the system environment.
set -euo pipefail
cd "$(dirname "$0")"
sudo apt update
sudo apt install -y python3-rpi.gpio espeak-ng alsa-utils
if [[ "${1:-}" == "--with-bluetooth" ]]; then
  sudo apt install -y bluez python3-dbus python3-gi python3-serial
fi
chmod +x start.sh
echo 'Installed. First run: python3 Braille.py --simulate'
echo 'Then verify wiring and run: ./start.sh'
