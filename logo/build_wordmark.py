"""GaleHeart — вордмарк в стиле Minecraft-изданий.

Наклонные блочные буквы, пиксельная текстура, светлая верхняя грань,
тёмная глубина вниз, звёздный SMP. Без Blockbench, чистый Pillow.

Запуск:  python build_wordmark.py
"""

from __future__ import annotations

import os
import random

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))

# ---------------------------------------------------------------- шрифт 9x11

FONT = {
    "G": ["011111110", "011111110", "011111110", "111000000", "111000111",
          "111000111", "111000111", "111000111", "011111111", "011111111", "001111110"],
    "A": ["000111000", "000111000", "001111100", "001111100", "011000110",
          "011000110", "111111111", "111111111", "110000011", "110000011", "110000011"],
    "L": ["111000000", "111000000", "111000000", "111000000", "111000000",
          "111000000", "111000000", "111000000", "111111111", "111111111", "111111111"],
    "E": ["111111111", "111111111", "111000000", "111000000", "111111100",
          "111111100", "111000000", "111000000", "111111111", "111111111", "111111111"],
    "H": ["111000111", "111000111", "111000111", "111000111", "111111111",
          "111111111", "111111111", "111000111", "111000111", "111000111", "111000111"],
    "R": ["111111100", "111111110", "111000111", "111000111", "111111110",
          "111111100", "111000000", "111011000", "111001100", "111000110", "111000011"],
    "T": ["111111111", "111111111", "111111111", "000111000", "000111000",
          "000111000", "000111000", "000111000", "000111000", "000111000", "000111000"],
    "S": ["011111111", "011111111", "111000000", "111000000", "011111100",
          "001111110", "000000111", "000000111", "111111110", "111111100", "011111000"],
    "M": ["110000011", "111000111", "111101111", "110111011", "110111011",
          "110010011", "110000011", "110000011", "110000011", "110000011", "110000011"],
    "P": ["111111100", "111111110", "111000111", "111000111", "111111110",
          "111111100", "111000000", "111000000", "111000000", "111000000", "111000000"],
}
for _ch, _rows in FONT.items():
    assert len(_rows) == 11 and all(len(r) == 9 for r in _rows), _ch

# ---------------------------------------------------------------- палитры

STORM = ((66, 158, 190), (190, 246, 255), (16, 48, 62))
CRIMSON = ((200, 44, 70), (255, 150, 162), (66, 8, 18))
NIGHT = ((46, 60, 190), (235, 242, 255), (9, 13, 70))

GALE = [("G", STORM), ("A", STORM), ("L", STORM), ("E", STORM)]
HEART = [("H", CRIMSON), ("E", CRIMSON), ("A", CRIMSON), ("R", CRIMSON), ("T", CRIMSON)]
MAIN = GALE + HEART
SUB = [("S", NIGHT), ("M", NIGHT), ("P", NIGHT)]

GAP = 4
SHEAR = 0.22  # наклон всего слова, в долях высоты
DEPTH_DX = -0.18


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def paint(letters, star: bool, seed: int):
    rng = random.Random(seed)
    width = len(letters) * 9 + (len(letters) - 1) * GAP
    grid = [[None] * width for _ in range(11)]
    for index, (ch, _palette) in enumerate(letters):
        ox = index * (9 + GAP)
        for y, row in enumerate(FONT[ch]):
            for x, cell in enumerate(row):
                if cell == "1":
                    grid[y][ox + x] = index

    colors = [[None] * width for _ in range(11)]
    for y in range(11):
        for x in range(width):
            index = grid[y][x]
            if index is None:
                continue
            base, hi, lo = letters[index][1]
            above = grid[y - 1][x] if y > 0 else None
            below = grid[y + 1][x] if y < 10 else None

            color = mix(base, hi, rng.choice((0.0, 0.03, 0.06, 0.10)))
            color = mix(color, lo, 0.16 * y / 10)

            if star and rng.random() < 0.08:
                color = mix(hi, (255, 255, 255), 0.9)
            elif rng.random() < 0.04:
                color = mix(color, hi, 0.35)

            if above is None:
                color = mix(color, hi, 0.62)
            elif below is None:
                color = mix(color, lo, 0.50)

            colors[y][x] = color
    return grid, colors, width


def dilate_outline(grid, width: int):
    outline = set()
    for y in range(11):
        for x in range(width):
            if grid[y][x] is None:
                continue
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < 11 and 0 <= nx < width and grid[ny][nx] is None:
                        outline.add((ny, nx))
    return outline


def shear(img: Image.Image, k: float) -> Image.Image:
    w, h = img.size
    new_w = int(w + k * h) + 2
    coeffs = (1, k, -k * h, 0, 1, 0)
    return img.transform((new_w, h), Image.AFFINE, coeffs, resample=Image.BICUBIC)


