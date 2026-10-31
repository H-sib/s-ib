"""Finds things on the phone's screen by their words. Usage:
  python ui.py list              prints the words on screen with their centres
  python ui.py tap "Add"         taps the first thing whose text or description is exactly Add (or contains it with --has)
  python ui.py tap --has "Edit"  taps the first thing that contains the words
Only ever taps inside the Workshop: it refuses when another app is in front."""
import subprocess, sys, re, xml.etree.ElementTree as ET, os

ADB = r"C:\Users\lordhasib\hsib-toolchain\sdk\platform-tools\adb.exe"
SER = "QV7124F53K"
ENV = dict(os.environ, MSYS_NO_PATHCONV="1")


def adb(*a):
    return subprocess.run([ADB, "-s", SER, *a], capture_output=True, text=True, env=ENV).stdout


def front():
    out = adb("shell", "dumpsys", "window")
    m = re.search(r"mCurrentFocus=Window\{[^ ]+ u0 ([^ }]+)", out)
    return m.group(1) if m else ""


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("shell", "cat", "/sdcard/ui.xml")
    root = ET.fromstring(xml[xml.index("<"):])
    found = []
    for n in root.iter("node"):
        words = (n.get("text") or "") or (n.get("content-desc") or "")
        b = re.findall(r"\d+", n.get("bounds") or "")
        if not words or len(b) != 4:
            continue
        x1, y1, x2, y2 = map(int, b)
        found.append((words.replace("\n", " "), (x1 + x2) // 2, (y1 + y2) // 2, n.get("clickable") == "true", (x1, y1, x2, y2)))
    return found


if __name__ == "__main__":
    here = front()
    if "app.hsib.workshop" not in here:
        sys.exit("The Workshop is not in front (" + here + "). Not tapping.")
    mode = sys.argv[1]
    items = nodes()
    if mode == "list":
        for w, x, y, c, _ in items:
            print(("* " if c else "  ") + w[:70], x, y)
    elif mode == "tap":
        has = "--has" in sys.argv
        want = [a for a in sys.argv[2:] if a != "--has"][0]
        pick = [i for i in items if (want in i[0] if has else i[0] == want)]
        if not pick:
            sys.exit("not found: " + want)
        w, x, y, _, _ = pick[0]
        adb("shell", "input", "tap", str(x), str(y))
        print("tapped", w[:60], x, y)
