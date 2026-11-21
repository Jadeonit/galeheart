"""Builds promo clips from the wordmark. No game footage needed."""

from __future__ import annotations

import math
import os
import random

import imageio.v2 as imageio
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
LOGO = os.path.join(ROOT, "logo", "galeheart-wordmark.png")
POSTER = os.path.join(HERE, "poster.png")

FPS = 24
NEAR_BLACK = (5, 8, 13)


def font(size, bold=True):
    try:
        return ImageFont.truetype("arialbd.ttf" if bold else "arial.ttf", size)
    except Exception:
        return ImageFont.load_default(size=size)


def backdrop(size):
    width, height = size
    image = Image.new("RGB", size, NEAR_BLACK)
    draw = ImageDraw.Draw(image)
    for y in range(height):
        t = y / (height - 1)
        draw.line([(0, y), (width, y)], fill=(int(5 + 9 * t), int(8 + 12 * t), int(13 + 20 * t)))
    glow = Image.new("RGB", size, (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([-width * 0.3, -height * 0.2, width * 0.7, height * 0.9], fill=(26, 70, 100))
    gd.ellipse([width * 0.4, -height * 0.1, width * 1.3, height * 1.1], fill=(96, 20, 36))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=max(width, height) // 5))
    return Image.blend(image, glow, 0.33).convert("RGBA")


def rain(frame, offset, seed, density=90):
    rng = random.Random(seed)
    layer = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    width, height = frame.size
    for _ in range(density):
        x = rng.randint(-60, width)
        y = (rng.randint(0, height) + offset) % (height + 100) - 30
        length = rng.randint(12, 34)
        draw.line([(x, y), (x - length // 3, y + length)], fill=(150, 190, 230, 30), width=1)
    frame.alpha_composite(layer)


def logo_image(width):
    image = Image.open(LOGO).convert("RGBA")
    ratio = width / image.width
    return image.resize((width, max(1, int(image.height * ratio))), Image.LANCZOS)


def fade_in(layer, alpha):
    alpha = max(0.0, min(1.0, alpha))
    copy = layer.copy()
    copy.putalpha(copy.split()[3].point(lambda a, a0=alpha: int(a * a0)))
    return copy


def emit(writer, image):
    writer.append_data(np.asarray(image.convert("RGB")))


def open_writer(path):
    return imageio.get_writer(path, fps=FPS, codec="libx264", quality=8, macro_block_size=8)


def vertical_video(path):
    """9:16 opener, 12 seconds. Hook, features, address."""
    size = (720, 1280)
    base = backdrop(size)
    logo = logo_image(640)
    writer = open_writer(path)

    total = 12.0
    for index in range(int(FPS * total)):
        t = index / FPS
        frame = base.copy()
        rain(frame, index * 9, 3 + index // 5)
        draw = ImageDraw.Draw(frame)

        if t > 0.4:
            draw.text((size[0] // 2, 190), "SEASON 1  ·  ACID SKY", font=font(26),
                      fill=(150, 200, 225), anchor="mm")

        if t > 0.6:
            pulse = 1.0 + 0.02 * math.sin(t * 2.2)
            layer = logo.resize((int(logo.width * pulse), int(logo.height * pulse)), Image.LANCZOS)
            frame.alpha_composite(fade_in(layer, (t - 0.6) / 1.2),
                                  ((size[0] - layer.width) // 2, 300))

        if t > 2.0:
            draw.text((size[0] // 2, 700), "The weather wants you dead.", font=font(36),
                      fill=(240, 246, 252), anchor="mm")

        lines = [
            (3.2, "Lifesteal hearts"),
            (3.9, "Anomalous weather, one hour warning"),
            (4.6, "No teleports, beds only"),
            (5.3, "Personal bounty contracts"),
            (6.0, "Sealed Nether and End"),
            (6.7, "Cosmetic season pass"),
        ]
        for start, text in lines:
            if t > start:
                alpha = min(1.0, (t - start) / 0.4)
                shade = int(150 + 30 * alpha)
                draw.text((size[0] // 2, 780 + int((start - 3.2) * 58)), text,
                          font=font(23, bold=False), fill=(shade, shade + 20, shade + 40), anchor="mm")

        if t > 8.0:
            alpha = min(1.0, (t - 8.0) / 0.8)
            draw.text((size[0] // 2, 1140), "d3.rionix.cloud:25039", font=font(30),
                      fill=(int(255 * alpha), int(255 * alpha), int(255 * alpha)), anchor="mm")
            draw.text((size[0] // 2, 1190), "JAVA 1.21.11  ·  EU  ·  60 SLOTS",
                      font=font(16, bold=False), fill=(120, 140, 160), anchor="mm")

        emit(writer, frame)
    writer.close()
    print("written", path)


def square_video(path):
    """1:1 poster push, 6 seconds."""
    size = (800, 800)
    poster = Image.open(POSTER).convert("RGB")
    writer = open_writer(path)

    for index in range(FPS * 6):
        t = index / FPS
        zoom = 1.0 + 0.06 * (t / 6.0)
        width = int(poster.width * zoom)
        height = int(poster.height * zoom)
        frame = poster.resize((width, height), Image.LANCZOS)
        left = (width - size[0]) // 2
        top = (height - size[1]) // 2
        emit(writer, frame.crop((left, top, left + size[0], top + size[1])))
    writer.close()
    print("written", path)


def alert_video(path):
    """9:16 weather alert card, 8 seconds."""
    size = (720, 1280)
    base = backdrop(size)
    logo = logo_image(520)
    writer = open_writer(path)

    for index in range(FPS * 8):
        t = index / FPS
        frame = base.copy()
        rain(frame, index * 11, 9 + index // 4, density=120)
        draw = ImageDraw.Draw(frame)

        bar_height = int(min(1.0, t / 0.6) * 90)
        draw.rectangle([0, 240, size[0], 240 + bar_height], fill=(150, 30, 40))
        if t > 0.5:
            draw.text((size[0] // 2, 285), "WEATHER ALERT", font=font(30), fill=(255, 240, 240), anchor="mm")

        if t > 1.0:
            draw.text((size[0] // 2, 430), "ACID RAIN", font=font(66), fill=(255, 255, 255), anchor="mm")
            draw.text((size[0] // 2, 515), "IN 60 MINUTES", font=font(30), fill=(230, 180, 180), anchor="mm")
            draw.text((size[0] // 2, 610), "Find shelter or put on the helmet.", font=font(22, bold=False),
                      fill=(190, 170, 170), anchor="mm")

        if t > 3.6:
            alpha = (t - 3.6) / 1.0
            layer = fade_in(logo, alpha)
            frame.alpha_composite(layer, ((size[0] - layer.width) // 2, 780))
            a = int(255 * min(1.0, alpha))
            draw.text((size[0] // 2, 1100), "d3.rionix.cloud:25039", font=font(28),
                      fill=(a, a, a), anchor="mm")

        emit(writer, frame)
    writer.close()
    print("written", path)


def bounty_video(path):
    """9:16 bounty explainer, 6 seconds."""
    size = (720, 1280)
    base = backdrop(size)
    writer = open_writer(path)

    for index in range(FPS * 6):
        t = index / FPS
        frame = base.copy()
        rain(frame, index * 7, 17 + index // 6, density=60)
        draw = ImageDraw.Draw(frame)

        if t > 0.2:
            draw.text((size[0] // 2, 330), "BOUNTY", font=font(84), fill=(236, 120, 96), anchor="mm")
        if t > 0.9:
            draw.text((size[0] // 2, 450), "Every player gets a contract.", font=font(26, bold=False),
                      fill=(235, 235, 240), anchor="mm")
        if t > 1.6:
            draw.text((size[0] // 2, 540), "Kill your target.", font=font(30), fill=(255, 255, 255), anchor="mm")
        if t > 2.3:
            draw.text((size[0] // 2, 620), "Take a heart. Get a new name.", font=font(26, bold=False),
                      fill=(225, 200, 200), anchor="mm")
        if t > 3.2:
            draw.text((size[0] // 2, 760), "/bounty", font=font(40), fill=(255, 255, 255), anchor="mm")
        if t > 4.0:
            draw.text((size[0] // 2, 900), "Three nights a week the payout doubles.", font=font(22, bold=False),
                      fill=(170, 190, 210), anchor="mm")
        if t > 4.8:
            draw.text((size[0] // 2, 1080), "d3.rionix.cloud:25039", font=font(28),
                      fill=(255, 255, 255), anchor="mm")

        emit(writer, frame)
    writer.close()
    print("written", path)


def square_photo():
    image = Image.open(POSTER).convert("RGB")
    side = image.height
    square = image.crop(((image.width - side) // 2, 0, (image.width + side) // 2, side))
    square = square.resize((1080, 1080), Image.LANCZOS)
    out = os.path.join(HERE, "square.png")
    square.save(out)
    print("written", out)


def story_photo():
    """1080x1920 story frame."""
    poster = Image.open(POSTER).convert("RGB")
    canvas = backdrop((1080, 1920))
    ratio = 1080 / poster.width
    scaled = poster.resize((1080, int(poster.height * ratio)), Image.LANCZOS)
    top = (1920 - scaled.height) // 2
    canvas.alpha_composite(scaled.convert("RGBA"), (0, top))
    out = os.path.join(HERE, "story.png")
    canvas.convert("RGB").save(out)
    print("written", out)


if __name__ == "__main__":
    vertical_video(os.path.join(HERE, "vertical.mp4"))
    alert_video(os.path.join(HERE, "alert.mp4"))
    bounty_video(os.path.join(HERE, "bounty.mp4"))
    square_video(os.path.join(HERE, "square.mp4"))
    square_photo()
    story_photo()
