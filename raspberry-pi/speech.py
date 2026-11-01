"""Offline English speech, no shell or temporary WAV files."""
import shutil
import subprocess


class Speech:
    def __init__(self, enabled=True):
        self.command = (shutil.which("espeak-ng") or shutil.which("espeak")) if enabled else None
        self.process = None
        if enabled and not self.command:
            print("Speech unavailable: install espeak-ng, or use --mute")

    def say(self, text):
        self.close()
        if self.command:
            try:
                self.process = subprocess.Popen([self.command, "-v", "en", "-s", "150", "--", text],
                                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            except OSError as exc:
                print(f"Speech unavailable: {exc}")

    def close(self):
        if self.process and self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=0.1)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=0.1)
        self.process = None
