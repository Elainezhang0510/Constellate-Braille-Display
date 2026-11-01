"""Pi 4B single-cell configuration. All GPIO numbers use BCM."""
import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
DOT_PINS = (23, 24, 10, 9, 25, 8)  # physical PCB J1-J6: dots 1,2,3,4,5,6
DOT_PHYSICAL_PINS = (16, 18, 19, 21, 22, 24)
BUTTON_PINS = {"Next": 26, "Previous": 13, "Voice": 16,
               "Random": 6, "Refresh": 5, "Change": 12}
BUTTON_PHYSICAL_PINS = (37, 33, 36, 31, 29, 32)
INTERNET_LED_PIN = 4  # physical pin 7
DEBOUNCE_SECONDS = 0.04
# Bench defaults only; not a thermal rating or a hardware watchdog.
MAX_ON_SECONDS = 90.0
COOLDOWN_SECONDS = 2.0
NETWORK_CHECK_URL = os.environ.get("BRAILLIO_CHECK_URL", "https://www.raspberrypi.com/")
FIREBASE_URL = os.environ.get("BRAILLIO_FIREBASE_URL",
    "")
BOARD_ID = os.environ.get("BRAILLIO_BOARD_ID", "1")
