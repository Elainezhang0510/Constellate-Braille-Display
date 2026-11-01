"""Streaming JSON compatible with Android's existing un-delimited SSID/PWD objects."""
import codecs
import json


class JSONStream:
    def __init__(self):
        self.decoder = codecs.getincrementaldecoder("utf-8")()
        self.buffer = ""

    def feed(self, data):
        try:
            self.buffer += self.decoder.decode(data)
        except UnicodeDecodeError as exc:
            self.__init__()
            raise ValueError("Invalid UTF-8") from exc
        if len(self.buffer) > 4096:
            self.__init__()
            raise ValueError("Message too long")
        objects = []
        while self.buffer.strip():
            self.buffer = self.buffer.lstrip()
            if not self.buffer.startswith("{"):
                self.__init__()
                raise ValueError("Expected Wi-Fi JSON object")
            try:
                value, end = json.JSONDecoder().raw_decode(self.buffer)
            except json.JSONDecodeError:
                # May be incomplete, including a split escape or multibyte character.
                break
            self.buffer = self.buffer[end:]
            objects.append(value)
        return objects


def credentials(value):
    if not isinstance(value, dict):
        raise ValueError("Expected object")
    ssid, password = value.get("SSID"), value.get("PWD")
    if not isinstance(ssid, str) or not isinstance(password, str):
        raise ValueError("SSID and PWD must be strings")
    if not ssid or len(ssid.encode("utf-8")) > 32:
        raise ValueError("SSID must contain 1-32 UTF-8 bytes")
    if any(ord(c) < 32 for c in ssid + password):
        raise ValueError("Control characters are not supported")
    return ssid, password
