"""Drives the phone by label and records clips for the videos. Usage: python -I tools/phone_drive.py <step> <step> ...
Never sends the system Back key (it can leave the app): `back` taps the in-app back arrow at 126,174. Refuses to tap when the Workshop is not in front.
steps: tap:<text> | xy:x,y | type:<text> | swipe:x1,y1,x2,y2[,ms] | text | shot:<name> | wait:<seconds> | back | scroll | kill | launch
       rec:<name>:<seconds>   starts a screen recording of at most <seconds> (<= 170); every tap, swipe and key after it is logged with its time
       endrec                 waits for the recording to end and pulls raw\\<name>.mp4 and raw\\<name>.taps.json
tap:<text> taps the first visible element whose text or description contains <text>."""
import json
import os
import pathlib
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = r"C:\Users\lordhasib\hsib-toolchain\sdk\platform-tools\adb.exe"
SERIAL = "QV7124F53K"
ROOT = pathlib.Path(__file__).resolve().parent.parent
RAW = ROOT / "store" / "videos" / "raw"
HERE = RAW / "shots"
HERE.mkdir(parents=True, exist_ok=True)
STATE = {"proc": None, "t0": 0.0, "name": "", "taps": []}


def adb(*args, binary=False):
    result = subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True)
    return result.stdout if binary else (result.stdout + result.stderr).decode("utf8", "replace")


def log(kind, x=None, y=None, x2=None, y2=None, ms=0):
    if STATE["proc"] is not None:
        STATE["taps"].append({"t": round(time.time() - STATE["t0"], 3), "kind": kind, "x": x, "y": y, "x2": x2, "y2": y2, "ms": ms})


