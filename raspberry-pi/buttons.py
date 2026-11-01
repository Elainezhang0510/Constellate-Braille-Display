"""Active-HIGH: 3.3V -> switch -> GPIO -> external 10k resistor -> GND."""
from config import DEBOUNCE_SECONDS


class Buttons:
    def __init__(self, gpio, pins, debounce=DEBOUNCE_SECONDS):
        self.gpio, self.pins, self.debounce = gpio, dict(pins), debounce
        self.stable, self.raw, self.changed = {}, {}, {}
        for name, pin in self.pins.items():
            gpio.setup(pin, gpio.IN, pull_up_down=gpio.PUD_OFF)
            value = bool(gpio.input(pin))
            self.stable[name] = self.raw[name] = value
            self.changed[name] = 0.0

    def poll(self, now):
        events = []
        for name, pin in self.pins.items():
            value = bool(self.gpio.input(pin))
            if value != self.raw[name]:
                self.raw[name], self.changed[name] = value, now
            if value != self.stable[name] and now - self.changed[name] >= self.debounce:
                self.stable[name] = value
                if value:
                    events.append(name)
        return events
