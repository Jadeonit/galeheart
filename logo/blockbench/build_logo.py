"""GaleHeart — генератор 3D-логотипа (Blockbench .bbmodel) + превью и аватарки.

Запуск:  python build_logo.py
Выход:   galeheart-logo.bbmodel, galeheart-atlas.png,
         galeheart-preview.png, galeheart-avatar-512.png
"""

from __future__ import annotations

import base64
import io
import json
import os
import uuid

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))

# ---------------------------------------------------------------- шрифт 5x7

FONT = {
    "G": ["0111110", "0111110", "1100011", "1100011", "1100000", "1101111", "1101111", "1100011", "0111110"],
    "A": ["0011100", "0011100", "0110110", "0110110", "1100011", "1100011", "1111111", "1111111", "1100011"],
    "L": ["1100000", "1100000", "1100000", "1100000", "1100000", "1100000", "1100000", "1111111", "1111111"],
    "E": ["1111111", "1111111", "1100000", "1100000", "1111110", "1111110", "1100000", "1111111", "1111111"],
    "H": ["1100011", "1100011", "1100011", "1111111", "1111111", "1111111", "1100011", "1100011", "1100011"],
    "R": ["1111110", "1111110", "1100011", "1100011", "1111110", "1111110", "1101100", "1100110", "1100011"],
    "T": ["1111111", "1111111", "0011000", "0011000", "0011000", "0011000", "0011000", "0011000", "0011000"],
    "S": ["0111111", "0111111", "1100000", "1100000", "0111110", "0111110", "0000011", "0000011", "1111110"],
    "M": ["1100011", "1110111", "1110111", "1101011", "1101011", "1100011", "1100011", "1100011", "1100011"],
    "P": ["1111110", "1111110", "1100011", "1100011", "1111110", "1111110", "1100000", "1100000", "1100000"],
}
for _ch, _rows in FONT.items():
    assert len(_rows) == 9 and all(len(r) == 7 for r in _rows), _ch

# ---------------------------------------------------------------- палитра

MAIN_LETTERS = [
    ("G", "#35557A"),
    ("A", "#3D6289"),
    ("L", "#456F98"),
    ("E", "#4D7CA8"),
    ("H", "#8E1F33"),
    ("E", "#9C2539"),
    ("A", "#AA2B40"),
    ("R", "#B83247"),
    ("T", "#C6394E"),
]
SUB_LETTERS = [("S", "#C7D0DC"), ("M", "#C7D0DC"), ("P", "#C7D0DC")]

MAIN_PX, MAIN_DEPTH, MAIN_GAP = 1.5, 3.0, 3.5
SUB_PX, SUB_DEPTH, SUB_GAP = 1.0, 2.0, 2.0
MAIN_Y = 0.0
SUB_Y = -14.5

ATLAS = 64
SW = 8  # размер плашки в атласе
COLS = ATLAS // SW


def rgb(hex_value: str) -> tuple[int, int, int]:
    h = hex_value.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def shade(color: tuple[int, int, int], factor: float) -> tuple[int, int, int]:
    return tuple(max(0, min(255, round(c * factor))) for c in color)


# ---------------------------------------------------------------- геометрия


def letter_runs(rows: list[str]):
    """Горизонтальные пробеги единиц: (row, col_start, length)."""
    for row, line in enumerate(rows):
        col = 0
        while col < len(line):
            if line[col] == "1":
                start = col
                while col < len(line) and line[col] == "1":
                    col += 1
                yield row, start, col - start
            else:
                col += 1


def letter_rects(rows: list[str]):
    """Жадное разбиение буквы на прямоугольники (row0, col0, row1, col1) включительно."""
    grid = [[ch == "1" for ch in line] for line in rows]
    h, w = len(grid), len(grid[0])
    used = [[False] * w for _ in range(h)]
    result = []
    for y in range(h):
        x = 0
        while x < w:
            if not grid[y][x] or used[y][x]:
                x += 1
                continue
            x2 = x
            while x2 + 1 < w and grid[y][x2 + 1] and not used[y][x2 + 1]:
                x2 += 1
            y2 = y
            while y2 + 1 < h and all(grid[y2 + 1][xx] and not used[y2 + 1][xx] for xx in range(x, x2 + 1)):
                y2 += 1
            for yy in range(y, y2 + 1):
                for xx in range(x, x2 + 1):
                    used[yy][xx] = True
            result.append((y, x, y2, x2))
            x = x2 + 1
    return result


