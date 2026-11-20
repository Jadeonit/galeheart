"""Builds the store page and an animated teaser from the wordmark.

Run:  python build_promo.py
Out:  ../store/index.html, teaser.gif
"""

from __future__ import annotations

import base64
import os
import random

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
LOGO = os.path.join(ROOT, "logo", "galeheart-wordmark.png")
STORE = os.path.join(ROOT, "store", "index.html")

W, H = 800, 450
FRAMES = 36
DELAY = 90


def load_logo(width: int) -> Image.Image:
    image = Image.open(LOGO).convert("RGBA")
    ratio = width / image.width
    return image.resize((width, max(1, int(image.height * ratio))), Image.LANCZOS)


def font(size: int) -> ImageFont.ImageFont:
    try:
        return ImageFont.truetype("arialbd.ttf", size)
    except Exception:
        return ImageFont.load_default(size=size)


def background() -> Image.Image:
    base = Image.new("RGB", (W, H), (6, 9, 14))
    draw = ImageDraw.Draw(base)
    for y in range(H):
        t = y / (H - 1)
        draw.line([(0, y), (W, y)], fill=(int(6 + 8 * t), int(9 + 10 * t), int(14 + 16 * t)))
    return base


def draw_rain(frame: Image.Image, offset: int, seed: int) -> None:
    rng = random.Random(seed)
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for _ in range(70):
        x = rng.randint(-100, W)
        y = (rng.randint(0, H) + offset) % (H + 120) - 40
        length = rng.randint(14, 30)
        draw.line([(x, y), (x - length // 3, y + length)], fill=(150, 190, 230, 28), width=1)
    frame.alpha_composite(layer)


def build_gif() -> None:
    logo = load_logo(620)
    title_font = font(34)
    small_font = font(18)

    frames = []
    for index in range(FRAMES):
        frame = background().convert("RGBA")
        draw_rain(frame, index * 14, seed=7 + index // 4)

        progress = min(1.0, (index + 1) / 10)
        alpha = int(255 * progress)
        pulse = 1.0 + 0.015 * (1 if (index // 4) % 2 == 0 else -1)

        scaled = logo.resize((int(logo.width * pulse), int(logo.height * pulse)), Image.LANCZOS)
        if alpha < 255:
            layer = Image.new("RGBA", scaled.size, (0, 0, 0, 0))
            layer.paste(scaled, (0, 0))
            mask = layer.split()[3].point(lambda a, a0=alpha: int(a * a0 / 255))
            layer.putalpha(mask)
            scaled = layer

        x = (W - scaled.width) // 2
        y = 70 + (H - 120 - scaled.height) // 2
        frame.alpha_composite(scaled, (x, y))

        if index >= 8:
            draw = ImageDraw.Draw(frame)
            draw.text((W // 2, 40), "SEASON 1  ·  ACID SKY", font=small_font,
                      fill=(200, 220, 235, 230), anchor="mm")
            draw.text((W // 2, H - 60), "d3.rionix.cloud:25039", font=title_font,
                      fill=(255, 255, 255, 240), anchor="mm")

        frames.append(frame.convert("P", palette=Image.ADAPTIVE, colors=128))

    frames[0].save(os.path.join(HERE, "teaser.gif"), save_all=True, append_images=frames[1:],
                   duration=DELAY, loop=0, optimize=True)
    print("teaser.gif written")


def build_store() -> None:
    with open(LOGO, "rb") as fh:
        logo_b64 = base64.b64encode(fh.read()).decode("ascii")

    html = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>GaleHeart — Support</title>
<style>
  :root {{ --bg:#070a0f; --panel:#0e1520; --line:#1b2634; --text:#e6edf5; --muted:#8fa0b5; --accent:#4fa9c9; }}
  * {{ box-sizing:border-box; }}
  body {{ margin:0; background:radial-gradient(1200px 600px at 50% -10%, #14283a 0%, var(--bg) 60%);
         color:var(--text); font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif; }}
  .wrap {{ max-width:980px; margin:0 auto; padding:48px 20px 80px; }}
  img.logo {{ width:min(560px,100%); display:block; margin:0 auto 18px; }}
  h1 {{ text-align:center; font-size:20px; font-weight:600; color:var(--muted); margin:0 0 8px; }}
  p.lead {{ text-align:center; color:var(--muted); margin:0 0 40px; }}
  .grid {{ display:grid; grid-template-columns:repeat(auto-fit,minmax(260px,1fr)); gap:18px; }}
  .card {{ background:var(--panel); border:1px solid var(--line); border-radius:16px; padding:24px; }}
  .card h2 {{ margin:0 0 6px; font-size:20px; }}
  .price {{ font-size:28px; font-weight:700; color:var(--accent); margin:8px 0 16px; }}
  ul {{ margin:0 0 20px; padding-left:18px; color:var(--muted); line-height:1.7; }}
  .btn {{ display:inline-block; padding:11px 18px; border-radius:10px; background:var(--accent);
          color:#04121a; font-weight:700; text-decoration:none; }}
  .btn.muted {{ background:#1a2432; color:var(--text); }}
  .note {{ margin-top:40px; padding:18px 20px; border:1px solid var(--line); border-radius:14px;
           color:var(--muted); font-size:14px; line-height:1.7; }}
  code {{ color:#cfe8f5; }}
</style>
</head>
<body>
<div class="wrap">
  <img class="logo" alt="GaleHeart" src="data:image/png;base64,{logo_b64}">
  <h1>SUPPORT THE SERVER</h1>
  <p class="lead">Cosmetics only. No power, no kits, no hearts — the server stays fair.</p>

  <div class="grid">
    <div class="card">
      <h2>Supporter</h2>
      <div class="price">$5</div>
      <ul>
        <li>Coloured name in chat</li>
        <li>Supporter role in Discord</li>
        <li>Your name on the wall of thanks</li>
      </ul>
      <a class="btn" href="https://discord.gg/your-invite">Open a ticket</a>
    </div>

    <div class="card">
      <h2>Season Pass</h2>
      <div class="price">$7 <span style="font-size:14px;color:#8fa0b5">· season 2</span></div>
      <ul>
        <li>30-tier cosmetic track</li>
        <li>Particles, titles, cape</li>
        <li>Progress resets each season, rewards stay</li>
      </ul>
      <a class="btn muted" href="#">Coming soon</a>
    </div>

    <div class="card">
      <h2>Custom</h2>
      <div class="price">Any</div>
      <ul>
        <li>One-off support</li>
        <li>Your idea for a cosmetic</li>
        <li>We will build it if it fits the season</li>
      </ul>
      <a class="btn" href="https://discord.gg/your-invite">Message us</a>
    </div>
  </div>

  <div class="note">
    <b>How it works.</b> Open a ticket in Discord, tell us your Minecraft nickname, pay,
    and a staff member runs <code>/grant &lt;nick&gt; supporter</code>. The rank appears
    in game within a few minutes and stays for the whole season.<br><br>
    We do not sell hearts, gear, kits, extra homes or any advantage. Ranks are cosmetic and
    social only, in line with the Minecraft EULA.
  </div>
</div>
</body>
</html>
"""
    os.makedirs(os.path.dirname(STORE), exist_ok=True)
    with open(STORE, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(html)
    print("store page written")


if __name__ == "__main__":
    build_gif()
    build_store()
