"""Optional Android-compatible Firebase reader using only the standard library."""
import json
import urllib.parse
import urllib.request
from braille_cell import normalize_text


class FirebaseClient:
    def __init__(self, base_url, timeout=5.0):
        self.base_url, self.timeout = base_url.rstrip("/"), timeout

    def fetch_text(self, board_id):
        board = urllib.parse.quote(str(board_id), safe="")
        url = f"{self.base_url}/Boards/{board}/words.json"
        with urllib.request.urlopen(url, timeout=self.timeout) as response:
            data = json.load(response)
        values = data.values() if isinstance(data, dict) else data if isinstance(data, list) else []
        words = []
        for item in values:
            if isinstance(item, str):
                try:
                    words.append(normalize_text(item))
                except ValueError:
                    pass
        if not words:
            raise ValueError("Firebase returned no supported words")
        return " ".join(words)
