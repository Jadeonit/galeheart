"""Tracker banners: 468x60 and 728x90, from the wordmark."""

from __future__ import annotations

import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
LOGO = os.path.join(ROOT, "logo", "galeheart-wordmark.png")


def font(size, bold=True):
    try:
        return ImageFont.truetype("arialbd.ttf" if bold else "arial.ttf", size)
    except Exception:
        return ImageFont.load_default(size=size)


def build(width, height, path):
    image = Image.new("RGB", (width, height), (7, 10, 15))
    draw = ImageDraw.Draw(image)
    for y in range(height):
        t = y / max(1, height - 1)
        draw.line([(0, y), (width, y)], fill=(int(6 + 10 * t), int(10 + 14 * t), int(16 + 24 * t)))

    glow = Image.new("RGB", (width, height), (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([-width * 0.2, -height * 0.6, width * 0.5, height * 1.6], fill=(20, 62, 88))
    gd.ellipse([width * 0.6, -height * 0.4, width * 1.3, height * 1.4], fill=(84, 16, 30))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=height // 3))
    image = Image.blend(image, glow, 0.4).convert("RGBA")

    logo = Image.open(LOGO).convert("RGBA")
    target_height = int(height * 0.52)
    ratio = target_height / logo.height
    logo = logo.resize((int(logo.width * ratio), target_height), Image.LANCZOS)

    pad = max(10, width // 40)
    image.alpha_composite(logo, (pad, (height - logo.height) // 2))

    draw = ImageDraw.Draw(image)
    text_x = pad * 2 + logo.width
    title_size = max(14, int(height * 0.34))
    draw.text((text_x, height * 0.20), "SEASON 1: ACID SKY", font=font(max(9, int(height * 0.16))),
              fill=(150, 200, 225))
    draw.text((text_x, height * 0.38), "d3.rionix.cloud:25039", font=font(title_size),
              fill=(240, 246, 252))
    draw.text((text_x, height * 0.72), "lifesteal · hostile weather · no teleports",
              font=font(max(8, int(height * 0.15)), bold=False), fill=(160, 180, 200))

    image.convert("RGB").save(path, quality=95)
    print("written", path)


if __name__ == "__main__":
    build(468, 60, os.path.join(HERE, "banner_468x60.png"))
    build(728, 90, os.path.join(HERE, "banner_728x90.png"))
