"""English braille encoding and bounded-on-time six-channel output."""
import math

# Raised dots, equivalent to all 26 rows in the supplied Arduino sketch.
DOTS = ("1", "12", "14", "145", "15", "124", "1245", "125", "24", "245",
        "13", "123", "134", "1345", "135", "1234", "12345", "1235", "234",
        "2345", "136", "1236", "2456", "1346", "13456", "1356")


def normalize_text(text):
    if any(not (c in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ" or c.isspace()) for c in text):
        raise ValueError("Only English a-z and whitespace are supported")
    text = " ".join(text.lower().split())
    if not text:
        raise ValueError("Text must contain at least one English letter")
    return text


def encode(char):
    if char == " ":
        return (0,) * 6
    if len(char) != 1 or not "a" <= char.lower() <= "z":
        raise ValueError("Expected one English letter or space")
    dots = DOTS[ord(char.lower()) - ord("a")]
    return tuple(int(str(n) in dots) for n in range(1, 7))


class SingleCell:
    """Changes never extend an active session; each session has an off interval.

    tick() must run frequently. This is not an independent hardware watchdog.
    """
    def __init__(self, gpio, pins, max_on=2.0, cooldown=2.0):
        if len(pins) != 6 or len(set(pins)) != 6:
            raise ValueError("Six distinct output pins required")
        if not all(math.isfinite(v) and v > 0 for v in (max_on, cooldown)):
            raise ValueError("On-time and cooldown must be positive and finite")
        self.gpio, self.pins = gpio, tuple(pins)
        self.max_on, self.cooldown = max_on, cooldown
        self.deadline = None
        self.ready_at = 0.0
        self.pending = None
        gpio.setup(self.pins, gpio.OUT, initial=gpio.LOW)

    def _write(self, pattern):
        self.gpio.output(self.pins, tuple(pattern))

    def request(self, pattern, now):
        pattern = tuple(pattern)
        if len(pattern) != 6 or any(v not in (0, 1) for v in pattern):
            raise ValueError("Expected six binary dot states")
        self.tick(now)
        self.pending = pattern
        if not any(pattern):
            self._write((0,) * 6)
            if self.deadline is not None:
                self.ready_at = now + self.cooldown
            self.deadline = None
            self.pending = None
        elif now >= self.ready_at:
            self._write(pattern)
            self.pending = None
            if self.deadline is None:
                self.deadline = now + self.max_on

    def tick(self, now):
        if self.deadline is not None and now >= self.deadline:
            self._write((0,) * 6)
            self.deadline = None
            self.ready_at = now + self.cooldown
        if self.pending is not None and now >= self.ready_at:
            pattern, self.pending = self.pending, None
            self.request(pattern, now)

    def close(self):
        self.pending = None
        self.deadline = None
        self._write((0,) * 6)