def render_word(letters, block: int, depth: int, seed: int, star: bool = False) -> Image.Image:
    grid, colors, width = paint(letters, star, seed)
    top = 2
    left = max(0, round(-DEPTH_DX * depth)) + 1
    w = width + left + 2
    h = top + 11 + depth + 1
    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)

    # глубина: слои вниз, дальние темнее
    for layer in range(depth, 0, -1):
        shade = 0.42 + 0.58 * (1.0 - layer / depth)
        ox = left + round(DEPTH_DX * layer)
        oy = top + layer
        for y in range(11):
            for x in range(width):
                index = grid[y][x]
                if index is None:
                    continue
                base = letters[index][1][2]
                col = tuple(round(c * shade) for c in base)
                draw.point((ox + x, oy + y), fill=col + (255,))

    # тёмный контур по краю буквы
    for y in range(11):
        for x in range(width):
            index = grid[y][x]
            if index is None:
                continue
            base = letters[index][1][2]
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < 11 and 0 <= nx < width and grid[ny][nx] is None:
                        draw.point((left + nx, top + ny), fill=tuple(round(c * 0.35) for c in base) + (255,))

    # светлая верхняя полка
    for y in range(11):
        for x in range(width):
            index = grid[y][x]
            if index is None:
                continue
            if y == 0 or grid[y - 1][x] is None:
                base, hi, lo = letters[index][1]
                draw.point((left + x, top + y - 1), fill=mix(hi, (255, 255, 255), 0.05) + (255,))

    # лицевая грань
    for y in range(11):
        for x in range(width):
            col = colors[y][x]
            if col is None:
                continue
            draw.point((left + x, top + y), fill=col + (255,))

    big = canvas.resize((w * block, h * block), Image.NEAREST)
    big = shear(big, SHEAR)

    alpha = big.split()[3].point(lambda a: int(a * 0.65))
    shadow = Image.new("RGBA", big.size, (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 255), (0, 0), alpha)
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=block * 1.1))
    out = Image.new("RGBA", big.size, (0, 0, 0, 0))
    out.alpha_composite(shadow, (int(block * 0.5), int(block * 1.4)))
    out.alpha_composite(big)
    return out


def trim(img: Image.Image) -> Image.Image:
    box = img.split()[3].getbbox()
    return img.crop(box) if box else img


def compose_wide(block: int = 14) -> Image.Image:
    main = trim(render_word(MAIN, block, depth=4, seed=7))
    sub = trim(render_word(SUB, max(7, block // 2), depth=3, seed=11, star=True))

    overlap = int(main.height * 0.16)
    width = max(main.width, sub.width) + block * 2
    height = main.height + sub.height - overlap + block * 2
    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    canvas.alpha_composite(main, ((width - main.width) // 2, block))
    canvas.alpha_composite(sub, ((width - sub.width) // 2, block + main.height - overlap))
    return canvas


def compose_stacked(block: int = 12) -> Image.Image:
    gale = trim(render_word(GALE, block, depth=4, seed=3))
    heart = trim(render_word(HEART, block, depth=4, seed=5))
    sub = trim(render_word(SUB, max(7, block // 2), depth=3, seed=9, star=True))

    gap = int(block * 0.35)
    width = max(gale.width, heart.width, sub.width) + block * 2
    height = gale.height + heart.height + sub.height + gap * 2 + block * 2
    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))

    y = block
    canvas.alpha_composite(gale, ((width - gale.width) // 2, y))
    y += gale.height + gap
    canvas.alpha_composite(heart, ((width - heart.width) // 2, y))
    y += heart.height + gap
    canvas.alpha_composite(sub, ((width - sub.width) // 2, y - int(sub.height * 0.22)))
    return canvas


def background(width: int, height: int) -> Image.Image:
    img = Image.new("RGB", (width, height), (5, 7, 11))
    glow = Image.new("RGB", (width, height), (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([-width * 0.2, -height * 0.3, width * 0.7, height * 1.3], fill=(24, 52, 70))
    gd.ellipse([width * 0.4, -height * 0.2, width * 1.2, height * 1.2], fill=(62, 14, 24))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=max(width, height) // 5))
    img = Image.blend(img, glow, 0.32)

    vignette = Image.new("L", (width, height), 0)
    vd = ImageDraw.Draw(vignette)
    vd.ellipse([-width * 0.12, -height * 0.2, width * 1.12, height * 1.2], fill=255)
    vignette = vignette.filter(ImageFilter.GaussianBlur(radius=max(width, height) // 10))
    dark = Image.new("RGBA", (width, height), (0, 0, 0, 150))
    base = img.convert("RGBA")
    return Image.composite(base, Image.alpha_composite(base, dark), vignette)


def place(img: Image.Image, size, fill: float) -> Image.Image:
    w, h = size
    bg = background(w, h)
    scale = min(w * fill / img.width, h * fill / img.height)
    img = img.resize((max(1, int(img.width * scale)), max(1, int(img.height * scale))), Image.LANCZOS)
    bg.alpha_composite(img, ((w - img.width) // 2, (h - img.height) // 2))
    return bg.convert("RGB")


def main():
    wide = compose_wide(14)
    stacked = compose_stacked(12)

    wide.save(os.path.join(HERE, "galeheart-wordmark.png"))
    stacked.save(os.path.join(HERE, "galeheart-wordmark-stacked.png"))

    place(wide, (1500, 500), 0.90).save(os.path.join(HERE, "galeheart-banner-1500x500.png"))
    place(stacked, (512, 512), 0.88).save(os.path.join(HERE, "galeheart-avatar-512.png"))
    place(stacked, (1024, 1024), 0.88).save(os.path.join(HERE, "galeheart-avatar-1024.png"))
    place(wide, (1600, 900), 0.86).save(os.path.join(HERE, "galeheart-preview.png"))

    print("wide:", wide.size, "stacked:", stacked.size)


if __name__ == "__main__":
    main()