def nodes():
    for attempt in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        xml = adb("exec-out", "cat", "/sdcard/ui.xml")
        if "<" in xml:
            break
        time.sleep(1)
    out = []
    STATE["banner"] = False
    for n in ET.fromstring(xml[xml.index("<"):]).iter("node"):
        label = (n.get("text") or n.get("content-desc") or "").strip()
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
        if m and label and n.get("package") not in ("com.hsib.sib", "com.google.android.inputmethod.latin") and 70 < int(m.group(2)) < 700 and "systemui" in (n.get("package") or ""):
            STATE["banner"] = True          # a notification banner (or any system window) is covering the top of the screen
        if label and m:
            x1, y1, x2, y2 = map(int, m.groups())
            out.append((label, (x1 + x2) // 2, (y1 + y2) // 2, y1, y2))
    return out


def tap_at(x, y):
    log("tap", x, y)
    adb("shell", "input", "tap", str(x), str(y))


def halt():
    """Stops the screen recording cleanly (so the file is playable) and keeps what was filmed as raw/<name>.partial.mp4."""
    if STATE["proc"] is None:
        return
    adb("shell", "pkill", "-2", "screenrecord")
    time.sleep(3)
    name = STATE["name"]
    adb("pull", "/sdcard/%s.mp4" % name, str(RAW / (name + ".partial.mp4")))
    (RAW / (name + ".partial.taps.json")).write_text(json.dumps(STATE["taps"]))
    adb("shell", "rm", "/sdcard/%s.mp4" % name)
    STATE["proc"] = None


def in_front():
    focus = adb("shell", "dumpsys window | grep mCurrentFocus")
    extra = os.environ.get("HSIB_ALLOW", "@@@")
    return "com.hsib.sib" in focus or "com.google.android.inputmethod" in focus or extra in focus


def reach(label, low=1700, high=2200, anyx=False, forward_first=False):
    """The words on screen after scrolling the bottom sheet (slow swipes, no fling) until the field called label sits between low and high."""
    found = nodes()
    for attempt in range(14):
        matches = [n for n in found if n[0] == label and (anyx or 400 < n[1] < 700) and n[3] > 1500]
        hit = max(matches, key=lambda n: n[2]) if matches else None        # the field is the lowest of the words with that name (a header may share it)
        if hit and low <= hit[2] <= high:
            return found
        forward = hit is not None and hit[2] > high
        if hit is None:
            forward = (attempt < 7) if forward_first else (attempt >= 5 or attempt % 4 == 3)         # out of sight: first look back up, then forward (or the other way round)
        if forward:
            adb("shell", "input", "swipe", "540", "2150", "540", "1900", "700")
        else:
            adb("shell", "input", "swipe", "540", "1800", "540", "2050", "700")
        time.sleep(0.9)
        found = nodes()
    return found


def run(step):
    kind, _, arg = step.partition(":")
    if kind in ("tap", "tap?", "tapx", "tapr", "chip", "code", "selectall", "clearcode", "tohub", "set", "enter", "hidekb", "xy", "dbl", "drag", "align", "back", "scroll", "swipe", "type", "rep") and not in_front():
        halt()
        sys.exit("STOPPED: the Workshop is not in front, so '%s' was not sent" % step)
    if kind in ("tap", "tap?"):
        found = nodes()
        if STATE.get("banner"):
            halt()
            sys.exit("STOPPED: something from the system (a notification banner?) covers the top of the screen, so '%s' was not tapped" % step)
        hit = next((n for n in found if arg.lower() in n[0].lower()), None)
        print("tap", arg, "->", hit[:3] if hit else "NOT FOUND")
        if hit:
            tap_at(hit[1], hit[2])
        elif kind == "tap":
            halt()
            sys.exit("STOPPED: '%s' is not on the screen" % arg)
        time.sleep(1.4)
    elif kind == "tohub":
        # goes back with the in-app arrow until the hub (the card The Atelier) is showing
        for _ in range(7):
            found = nodes()
            if any(n[0] == "The Atelier" for n in found):
                break
            arrow = next((n for n in found if n[0] == "Back"), None)
            if arrow is None:
                if STATE["proc"] is not None:
                    halt()
                sys.exit("STOPPED: no Back arrow on the screen and not at the hub")
            tap_at(arrow[1], arrow[2])
            time.sleep(1.7)
    elif kind == "chip":
        # chip:<label>  taps a chip in the sideways row of the examples; slides the row when the chip is out of sight
        for attempt in range(5):
            found = nodes()
            hit = next((n for n in found if n[0] == arg and n[3] < 400), None)
            if hit is None and not any(n[0] in ("Hello", "Waves", "Golden spiral", "Bouncing balls", "Rose curve") and n[3] < 400 and n[1] != 393 for n in found):
                opener = next((n for n in found if n[0] == "Examples"), None)         # the row is closed: open it
                if opener:
                    tap_at(opener[1], opener[2])
                    time.sleep(1.5)
                    continue
            if hit:
                tap_at(hit[1], hit[2])
                break
            swipe = [900, 253, 250, 253, 300] if attempt < 3 else [250, 253, 900, 253, 300]
            log("swipe", *swipe[:4], swipe[4])
            adb("shell", "input", "swipe", *map(str, swipe))
            time.sleep(1.0)
        else:
            halt()
            sys.exit("STOPPED: chip '%s' not found" % arg)
        time.sleep(1.2)
    elif kind == "tapr":
        # tapr:<label>  scrolls the bottom sheet until the label is in view, then taps it
        found = reach(arg, anyx=True, forward_first=True)
        hit = next((n for n in found if n[0] == arg and n[3] > 1500), None)
        print("tapr", arg, "->", hit[:3] if hit else "NOT FOUND")
        if hit is None:
            halt()
            sys.exit("STOPPED: '%s' is not on the screen" % arg)
        tap_at(hit[1], hit[2])
        time.sleep(1.2)
    elif kind == "selectall":
        # in the code editor with the keyboard up: a long press on the code brings the Select all popup; then everything is deleted
        adb("shell", "input", "swipe", "400", "1150", "400", "1150", "900")
        time.sleep(1.4)
        tap_at(195, 1035)
        time.sleep(0.8)
        log("key")
        adb("shell", "input", "keyevent", "67")
        time.sleep(0.8)
    elif kind == "code":
        # code:<file>  types the lines of a text file into the code editor; the editor keeps the indent of the line before (and adds four after a colon),
        # so each line is corrected with spaces or Delete presses to the indent the file wants
        lines = pathlib.Path(arg).read_text(encoding="utf-8").splitlines()
        auto, prev_colon, prev_indent = 0, False, 0
        for number, line in enumerate(lines):
            want = len(line) - len(line.lstrip(" "))
            body = line.strip()
            parts = []
            if number:
                parts.append("input keyevent 66; sleep 0.4")
                auto = (prev_indent + (4 if prev_colon else 0))
                if want < auto:
                    parts.append("input keyevent " + " ".join(["67"] * (auto - want)) + "; sleep 0.2")
                elif want > auto:
                    parts.append("input text '" + "%s" * (want - auto) + "'")
            if body:
                parts.append("input text '" + body.replace(" ", "%s") + "'")
            log("key")
            adb("shell", "; ".join(parts) if parts else "true")
            prev_indent, prev_colon = want, body.endswith(":")
            time.sleep(0.25)
        for _ in range(3):                                           # anything left after the typed code (the old code) goes
            adb("shell", "input", "keyevent", *(["112"] * 150))
        time.sleep(1.0)
        for n in nodes():
            if chr(10) in n[0] and len(n[0]) > 30 and n[0].split(chr(10))[0] == lines[0]:
                got = n[0].split(chr(10))
                bad = [(i + 1, w, g) for i, (w, g) in enumerate(zip(lines, got)) if w != g]
                print("code typed:", "exactly as written" if not bad and len(got) == len(lines) else "DIFFERENT %s (%d lines typed of %d)" % (bad[:4], len(got), len(lines)))
    elif kind == "clearcode":
        # in the code editor with the keyboard up: the cursor goes to the very end (Down, then End) and everything is deleted backwards
        log("key")
        for _ in range(4):                                           # forward delete (whatever is after the cursor), then backwards (whatever is before it), in batches
            adb("shell", "input", "keyevent", *(["112"] * 150))
        for _ in range(4):
            adb("shell", "input", "keyevent", *(["67"] * 150))
        time.sleep(0.8)
    elif kind == "tapx":
        # tapx:<exact label>[#n]  taps the n-th (default first) element whose words are exactly the label
        label, _, nth = arg.partition("#")
        found = [n for n in nodes() if n[0] == label]
        hit = found[int(nth or 0)] if len(found) > int(nth or 0) else None
        print("tapx", arg, "->", hit[:3] if hit else "NOT FOUND")
        if hit is None:
            halt()
            sys.exit("STOPPED: '%s' is not on the screen" % arg)
        tap_at(hit[1], hit[2])
        time.sleep(1.2)
    elif kind == "set":
        # set:<field label>=<text>  clears the field (its Clear button) and types the text
        label, _, text = arg.partition("=")
        found = reach(label)
        clear = next((n for n in found if n[0] == "Clear " + label), None)
        cands = [n for n in found if n[0] == label and 400 < n[1] < 700] or [n for n in found if n[0] == label]
        field = max(cands, key=lambda n: n[2]) if cands else None          # the field sits below a header of the same name
        print("set", label, "->", field[:3] if field else "NOT FOUND")
        if field is None:
            halt()
            sys.exit("STOPPED: field '%s' is not on the screen" % label)
        if clear:
            tap_at(clear[1], clear[2])
            time.sleep(0.6)
        tap_at(field[1], field[2])
        time.sleep(0.7)
        if not clear:
            adb("shell", "input", "keyevent", "123", *["67"] * 24)       # end of the field, then delete backwards
        log("key")
        adb("shell", "input", "text", "'" + text.replace(" ", "%s") + "'")
        time.sleep(0.7)
    elif kind == "enter":
        # closes the keyboard with its Done key; only when the keyboard is up (with no field in focus Enter presses the focused Back arrow and leaves the editor)
        if "mInputShown=true" in adb("shell", "dumpsys", "input_method"):
            log("key")
            adb("shell", "input", "keyevent", "66")
        else:
            print("enter skipped: no keyboard")
        time.sleep(0.8)
    elif kind == "hidekb":
        # hides the keyboard with the system key, only while the keyboard is really up (that key then only closes it)
        if "mInputShown=true" in adb("shell", "dumpsys", "input_method"):
            adb("shell", "input", "keyevent", "4")
        else:
            print("hidekb skipped: no keyboard")
        time.sleep(0.8)
    elif kind == "xy":
        x, y = arg.split(",")
        tap_at(int(x), int(y))
        time.sleep(1.0)
    elif kind == "drag":
        label, _, delta = arg.partition(">")
        dx, dy, ms = [int(v) for v in delta.split(",")]
        hit = next((n for n in nodes() if label.lower() in n[0].lower()), None)
        print("drag", label, "->", hit[:3] if hit else "NOT FOUND")
        if hit:
            a = [hit[1], hit[2], hit[1] + dx, hit[2] + dy]
            log("swipe", a[0], a[1], a[2], a[3], ms)
            adb("shell", "input", "swipe", *map(str, a), str(ms))
        time.sleep(ms / 1000 + 0.8)
    elif kind == "rep":
        x, y, count, gap = arg.split(",")
        for _ in range(int(count)):
            tap_at(int(x), int(y))
            time.sleep(float(gap))
        time.sleep(0.6)
    elif kind == "align":
        mover, _, target = arg.partition(">")
        found = nodes()
        def pick(name):
            exact = [n for n in found if n[0].lower() == name.lower()]
            return exact[0] if exact else next((n for n in found if name.lower() in n[0].lower()), None)
        a, b = pick(mover), pick(target)
        print("align", mover, "->", a[:3] if a else "NOT FOUND", "onto", b[:3] if b else "NOT FOUND")
        if a and b:
            log("swipe", a[1], a[2], b[1], b[2], 900)
            adb("shell", "input", "swipe", str(a[1]), str(a[2]), str(b[1]), str(b[2]), "900")
        time.sleep(1.8)
    elif kind == "dbl":
        if arg.startswith("@"):
            hit = next((n for n in nodes() if arg[1:].lower() in n[0].lower()), None)
            print("dbl", arg, "->", hit[:3] if hit else "NOT FOUND")
            arg = "%d,%d" % (hit[1], hit[2]) if hit else "540,1200"
        x, y = arg.split(",")
        log("tap", int(x), int(y))
        log("tap", int(x), int(y))
        adb("shell", "input tap %s %s; input tap %s %s" % (x, y, x, y))
        time.sleep(1.2)
    elif kind == "text":
        for label, x, y, y1, y2 in nodes():
            print("  %4d %5d %s" % (y, x, label.replace("\n", " / ")[:110]))
    elif kind == "shot":
        (HERE / (arg + ".png")).write_bytes(adb("exec-out", "screencap", "-p", binary=True))
        print("saved", arg)
    elif kind == "mark":
        log("mark")
        print("mark", arg, round(time.time() - STATE["t0"], 2))
    elif kind == "wait":
        time.sleep(float(arg))
    elif kind == "back":
        tap_at(126, 174)
        time.sleep(1.2)
    elif kind == "scroll":
        a = [540, 1900, 540, 700, 350]
        log("swipe", a[0], a[1], a[2], a[3], 350)
        adb("shell", "input", "swipe", *map(str, a))
        time.sleep(1.2)
    elif kind == "swipe":
        a = [int(v) for v in arg.split(",")]
        ms = a[4] if len(a) > 4 else 300
        log("swipe", a[0], a[1], a[2], a[3], ms)
        adb("shell", "input", "swipe", *map(str, a[:4]), str(ms))
        time.sleep(ms / 1000 + 0.6)
    elif kind == "type":
        log("key")
        adb("shell", "input", "text", "'" + arg.replace(" ", "%s") + "'")
        time.sleep(0.8)
    elif kind == "start":
        adb("shell", "am", "start", "-n", arg)
        time.sleep(0.5)
    elif kind == "stop":
        adb("shell", "am", "force-stop", arg)
        time.sleep(0.8)
    elif kind == "kill":
        adb("shell", "am", "force-stop", "com.hsib.sib")
        time.sleep(0.8)
    elif kind == "launch":
        adb("shell", "monkey", "-p", "com.hsib.sib", "-c", "android.intent.category.LAUNCHER", "1")
        time.sleep(1.5)
    elif kind == "rec":
        name, seconds = arg.split(":")
        STATE.update(name=name, taps=[], t0=time.time())
        STATE["proc"] = subprocess.Popen([ADB, "-s", SERIAL, "shell", "screenrecord", "--time-limit", seconds, "/sdcard/%s.mp4" % name])
        time.sleep(0.6)
    elif kind == "endrec":
        STATE["proc"].wait()
        name = STATE["name"]
        (RAW / (name + ".mp4")).write_bytes(b"")
        adb("pull", "/sdcard/%s.mp4" % name, str(RAW / (name + ".mp4")))
        adb("shell", "rm", "/sdcard/%s.mp4" % name)
        (RAW / (name + ".taps.json")).write_text(json.dumps(STATE["taps"]))
        print("recorded", name, (RAW / (name + ".mp4")).stat().st_size // 1024, "KB,", len(STATE["taps"]), "taps")
        STATE["proc"] = None
    else:
        print("unknown step", step)


if __name__ == "__main__":
    for step in sys.argv[1:]:
        run(step)
