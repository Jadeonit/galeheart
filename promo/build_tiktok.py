"""Hook-first cuts for TikTok: the text is on screen at frame zero."""

from __future__ import annotations

import json
import math
import os

from PIL import Image, ImageDraw

import build_videos as base

HERE = os.path.dirname(os.path.abspath(__file__))
SIZE = (720, 1280)
FPS = base.FPS
DURATION = 7.0

CUTS = [
    {
        "file": "tt_weather.mp4",
        "hook": "THE WEATHER",
        "hook2": "WANTS YOU DEAD",
        "sub": "acid rain in 60 minutes",
        "badge": "OPENING NOV 25",
        "bullets": ["one hour of warning", "craft the protection", "or stay under a roof"],
        "accent": (150, 30, 40),
    },
    {
        "file": "tt_hearts.mp4",
        "hook": "10 HEARTS",
        "hook2": "KILL TO TAKE ONE",
        "sub": "die and you lose one",
        "badge": "OPENING NOV 25",
        "bullets": ["20 is the ceiling", "one heart is a countdown", "trust nobody"],
        "accent": (200, 50, 75),
    },
    {
        "file": "tt_bounty.mp4",
        "hook": "EVERYONE HAS",
        "hook2": "A BOUNTY",
        "sub": "a contract with someone's name",
        "badge": "OPENING NOV 25",
        "bullets": ["kill them, take a heart", "the contract rerolls", "nobody is safe"],
        "accent": (230, 120, 60),
    },
]


def render(cut, logo):
    path = os.path.join(HERE, cut["file"])
    frame_base = base.backdrop(SIZE)
    writer = base.open_writer(path)
    accent = cut["accent"]

    for index in range(int(FPS * DURATION)):
        t = index / FPS
        frame = frame_base.copy()
        base.rain(frame, index * 12, hash(cut["file"]) % 800 + index // 4)
        draw = ImageDraw.Draw(frame)

        # hook on screen from the first frame
        draw.rectangle([0, 300, SIZE[0], 316], fill=accent)
        draw.text((SIZE[0] // 2, 420), cut["hook"], font=base.font(58), fill=(255, 255, 255), anchor="mm")
        draw.text((SIZE[0] // 2, 492), cut["hook2"], font=base.font(58), fill=accent, anchor="mm")

        if t > 0.7:
            draw.text((SIZE[0] // 2, 580), cut["sub"], font=base.font(25, bold=False),
                      fill=(215, 215, 225), anchor="mm")

        if t > 0.9:
            draw.text((SIZE[0] // 2, 642), cut["badge"], font=base.font(24), fill=accent, anchor="mm")

        for position, bullet in enumerate(cut["bullets"]):
            start = 1.6 + position * 0.55
            if t > start:
                alpha = min(1.0, (t - start) / 0.3)
                tone = int(150 + 60 * alpha)
                draw.text((SIZE[0] // 2, 712 + position * 52), bullet, font=base.font(22, bold=False),
                          fill=(tone, tone + 15, tone + 30), anchor="mm")

        if t > 4.8:
            alpha = min(1.0, (t - 4.8) / 0.6)
            pulse = 1.0 + 0.015 * math.sin(t * 3.0)
            layer = logo.resize((int(400 * pulse), int(logo.height * (400 / logo.width) * pulse)),
                                Image.LANCZOS)
            frame.alpha_composite(base.fade_in(layer, alpha), ((SIZE[0] - layer.width) // 2, 930))
            shade = int(255 * alpha)
            draw.text((SIZE[0] // 2, 1140), "DISCORD IN BIO", font=base.font(30),
                      fill=(shade, shade, shade), anchor="mm")

        base.emit(writer, frame)

    writer.close()
    events = [(0.0, "impact", 0.75)]
    for position in range(len(cut["bullets"])):
        events.append((1.6 + position * 0.55, "tick", 0.7))
    events.append((4.8, "chime", 0.5))
    print("written", path)
    return events


def main():
    logo = base.logo_image(400)
    timings = {}
    for cut in CUTS:
        timings[cut["file"]] = render(cut, logo)
    with open(os.path.join(HERE, "tiktok_timing.json"), "w", encoding="utf-8") as handle:
        json.dump(timings, handle, indent=1)
    print("timings written")


if __name__ == "__main__":
    main()
