"""Validated, line-delimited Bluetooth commands for the single-cell reader."""
import json

MAX_LINE = 512
MAX_TEXT = 128


class LineMessages:
    def __init__(self):
        self.buffer = bytearray()

    def feed(self, data):
        self.buffer.extend(data)
        if len(self.buffer) > MAX_LINE and b"\n" not in self.buffer:
            self.buffer.clear()
            raise ValueError("Message too long")
        messages = []
        while b"\n" in self.buffer:
            line, _, remainder = self.buffer.partition(b"\n")
            self.buffer = bytearray(remainder)
            if len(line) > MAX_LINE:
                raise ValueError("Message too long")
            try:
                value = json.loads(line.decode("utf-8"))
            except (UnicodeError, json.JSONDecodeError) as exc:
                raise ValueError("Invalid JSON") from exc
            if not isinstance(value, dict):
                raise ValueError("Expected JSON object")
            messages.append(value)
        if len(self.buffer) > MAX_LINE:
            self.buffer.clear()
            raise ValueError("Message too long")
        return messages


def board_command(value, reader, cell, now):
    """Apply one whitelisted action; only the existing cell controls GPIO."""
    from braille_cell import encode

    command = value.get("cmd")
    if command != "load":
        raise ValueError("Unknown command")
    raw = value.get("text")
    if not isinstance(raw, str) or len(raw) > MAX_TEXT:
        raise ValueError("Text must be at most 128 characters")
    reader.reload(raw)
    reader.mode = "text"
    char = reader.character
    dots = encode(char)
    cell.request(dots, now)
    return {"ok": True, "cmd": command, "char": char,
            "index": reader.index + 1, "total": len(reader.sequence),
            "dots": "".join(map(str, dots)),
            "state": "queued" if cell.pending is not None else "requested"}


def reply_bytes(reply):
    return (json.dumps(reply, ensure_ascii=True, separators=(",", ":")) + "\n").encode("ascii")