def build_boxes(layout: str = "wide"):
    boxes = []
    colors = []  # уникальные цвета в порядке появления

    def word_width(letters, px, gap):
        return len(letters) * 7 * px + (len(letters) - 1) * gap

    def add_word(letters, px, depth, gap, base_y, group):
        total = word_width(letters, px, gap)
        x0 = -total / 2
        for index, (ch, color) in enumerate(letters):
            advance = 7 * px + gap
            lx = x0 + index * advance
            if color not in colors:
                colors.append(color)
            ci = colors.index(color)
            for row0, col0, row1, col1 in letter_rects(FONT[ch]):
                x1 = lx + col0 * px
                x2 = lx + (col1 + 1) * px
                y_top = base_y + (9 - row0) * px
                y_bot = base_y + (9 - (row1 + 1)) * px
                boxes.append(
                    {
                        "group": group,
                        "letter": f"{ch}{index + 1}",
                        "x1": x1,
                        "x2": x2,
                        "y1": y_bot,
                        "y2": y_top,
                        "z1": 0.0,
                        "z2": depth,
                        "color_index": ci,
                    }
                )

    if layout == "stacked":
        add_word(MAIN_LETTERS[:4], MAIN_PX, MAIN_DEPTH, MAIN_GAP, 16.5, "GALE")
        add_word(MAIN_LETTERS[4:], MAIN_PX, MAIN_DEPTH, MAIN_GAP, 0.0, "HEART")
        add_word(SUB_LETTERS, SUB_PX, SUB_DEPTH, SUB_GAP, -14.5, "SMP")
    else:
        add_word(MAIN_LETTERS, MAIN_PX, MAIN_DEPTH, MAIN_GAP, MAIN_Y, "GALEHEART")
        add_word(SUB_LETTERS, SUB_PX, SUB_DEPTH, SUB_GAP, SUB_Y, "SMP")
    return boxes, colors


# ---------------------------------------------------------------- атлас


def build_atlas(colors: list[str]) -> Image.Image:
    atlas = Image.new("RGBA", (ATLAS, ATLAS), (0, 0, 0, 0))
    for index, hex_value in enumerate(colors):
        base = rgb(hex_value)
        col, row = index % COLS, index // COLS
        x, y = col * SW, row * SW
        for offset, factor in ((0, 1.0), (1, 1.20), (2, 0.70)):
            patch = shade(base, factor)
            for yy in range(y, y + SW):
                for xx in range(x, x + SW):
                    atlas.putpixel((xx, yy), patch + (255,))
    return atlas


def swatch_uv(color_index: int):
    slot = color_index * 3
    col, row = slot % COLS, slot // COLS
    u, v = col * SW, row * SW
    return [u, v, u + SW, v + SW]


def swatch_uv_light(color_index: int):
    slot = color_index * 3 + 1
    col, row = slot % COLS, slot // COLS
    u, v = col * SW, row * SW
    return [u, v, u + SW, v + SW]


def swatch_uv_dark(color_index: int):
    slot = color_index * 3 + 2
    col, row = slot % COLS, slot // COLS
    u, v = col * SW, row * SW
    return [u, v, u + SW, v + SW]


# ---------------------------------------------------------------- bbmodel


