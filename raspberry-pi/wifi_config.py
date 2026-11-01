"""NetworkManager Wi-Fi configuration shared by both Bluetooth entry points."""
import json
import subprocess

from wifi_protocol import credentials


def configure_wifi(value, interface="wlan0"):
    ssid, password = credentials(value)
    command = ["nmcli", "--wait", "20", "device", "wifi", "connect", ssid, "ifname", interface]
    if password:
        command += ["password", password]
    result = subprocess.run(command, capture_output=True, timeout=24, check=False)
    if result.returncode:
        return {"result": "FAIL", "IP": "<Not Set>"}
    info = subprocess.run(["ip", "-j", "-4", "addr", "show", "dev", interface],
                          capture_output=True, text=True, timeout=2, check=True)
    for device in json.loads(info.stdout):
        for address in device.get("addr_info", []):
            if address.get("scope") == "global" and address.get("local"):
                return {"result": "SUCCESS", "IP": address["local"]}
    return {"result": "FAIL", "IP": "<Not Set>"}
