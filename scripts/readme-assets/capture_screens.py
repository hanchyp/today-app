"""Capture the raw phone screenshots used by the README.

Runs on the Android emulator (or any device on adb) at 390x844 dp, 3x.
Does a fresh install of the debug APK, finishes onboarding with the
built-in sample data, adds a starting balance and one wallet, then walks
through every screen the README shows. Output goes to ./raw/*.png, which
compose.mjs turns into the final images in docs/images/.

    python capture_screens.py            # all screens
    python capture_screens.py --keep     # leave the emulator in demo mode afterwards
"""
from __future__ import annotations

import sys
from pathlib import Path

import adb_driver as d

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
RAW = HERE / "raw"
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"


def snap(name: str, ready: str | None = None, settle_ms: int = 900) -> None:
    """Wait for a piece of text that only shows once data has loaded, then capture."""
    if ready and d.find(ready, timeout=20) is None:
        raise RuntimeError(f"{name}: '{ready}' never appeared")
    d.wait(settle_ms)
    d.screenshot(RAW / f"{name}.png")
    print("captured", name)


def tab(label: str) -> None:
    d.tap_text(label, exact=True)
    d.wait(600)


def onboarding() -> None:
    d.tap_text("Nama panggilan", timeout=20)
    d.wait(400)
    d.shell("input keyevent KEYCODE_MOVE_END")
    for _ in range(15):
        d.shell("input keyevent KEYCODE_DEL")
    d.type_text("Raka")
    d.hide_keyboard()
    d.tap_text("Lanjut", exact=True)
    d.wait(800)
    d.tap_text("Lanjut", exact=True)  # keep all four modules
    d.wait(800)
    d.tap_text("Data contoh", exact=True)
    d.tap_text("Mulai pakai Today")


def dialog_amount(title_tap: str, value: str) -> None:
    d.tap_text(title_tap, exact=True, timeout=15)
    d.wait(700)
    d.type_text(value)
    d.wait(300)
    d.tap_text("Simpan", exact=True)
    d.wait(1600)  # let the dialog close before the next lookup


def setup_finance() -> None:
    """Starting balance plus a weekly 'Makan' wallet, so the finance screens are not empty."""
    tab("Uang")
    d.tap_text("Kelola", exact=True)
    d.wait(1200)
    dialog_amount("+ Tambah saldo", "1500000")
    dialog_amount("+ Dompet", "Makan")
    dialog_amount("Makan", "300000")
    d.back()
    d.wait(800)


def log_first_set(kg_steps: int = 16, step_ms: int = 280) -> None:
    """Open set 1, raise the weight (2.5 kg per step, 16 steps = 40 kg) and tick it off."""
    check = d.find("Set 1", exact=True, timeout=10)  # the check box at the end of the row
    d.tap(int(d.screen_size()[0] * 0.55), check[1])  # tapping the row opens the editor
    d.wait(600)
    plus = d.find("Tambah Beban", timeout=5)
    for _ in range(kg_steps):
        d.tap(*plus)
        d.wait(step_ms)
    d.wait(400)
    d.tap(*check)


def main() -> None:
    d.setup_device()
    d.fresh_install(APK)
    d.launch()
    onboarding()

    snap("today", ready="Berikutnya", settle_ms=1500)
    d.tap_text("Kalender", exact=True)
    snap("calendar", ready="AGENDA")
    d.back()

    tab("Latihan")
    snap("workout_list", ready="Rutinitas")

    tab("Karier")
    snap("career_list", ready="Awan Biru Labs")
    d.tap_text("Awan Biru Labs", exact=True)
    snap("career_detail", ready="Catatan")
    d.back()

    setup_finance()
    snap("finance", ready="Transaksi terbaru")
    d.tap_fab()  # "+ Catat"
    d.wait(1000)
    for digit in "45000":
        d.tap_text(digit, exact=True, timeout=4)
        d.wait(150)
    d.tap_text("Makan · ", timeout=4)  # wallet chip
    snap("finance_add", ready="Catat transaksi")
    d.back()
    d.wait(600)

    tab("Tugas")
    snap("tasks", ready="Berikutnya")
    d.tap_text("Skripsi")
    snap("project", ready="Revisi Bab 3")
    d.back()

    tab("Today")
    d.tap_text("Pengaturan", exact=True)
    snap("settings", ready="Pengaturan")
    for _ in range(3):
        d.scroll_down(1100)
    snap("settings_backup", ready="Backup")
    d.back()

    tab("Latihan")
    d.tap_text("Mulai latihan")
    d.find("Bench press", timeout=20)
    d.wait(800)
    log_first_set()
    snap("workout_session", ready="TIMER ISTIRAHAT", settle_ms=2500)
    d.tap_text("Lihat animasi")
    snap("exercise_animation", ready="Tutup", settle_ms=1500)
    d.tap_text("Tutup", exact=True)

    if "--keep" not in sys.argv:
        d.reset_device()


if __name__ == "__main__":
    main()