def build_bbmodel(boxes, colors, atlas_png: bytes, name: str) -> dict:
    elements = []
    groups: dict[str, dict[str, list[str]]] = {}

    for box in boxes:
        eid = str(uuid.uuid4())
        base = swatch_uv(box["color_index"])
        light = swatch_uv_light(box["color_index"])
        dark = swatch_uv_dark(box["color_index"])
        element = {
            "name": f'{box["letter"]}_{box["x1"]:.1f}_{box["y1"]:.1f}',
            "from": [round(box["x1"], 3), round(box["y1"], 3), round(box["z1"], 3)],
            "to": [round(box["x2"], 3), round(box["y2"], 3), round(box["z2"], 3)],
            "origin": [0, 0, 0],
            "rotation": [0, 0, 0],
            "faces": {
                "north": {"uv": base, "texture": 0},
                "east": {"uv": dark, "texture": 0},
                "south": {"uv": dark, "texture": 0},
                "west": {"uv": dark, "texture": 0},
                "up": {"uv": light, "texture": 0},
                "down": {"uv": dark, "texture": 0},
            },
            "type": "cube",
            "uuid": eid,
        }
        elements.append(element)
        groups.setdefault(box["group"], {}).setdefault(box["letter"], []).append(eid)

    outliner = []
    for group_name, letters in groups.items():
        letter_nodes = [
            {"name": name, "origin": [0, 0, 0], "uuid": str(uuid.uuid4()), "children": children}
            for name, children in letters.items()
        ]
        outliner.append(
            {"name": group_name, "origin": [0, 0, 0], "uuid": str(uuid.uuid4()), "children": letter_nodes}
        )

    texture = {
        "path": "",
        "name": "galeheart-atlas.png",
        "folder": "",
        "namespace": "",
        "id": "0",
        "group": "",
        "width": ATLAS,
        "height": ATLAS,
        "uv_width": ATLAS,
        "uv_height": ATLAS,
        "particle": False,
        "use_as_default": False,
        "layers_enabled": False,
        "sync_to_project": "",
        "render_mode": "default",
        "render_sides": "auto",
        "frame_time": 1,
        "frame_order_type": "loop",
        "frame_order": "",
        "frame_interpolate": False,
        "visible": True,
        "internal": True,
        "saved": False,
        "uuid": str(uuid.uuid4()),
        "relative_path": "",
        "source": "data:image/png;base64," + base64.b64encode(atlas_png).decode("ascii"),
    }

    return {
        "meta": {"format_version": "4.9", "model_format": "free", "box_uv": False},
        "name": name,
        "model_identifier": "",
        "variable_placeholders": "",
        "variable_placeholder_buttons": [],
        "timeline_setups": [],
        "unhandled_root_fields": {},
        "resolution": {"width": ATLAS, "height": ATLAS},
        "elements": elements,
        "outliner": outliner,
        "textures": [texture],
    }


# ---------------------------------------------------------------- рендер

K = 0.45  # глубина уходит вправо-вверх


