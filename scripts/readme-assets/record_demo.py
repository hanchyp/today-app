"""Record the README demo GIF: logging an expense from the Today screen.

Run right after `capture_screens.py --keep` (it needs a fresh install with
sample data, the 'Makan' wallet, and the emulator in demo mode). Records the
screen with `adb screenrecord`, then turns it into docs/images/demo.gif with a
two-pass ffmpeg palette.

Flow: Today -> Tambah -> Transaksi -> type Rp25.000 -> pick the Makan wallet
-> type a note -> Simpan -> new row in Keuangan.

Each step finds its button through uiautomator, which takes 1-2 s while the
screen sits still. ffmpeg's mpdecimate drops those frozen frames, so the GIF
only keeps the moments where something changes, plus a hold at the end.
"""
from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
from pathlib import Path

import sys

import adb_driver as d

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
OUT = ROOT / "docs/images/demo.gif"
FFMPEG = os.environ.get("FFMPEG") or shutil.which("ffmpeg") or "ffmpeg"
WIDTH = 360  # portrait phone GIF, shown at 320 px in the README
FPS = 12
SPEED = 12  # distinct frames per second after dropping frozen ones (lower = slower)
RAW_VIDEO = HERE / "raw" / "demo.mp4"


def cold_start() -> None:
    """A cold start always lands on the Today tab, whatever screen was open before."""
    d.shell("input keyevent KEYCODE_HOME")
    d.shell(f"am force-stop {d.PACKAGE}")
    d.launch()
    if d.find("Berikutnya", timeout=20) is None:
        raise RuntimeError("Today screen did not load")
    d.wait(1500)


def flow() -> None:
    d.wait(800)
    d.tap_fab()  # "+ Tambah"
    d.tap_text("Transaksi", exact=True, timeout=12)
    if d.find("Masukkan nominal", timeout=12) is None:
        raise RuntimeError("The transaction sheet did not open")
    for key in ("2", "5", "000"):
        d.tap_text(key, exact=True)
    d.tap_text("Makan · ")  # wallet chip
    d.tap_text("Catatan (opsional)")
    d.wait(600)
    d.type_text("Kopi susu", per_char_ms=90)
    # The save button stays above the keyboard; pressing back here would close the sheet.
    d.tap_text("Simpan Rp")
    d.tap_text("Uang", exact=True, timeout=12)  # show the new row in Keuangan
    d.find("Kopi susu", timeout=12)
    d.wait(800)


def record() -> None:
    cold_start()
    remote = "/sdcard/today-demo.mp4"
    rec = subprocess.Popen([d.ADB, "shell", "screenrecord", "--bit-rate", "12000000", remote])
    try:
        flow()
    finally:
        d.shell("pkill -INT screenrecord")
        rec.wait(timeout=20)
    d.wait(1500)  # let the mp4 finish writing
    RAW_VIDEO.parent.mkdir(exist_ok=True)
    d.adb("pull", remote, str(RAW_VIDEO))
    d.shell(f"rm {remote}")


def encode() -> None:
    tmp = Path(tempfile.mkdtemp())
    palette = tmp / "palette.png"
    # Drop frozen frames, replay what is left at SPEED fps, hold the final frame for 2.5 s.
    base = (f"mpdecimate=hi=768:lo=320:frac=0.33,setpts=N/{SPEED}/TB,"
            f"tpad=stop_mode=clone:stop_duration=2.5,fps={FPS},scale={WIDTH}:-2:flags=lanczos")
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", str(RAW_VIDEO),
                    "-vf", f"{base},palettegen=stats_mode=diff", str(palette)], check=True)
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", str(RAW_VIDEO), "-i", str(palette),
                    "-lavfi", f"{base}[x];[x][1:v]paletteuse=dither=sierra2_4a", str(OUT)], check=True)
    shutil.rmtree(tmp, ignore_errors=True)
    print(f"wrote {OUT} ({OUT.stat().st_size / 1e6:.1f} MB)")


def main() -> None:
    """`python record_demo.py` records and encodes; `--encode-only` re-encodes raw/demo.mp4."""
    if not shutil.which(FFMPEG) and not Path(FFMPEG).exists():
        raise SystemExit("ffmpeg not found. Install it (winget install Gyan.FFmpeg) or set FFMPEG.")
    if "--encode-only" not in sys.argv:
        record()
    encode()


if __name__ == "__main__":
    main()
