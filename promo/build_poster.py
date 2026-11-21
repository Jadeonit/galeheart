import os
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
LOGO = os.path.join(ROOT, "logo", "galeheart-wordmark.png")
OUT = os.path.join(HERE, "poster.png")

W, H = 1920, 1080


def font(size, bold=True):
    name = "arialbd.ttf" if bold else "arial.ttf"
    try:
        return ImageFont.truetype(name, size)
    except Exception:
        return ImageFont.load_default(size=size)


def build():
    img = Image.new("RGB", (W, H), (5, 8, 13))
    draw = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        draw.line([(0, y), (W, y)], fill=(int(5 + 9 * t), int(8 + 12 * t), int(13 + 20 * t)))

    glow = Image.new("RGB", (W, H), (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([-W * 0.25, -H * 0.4, W * 0.55, H * 1.2], fill=(24, 70, 100))
    gd.ellipse([W * 0.45, -H * 0.3, W * 1.25, H * 1.3], fill=(92, 18, 34))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=280))
    img = Image.blend(img, glow, 0.35).convert("RGBA")

    rain = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    rd = ImageDraw.Draw(rain)
    rng = random.Random(11)
    for _ in range(220):
        x = rng.randint(-200, W)
        y = rng.randint(-100, H)
        length = rng.randint(20, 60)
        rd.line([(x, y), (x - length // 3, y + length)], fill=(150, 190, 230, 26), width=1)
    img.alpha_composite(rain)

    logo = Image.open(LOGO).convert("RGBA")
    ratio = 1150 / logo.width
    logo = logo.resize((1150, int(logo.height * ratio)), Image.LANCZOS)
    img.alpha_composite(logo, ((W - logo.width) // 2, 300))

    draw = ImageDraw.Draw(img)
    draw.text((W // 2, 150), "SEASON 1  ·  ACID SKY", font=font(34), fill=(150, 200, 225), anchor="mm")
    draw.text((W // 2, 690), "The weather wants you dead.", font=font(56), fill=(240, 246, 252), anchor="mm")

    features = "Lifesteal hearts  ·  Anomalous weather  ·  No teleports  ·  Weekly bounty"
    draw.text((W // 2, 790), features, font=font(32, bold=False), fill=(160, 180, 200), anchor="mm")
    draw.text((W // 2, 845), "Sealed Nether and End  ·  Cosmetic season pass", font=font(32, bold=False),
              fill=(160, 180, 200), anchor="mm")

    draw.text((W // 2, 935), "d3.rionix.cloud:25039", font=font(52), fill=(255, 255, 255), anchor="mm")
    draw.text((W // 2, 995), "discord.gg/g9XANw8Wa", font=font(34), fill=(111, 176, 214), anchor="mm")
    draw.text((W // 2, 1045), "Java 1.21.11  ·  EU", font=font(24, bold=False),
              fill=(120, 140, 160), anchor="mm")

    img.convert("RGB").save(OUT, quality=95)
    print("poster written", OUT)


if __name__ == "__main__":
    build()
