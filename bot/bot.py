#!/usr/bin/env python3
"""GaleHeart event bot.

Receives JSON events from the Minecraft plugins over HTTP and posts them
to a Discord channel. Stdlib only, no dependencies.

    POST http://127.0.0.1:8099/event
    {"title": "Anomaly incoming", "description": "Acid rain in 1 h", "color": 3447003}
"""

from __future__ import annotations

import json
import os
import sys
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
CONFIG_PATH = os.path.join(HERE, "config.json")

DISCORD_API = "https://discord.com/api/v10"

_lock = threading.Lock()


def load_config() -> dict:
    if not os.path.exists(CONFIG_PATH):
        print(f"Missing {CONFIG_PATH}. Copy config.example.json and fill it in.")
        sys.exit(1)
    with open(CONFIG_PATH, "r", encoding="utf-8") as fh:
        return json.load(fh)


CONFIG = load_config()
TOKEN = CONFIG.get("token", "")
CHANNEL = str(CONFIG.get("channel_id", ""))
HOST = CONFIG.get("host", "127.0.0.1")
PORT = int(CONFIG.get("port", 8099))
SECRET = CONFIG.get("secret", "")


def post_discord(title: str, description: str, color: int = 3447003) -> None:
    if not TOKEN or not CHANNEL:
        print("token/channel_id are not configured", file=sys.stderr)
        return

    payload = {
        "embeds": [
            {
                "title": title[:256],
                "description": description[:4000],
                "color": int(color),
                "footer": {"text": "GaleHeart"},
            }
        ],
        "allowed_mentions": {"parse": []},
    }
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        f"{DISCORD_API}/channels/{CHANNEL}/messages",
        data=body,
        method="POST",
        headers={
            "Authorization": f"Bot {TOKEN}",
            "Content-Type": "application/json",
            "User-Agent": "GaleHeartBot/1.0",
        },
    )
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                response.read()
            return
        except urllib.error.HTTPError as error:
            if error.code == 429:
                try:
                    retry = json.loads(error.read()).get("retry_after", 1)
                except Exception:
                    retry = 1
                time.sleep(float(retry) + 0.2)
                continue
            print(f"Discord error {error.code}: {error.read()[:200]!r}", file=sys.stderr)
            return
        except Exception as error:  # network hiccup
            print(f"Discord request failed: {error}", file=sys.stderr)
            time.sleep(1)


class Handler(BaseHTTPRequestHandler):
    server_version = "GaleHeartBot/1.0"

    def _reply(self, code: int, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:  # health probe
        if self.path == "/health":
            self._reply(200, {"ok": True})
        else:
            self._reply(404, {"error": "not found"})

    def do_POST(self) -> None:
        if self.path != "/event":
            self._reply(404, {"error": "not found"})
            return
        if SECRET and self.headers.get("X-Secret", "") != SECRET:
            self._reply(401, {"error": "bad secret"})
            return

        try:
            length = int(self.headers.get("Content-Length", "0"))
            data = json.loads(self.rfile.read(length) or b"{}")
        except Exception:
            self._reply(400, {"error": "bad json"})
            return

        title = str(data.get("title", "Event"))
        description = str(data.get("description", ""))
        color = int(data.get("color", 3447003))

        with _lock:
            post_discord(title, description, color)
        self._reply(200, {"ok": True})

    def log_message(self, fmt: str, *args) -> None:
        print(f"{self.address_string()} {fmt % args}")


def main() -> None:
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"GaleHeart bot listening on http://{HOST}:{PORT}/event -> channel {CHANNEL}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("shutting down")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
