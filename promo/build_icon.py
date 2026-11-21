"""Renders a 64x64 server icon: pixel heart, gradient, dark plate."""

import os

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.join(r"C:\Users\user\Documents\Проект по умолчанию", "galeheart", "promo")
OUT = os.path.join(HERE, "icon.png")

PATTERN = [
    "..###...###..",
    ".#####.#####.",
    "#############",
    "#############",
    "#############",
    ".###########.",
    "..#########..",
    "...#######...",
    "....#####....",
    ".....###.....",
    "......#......",
]

SCALE = 4
SIZE = 64


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def build():
    image = Image.new("RGB", (SIZE, SIZE), (7, 10, 15))
    glow = Image.new("RGB", (SIZE, SIZE), (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([-10, -6, 40, 50], fill=(24, 60, 86))
    gd.ellipse([22, 6, 78, 62], fill=(70, 16, 28))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=9))
    image = Image.blend(image, glow, 0.45)

    draw = ImageDraw.Draw(image)
    width = len(PATTERN[0]) * SCALE
    height = len(PATTERN) * SCALE
    left = (SIZE - width) // 2
    top = (SIZE - height) // 2 + 1

    for row, line in enumerate(PATTERN):
        for col, cell in enumerate(line):
            if cell != "#":
                continue
            t = row / (len(PATTERN) - 1)
            colour = lerp((79, 169, 201), (200, 50, 75), t)
            x = left + col * SCALE
            y = top + row * SCALE
            draw.rectangle([x, y, x + SCALE - 1, y + SCALE - 1], fill=colour)
            # top-left highlight keeps the pixel look
            if row < 2:
                draw.line([(x, y), (x + SCALE - 1, y)], fill=lerp(colour, (255, 255, 255), 0.25))

    image.save(OUT)
    print("written", OUT)


if __name__ == "__main__":
    build()
