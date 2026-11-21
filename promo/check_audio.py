import re
import subprocess

import imageio_ffmpeg

FF = imageio_ffmpeg.get_ffmpeg_exe()
for name in ("vertical.mp4", "alert.mp4", "bounty.mp4", "square.mp4"):
    result = subprocess.run(
        [FF, "-hide_banner", "-i", name, "-af", "volumedetect", "-f", "null", "-"],
        capture_output=True, text=True, encoding="utf-8", errors="replace")
    text = result.stderr
    mean = re.search(r"mean_volume: ([-\d.]+) dB", text)
    peak = re.search(r"max_volume: ([-\d.]+) dB", text)
    rate = re.search(r"Audio: aac.*?, (\d+) Hz", text)
    duration = re.search(r"Duration: (\d\d:\d\d:\d\d)", text)
    print(f"{name:<14} {rate.group(1) if rate else '?':>6} Hz  "
          f"mean {mean.group(1) if mean else '?':>6} dB  "
          f"peak {peak.group(1) if peak else '?':>6} dB  "
          f"{duration.group(1) if duration else '?'}")
