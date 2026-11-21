"""Builds a batch of vertical shorts from one template, so there is one per day."""

from __future__ import annotations

import json
import math
import os

from PIL import Image, ImageDraw

import build_videos as base

HERE = os.path.dirname(os.path.abspath(__file__))
SIZE = (720, 1280)
FPS = base.FPS
DURATION = 8.0

SHORTS = [
    {
        "file": "short_acid.mp4",
        "label": "WEATHER ALERT",
        "hook": "ACID RAIN",
        "sub": "In 60 minutes. Then it lands.",
        "bullets": ["Your armour melts in the open", "Craft the acid helmet", "Or stay under a roof"],
        "accent": (150, 30, 40),
    },
    {
        "file": "short_fire.mp4",
        "label": "WEATHER ALERT",
        "hook": "FIRE RAIN",
        "sub": "The sky opens. Everything burns.",
        "bullets": ["One hour of warning", "Fire resistance or a deep cave", "Bases without a roof do not survive"],
        "accent": (196, 84, 24),
    },
    {
        "file": "short_blizzard.mp4",
        "label": "WEATHER ALERT",
        "hook": "BLIZZARD",
        "sub": "Freezes you mid-fight.",
        "bullets": ["Movement slows to a crawl", "Warm gear or a sealed room", "Chase someone into the storm"],
        "accent": (44, 110, 150),
    },
    {
        "file": "short_fog.mp4",
        "label": "WEATHER ALERT",
        "hook": "TOXIC FOG",
        "sub": "Poison that follows you inside.",
        "bullets": ["Masks are craftable", "Vents do not save you", "The fog does not care who you are"],
        "accent": (96, 138, 60),
    },
    {
        "file": "short_hearts.mp4",
        "label": "LIFESTEAL",
        "hook": "10 HEARTS",
        "sub": "Kill to take one. Die to lose one.",
        "bullets": ["20 is the ceiling", "One heart left means a slow death", "Trust nobody"],
        "accent": (200, 50, 75),
    },
    {
        "file": "short_teleports.mp4",
        "label": "NO TELEPORTS",
        "hook": "NO /HOME",
        "sub": "No /tpa. No /spawn. No /back.",
        "bullets": ["Beds are your only anchor", "Bases stay hidden because getting there is hard", "Your legs are the map"],
        "accent": (79, 169, 201),
    },
    {
        "file": "short_bounty.mp4",
        "label": "BOUNTY",
        "hook": "EVERYONE HAS A TARGET",
        "sub": "A contract with someone's name on it.",
        "bullets": ["Kill them, take a heart", "The contract rerolls", "Three nights a week it pays double"],
        "accent": (230, 120, 60),
    },
    {
        "file": "short_pass.mp4",
        "label": "SEASON PASS",
        "hook": "COSMETICS ONLY",
        "sub": "No kits. No hearts. No advantage.",
        "bullets": ["Earn shards by playing", "Titles and particle trails", "Levels reset, rewards stay"],
        "accent": (150, 100, 200),
    },
]

BULLET_START = 2.8
BULLET_STEP = 0.6
FOOTER_AT = 5.6


def render(short, logo):
    path = os.path.join(HERE, short["file"])
    frame_base = base.backdrop(SIZE)
    writer = base.open_writer(path)
    accent = short["accent"]

    for index in range(int(FPS * DURATION)):
        t = index / FPS
        frame = frame_base.copy()
        base.rain(frame, index * 10, hash(short["file"]) % 1000 + index // 5)
        draw = ImageDraw.Draw(frame)

        # top label bar
        if t > 0.2:
            bar = int(min(1.0, (t - 0.2) / 0.4) * 76)
            draw.rectangle([0, 250, SIZE[0], 250 + bar], fill=accent)
            if t > 0.4:
                draw.text((SIZE[0] // 2, 288), short["label"], font=base.font(26),
                          fill=(255, 245, 245), anchor="mm")

        if t > 1.2:
            alpha = min(1.0, (t - 1.2) / 0.5)
            shade = int(255 * alpha)
            size = 64 if len(short["hook"]) < 14 else 50
            draw.text((SIZE[0] // 2, 400), short["hook"], font=base.font(size),
                      fill=(shade, shade, shade), anchor="mm")

        if t > 2.0:
            draw.text((SIZE[0] // 2, 480), short["sub"], font=base.font(24, bold=False),
                      fill=(220, 220, 228), anchor="mm")

        for position, bullet in enumerate(short["bullets"]):
            start = BULLET_START + position * BULLET_STEP
            if t > start:
                alpha = min(1.0, (t - start) / 0.3)
                tone = int(150 + 60 * alpha)
                draw.text((SIZE[0] // 2, 620 + position * 62), bullet, font=base.font(23, bold=False),
                          fill=(tone, tone + 15, tone + 30), anchor="mm")

        if t > FOOTER_AT:
            alpha = min(1.0, (t - FOOTER_AT) / 0.7)
            pulse = 1.0 + 0.02 * math.sin(t * 3.0)
            layer = logo.resize((int(430 * pulse), int(logo.height * (430 / logo.width) * pulse)),
                                Image.LANCZOS)
            frame.alpha_composite(base.fade_in(layer, alpha), ((SIZE[0] - layer.width) // 2, 900))
            shade = int(255 * alpha)
            draw.text((SIZE[0] // 2, 1120), "DISCORD IN BIO", font=base.font(30),
                      fill=(shade, shade, shade), anchor="mm")
            draw.text((SIZE[0] // 2, 1170), "JAVA 1.21.11  ·  EU", font=base.font(16, bold=False),
                      fill=(120, 140, 160), anchor="mm")

        base.emit(writer, frame)

    writer.close()
    events = [(0.05, "whoosh", 0.6), (1.2, "impact", 0.7)]
    for position in range(len(short["bullets"])):
        events.append((BULLET_START + position * BULLET_STEP, "tick", 0.7))
    events.append((FOOTER_AT, "chime", 0.5))
    print("written", path)
    return events


def main():
    logo = base.logo_image(430)
    timings = {}
    for short in SHORTS:
        timings[short["file"]] = render(short, logo)
    with open(os.path.join(HERE, "shorts_timing.json"), "w", encoding="utf-8") as handle:
        json.dump(timings, handle, indent=1)
    print("timings written")


if __name__ == "__main__":
    main()
