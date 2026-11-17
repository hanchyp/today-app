"""Small helper for driving the Android emulator over adb.

Used by capture_screens.py and record_demo.py. Elements are found by their
visible text or content description through `uiautomator dump`, so the
scripts keep working when layouts move a little.
"""
from __future__ import annotations

import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = os.environ.get("ADB", r"F:\Sdk\platform-tools\adb.exe")
PACKAGE = "com.farhan.today"


def adb(*args: str, check: bool = True, capture: bool = True) -> str:
    out = subprocess.run([ADB, *args], check=check, capture_output=capture, text=True,
                         encoding="utf-8", errors="replace")
    return (out.stdout or "") if capture else ""


def shell(cmd: str) -> str:
    return adb("shell", cmd)


def wait(ms: int) -> None:
    time.sleep(ms / 1000)


def screenshot(path: Path) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    data = subprocess.run([ADB, "exec-out", "screencap", "-p"], check=True, capture_output=True).stdout
    path.write_bytes(data)
    return path


def _nodes():
    shell("uiautomator dump /sdcard/ui.xml")
    xml = adb("exec-out", "cat /sdcard/ui.xml")
    xml = xml[xml.find("<?xml"):]
    root = ET.fromstring(xml)
    for n in root.iter("node"):
        b = re.findall(r"\d+", n.get("bounds", "[0,0][0,0]"))
        x1, y1, x2, y2 = map(int, b)
        yield n, ((x1 + x2) // 2, (y1 + y2) // 2), (x1, y1, x2, y2)


def find(text: str, exact: bool = False, timeout: float = 8.0):
    """Return the centre of the first node whose text or content-desc matches."""
    end = time.time() + timeout
    while time.time() < end:
        for n, centre, _ in _nodes():
            for attr in ("text", "content-desc"):
                v = n.get(attr) or ""
                if (v == text) if exact else (text.lower() in v.lower()):
                    return centre
        wait(400)
    return None


def visible_texts() -> list[str]:
    return [t for n, _, _ in _nodes() for t in (n.get("text"), n.get("content-desc")) if t]


def tap(x: int, y: int) -> None:
    shell(f"input tap {x} {y}")


def tap_text(text: str, exact: bool = False, timeout: float = 8.0) -> None:
    pos = find(text, exact, timeout)
    if pos is None:
        raise RuntimeError(f"Element not found: {text!r}")
    tap(*pos)


def type_text(text: str, per_char_ms: int = 0) -> None:
    """Type text. With per_char_ms > 0 it types one character at a time (human speed)."""
    if per_char_ms <= 0:
        shell("input text " + text.replace(" ", "%s"))
        return
    for ch in text:
        shell("input text " + ("%s" if ch == " " else ch))
        wait(per_char_ms)


def tap_fab() -> None:
    """Tap the bottom-right extended FAB (its label is not always in the uiautomator tree)."""
    w, h = screen_size()
    tap(int(w * 0.80), int(h * 0.825))


def swipe(x1: int, y1: int, x2: int, y2: int, ms: int = 350) -> None:
    shell(f"input swipe {x1} {y1} {x2} {y2} {ms}")


def scroll_down(amount: int = 900) -> None:
    w, h = screen_size()
    swipe(w // 2, h * 3 // 4, w // 2, h * 3 // 4 - amount, 450)


def scroll_up(amount: int = 900) -> None:
    w, h = screen_size()
    swipe(w // 2, h // 4, w // 2, h // 4 + amount, 450)


def back() -> None:
    shell("input keyevent KEYCODE_BACK")


def hide_keyboard() -> None:
    if "mInputShown=true" in shell("dumpsys input_method"):
        back()


def screen_size() -> tuple[int, int]:
    m = re.search(r"(\d+)x(\d+)", shell("wm size").splitlines()[-1])
    return int(m.group(1)), int(m.group(2))


def setup_device(width_px: int = 1170, height_px: int = 2532, density: int = 480) -> None:
    """390x844 dp at 3x plus a clean demo-mode status bar (9:41, full battery)."""
    shell(f"wm size {width_px}x{height_px}")
    shell(f"wm density {density}")
    shell("settings put global sysui_demo_allowed 1")
    demo = "am broadcast -a com.android.systemui.demo -e command"
    for cmd in (
        "enter",
        "clock -e hhmm 0941",
        "battery -e level 100 -e plugged false",
        "network -e wifi show -e level 4 -e mobile hide",
        "notifications -e visible false",
    ):
        shell(f"{demo} {cmd}")


def reset_device() -> None:
    shell("am broadcast -a com.android.systemui.demo -e command exit")
    shell("wm size reset")
    shell("wm density reset")


def fresh_install(apk: Path) -> None:
    adb("uninstall", PACKAGE, check=False)
    adb("install", "-r", str(apk))
    shell(f"pm grant {PACKAGE} android.permission.POST_NOTIFICATIONS")


def launch() -> None:
    shell(f"monkey -p {PACKAGE} -c android.intent.category.LAUNCHER 1")


def open_route(route: str) -> None:
    """Open an in-app route the same way a notification does (MainActivity EXTRA_ROUTE)."""
    shell(f"am start -n {PACKAGE}/.MainActivity --es route '{route}'")