def render_logo(boxes, colors, scale: float) -> Image.Image:
    ss = 4  # суперсэмплинг
    s = scale * ss

    def proj(x, y, z):
        return (x + z * K) * s, -(y + z * K) * s

    pts_front = []
    for b in boxes:
        pts_front.append((*proj(b["x1"], b["y1"], 0), *proj(b["x2"], b["y2"], 0)))
        pts_front.append((*proj(b["x1"], b["y1"], b["z2"]), *proj(b["x2"], b["y2"], b["z2"])))

    minx = min(min(p[0], p[2]) for p in pts_front)
    maxx = max(max(p[0], p[2]) for p in pts_front)
    miny = min(min(p[1], p[3]) for p in pts_front)
    maxy = max(max(p[1], p[3]) for p in pts_front)

    pad = 8 * ss
    img = Image.new("RGBA", (int(maxx - minx + pad * 2), int(maxy - miny + pad * 2)), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    ox, oy = -minx + pad, -miny + pad

    def P(x, y, z):
        px, py = proj(x, y, z)
        return px + ox, py + oy

    # сначала боковые грани (верх + правый бок), потом фронт — так перекрытия читаются верно
    for b in boxes:
        base = shade(rgb(colors[b["color_index"]]), 1.28)
        x1, x2, y1, y2, z1, z2 = b["x1"], b["x2"], b["y1"], b["y2"], b["z1"], b["z2"]
        draw.polygon([P(x1, y2, z1), P(x2, y2, z1), P(x2, y2, z2), P(x1, y2, z2)], fill=base + (255,))

    for b in boxes:
        side = shade(rgb(colors[b["color_index"]]), 0.55)
        x1, x2, y1, y2, z1, z2 = b["x1"], b["x2"], b["y1"], b["y2"], b["z1"], b["z2"]
        draw.polygon([P(x2, y1, z1), P(x2, y2, z1), P(x2, y2, z2), P(x2, y1, z2)], fill=side + (255,))

    for b in boxes:
        base = rgb(colors[b["color_index"]])
        x1, x2, y1, y2 = b["x1"], b["x2"], b["y1"], b["y2"]
        draw.rectangle([P(x1, y2, 0), P(x2, y1, 0)], fill=base + (255,))

    return img.resize((img.width // ss, img.height // ss), Image.LANCZOS)


def background(size, glow_blue=(58, 110, 170), glow_red=(170, 40, 60)) -> Image.Image:
    w, h = size
    img = Image.new("RGB", (w, h), (10, 16, 26))
    draw = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        r = int(10 + 10 * t)
        g = int(16 + 14 * t)
        b = int(26 + 20 * t)
        draw.line([(0, y), (w, y)], fill=(r, g, b))

    # мягкое свечение: синее слева, красное справа
    glow = Image.new("RGB", (w, h), (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([-w * 0.25, h * 0.05, w * 0.45, h * 0.95], fill=glow_blue)
    gd.ellipse([w * 0.55, h * 0.05, w * 1.25, h * 0.95], fill=glow_red)
    glow = glow.filter(ImageFilter.GaussianBlur(radius=max(w, h) // 6))
    img = Image.blend(img, Image.blend(img, glow, 0.35), 0.55)

    # дождь
    rain = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    rd = ImageDraw.Draw(rain)
    step = max(14, w // 60)
    for i in range(-h, w, step):
        rd.line([(i, 0), (i + h * 0.35, h)], fill=(150, 190, 230, 12), width=1)
    img = Image.alpha_composite(img.convert("RGBA"), rain)

    # виньетка
    vignette = Image.new("L", (w, h), 0)
    vd = ImageDraw.Draw(vignette)
    vd.ellipse([-w * 0.1, -h * 0.15, w * 1.1, h * 1.15], fill=255)
    vignette = vignette.filter(ImageFilter.GaussianBlur(radius=max(w, h) // 8))
    dark = Image.new("RGBA", (w, h), (0, 0, 0, 120))
    img = Image.composite(img, Image.alpha_composite(img, dark), vignette)

    return img.convert("RGBA")


def compose(logo: Image.Image, size, logo_scale: float) -> Image.Image:
    w, h = size
    bg = background(size)

    target_w = int(w * logo_scale)
    ratio = target_w / logo.width
    logo = logo.resize((target_w, int(logo.height * ratio)), Image.LANCZOS)

    # тень
    alpha = logo.split()[3].point(lambda a: int(a * 0.55))
    shadow = Image.new("RGBA", logo.size, (0, 0, 0, 255))
    shadow.putalpha(alpha)
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=max(4, w // 90)))

    x = (w - logo.width) // 2
    y = (h - logo.height) // 2
    bg.alpha_composite(shadow, (x + max(2, w // 200), y + max(4, h // 70)))
    bg.alpha_composite(logo, (x, y))
    return bg


def main():
    wide_boxes, wide_colors = build_boxes("wide")
    stacked_boxes, stacked_colors = build_boxes("stacked")
    if wide_colors != stacked_colors:
        raise SystemExit("палитры разъехались")

    atlas = build_atlas(wide_colors)
    atlas_path = os.path.join(HERE, "galeheart-atlas.png")
    atlas.save(atlas_path)

    buf = io.BytesIO()
    atlas.save(buf, format="PNG")
    atlas_png = buf.getvalue()

    model_wide = build_bbmodel(wide_boxes, wide_colors, atlas_png, "GaleHeart")
    with open(os.path.join(HERE, "galeheart-logo.bbmodel"), "w", encoding="utf-8") as fh:
        json.dump(model_wide, fh, ensure_ascii=False, indent=1)

    model_stacked = build_bbmodel(stacked_boxes, stacked_colors, atlas_png, "GaleHeart Stacked")
    with open(os.path.join(HERE, "galeheart-logo-stacked.bbmodel"), "w", encoding="utf-8") as fh:
        json.dump(model_stacked, fh, ensure_ascii=False, indent=1)

    logo = render_logo(wide_boxes, wide_colors, scale=12)
    logo.save(os.path.join(HERE, "galeheart-logo-transparent.png"))

    stacked = render_logo(stacked_boxes, stacked_colors, scale=12)
    stacked.save(os.path.join(HERE, "galeheart-logo-stacked-transparent.png"))

    compose(logo, (1600, 900), 0.72).convert("RGB").save(os.path.join(HERE, "galeheart-preview.png"))
    compose(logo, (1500, 500), 0.62).convert("RGB").save(os.path.join(HERE, "galeheart-banner-1500x500.png"))
    compose(stacked, (512, 512), 0.84).convert("RGB").save(os.path.join(HERE, "galeheart-avatar-512.png"))

    print("wide boxes:", len(wide_boxes), "stacked boxes:", len(stacked_boxes))
    print("colors:", len(wide_colors))
    print("wide logo:", logo.size, "stacked logo:", stacked.size)


if __name__ == "__main__":
    main()
