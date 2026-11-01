"""Background endpoint reachability probe; Braille.py owns the GPIO4 LED."""
import threading
import urllib.error
import urllib.request
from config import NETWORK_CHECK_URL


class InternetMonitor:
    def __init__(self, url=NETWORK_CHECK_URL, interval=10.0):
        self.url, self.interval = url, interval
        self.online = False
        self.stop = threading.Event()
        self.thread = threading.Thread(target=self._run, daemon=True)

    def start(self):
        self.thread.start()

    def _run(self):
        while not self.stop.is_set():
            try:
                with urllib.request.urlopen(self.url, timeout=3) as response:
                    self.online = 200 <= response.status < 400
            except (OSError, ValueError, urllib.error.URLError):
                self.online = False
            self.stop.wait(self.interval)

    def close(self):
        self.stop.set()
