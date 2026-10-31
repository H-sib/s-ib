"""Checks video specs against the raw recordings BEFORE a render (a render takes minutes, this takes seconds) and prints only problems.
Usage: python tools\\video_lint.py <spec.txt> [<spec.txt> ...] [--raw store\\videos\\raw]      or      python tools\\video_lint.py all     (every spec in store\\videos\\specs and store\\videos\\specs\\shorts)
ERROR = the render would be wrong or refused. WARN = probably a mistake, look at it. Exit code 1 when there is any ERROR.
Checks: the file and the touch log exist; start and end lie inside the recording (real frame times, not guessed); the range does not touch a stretch in raw\\blocked.txt; the screen actually changes in the range (a still
screen is dead time); at least one touch in a clip that is a tutorial step; the speed up is readable (over 14 times is not); no slow motion stretch; caption of at most 6 words; zoom not closer than the cap; durations in beats
have a bpm; the music file exists; a Short stays under 60 seconds; the same stretch is not used twice in one video; the last scene is a card."""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import make_video as mv          # noqa: E402  (importing it does not render anything)


def lint(spec, raw):
    errors, warns = [], []
    try:
        scenes, header = mv.parse(spec)
    except SystemExit as stop:
        return ["cannot parse: %s" % stop], [], 0.0
    bpm = header["bpm"]
    for problem in mv.register_looks(header):
        errors.append(problem)
    if header["look"] not in mv.LOOKS:
        errors.append("unknown look %s (known: %s)" % (header["look"], ", ".join(mv.LOOKS)))
    if header["music"] and not (mv.MUSIC / header["music"][0]).exists():
        errors.append("music file %s is missing in store\\videos\\music (python tools\\make_music.py store\\videos\\music)" % header["music"][0])
    if not header["music"]:
        warns.append("no music line")
    blocked = []
    bfile = raw / "blocked.txt"
    if bfile.exists():
        for line in bfile.read_text(encoding="utf-8").splitlines():
            part = [x.strip() for x in line.split("|")]
            if len(part) >= 3 and not line.startswith("#"):
                try:
                    blocked.append((part[0], float(part[1]), float(part[2])))
                except ValueError:
                    pass
    if any(s.get("opts", {}).get("dur", "").endswith("b") or str(s.get("dur", "")).endswith("b") for s in scenes) and not bpm:
        errors.append("a duration is in beats (b) but the music line has no bpm")
    total, used = 0.0, []
    for number, scene in enumerate(scenes, 1):
        tag = "scene %d (%s)" % (number, scene["kind"])
        try:
            frames = mv.frames_of(scene, bpm)
        except Exception as problem:
            errors.append("%s: bad duration (%s)" % (tag, problem))
            continue
        seconds = frames / mv.FPS
        total += seconds
        opts = scene["opts"]
        caption = scene.get("text", "")
        words = len(caption.replace("*", "").split())
        if words > 6:
            warns.append("%s: caption has %d words (6 at most): %s" % (tag, words, caption[:40]))
        if "zoom" in opts:
            try:
                z = mv.zoom_of(opts)
                if max(z[2], z[3]) > mv.MAX_ZOOM:
                    warns.append("%s: zoom %.2f is closer than the cap %.2f (it will be capped)" % (tag, max(z[2], z[3]), mv.MAX_ZOOM))
            except Exception:
                errors.append("%s: bad zoom option %s" % (tag, opts["zoom"]))
        if scene["kind"] == "still":
            if not (raw / scene["file"]).exists():
                errors.append("%s: picture %s is missing in the raw folder" % (tag, scene["file"]))
            continue
        if scene["kind"] != "clip":
            continue
        path = raw / scene["file"]
        a, b = scene["start"], scene["end"]
        if not path.exists():
            errors.append("%s: recording %s is missing" % (tag, scene["file"]))
            continue
        if b <= a:
            errors.append("%s: end %.1f is not after start %.1f" % (tag, b, a))
            continue
        times = mv.pts_index(path)
        last = times[-1] if times else 0
        if a < 0 or b > last + 0.5:
            errors.append("%s: %s %.1f to %.1f is outside the recording (it ends at %.1f)" % (tag, scene["file"], a, b, last))
            continue
        for name, x, y in blocked:
            if name == scene["file"] and a < y and b > x:
                errors.append("%s: %s %.1f to %.1f overlaps a blocked stretch (%.1f to %.1f)" % (tag, scene["file"], a, b, x, y))
        stored = sum(1 for t in times if a <= t <= b)
        touches = [t for t in mv.load_touches(path) if t["kind"] in ("tap", "swipe") and a <= t["t"] - mv.LATENCY <= b]
        if not path.with_name(path.stem + ".taps.json").exists():
            warns.append("%s: %s has no touch log (no touch rings or tap sounds)" % (tag, scene["file"]))
        if stored <= 2 and not touches:
            warns.append("%s: the screen does not change between %.1f and %.1f of %s (dead time)" % (tag, a, b, scene["file"]))
        elif not touches and (b - a) > 6:
            warns.append("%s: %.0f seconds of %s with no touch in it" % (tag, b - a, scene["file"]))
        speed = (b - a) / max(seconds, 0.1)
        if speed > 14 and "ramp" not in opts and (len(touches) < 2 or speed > 30):          # with several touches the auto speed keeps each tap near real time
            warns.append("%s: %.0f times faster than real time, too fast to read" % (tag, speed))
        if speed < 0.9 and "dur" in opts:
            warns.append("%s: slow motion (%.1f times); the range is shorter than its duration, widen it or shorten dur" % (tag, speed))
        for name, x, y in used:
            if name == scene["file"] and a < y - 1 and b > x + 1:
                warns.append("%s: %s %.1f to %.1f repeats a stretch already used in this video" % (tag, scene["file"], a, b))
        used.append((scene["file"], a, b))
    if scenes and scenes[-1]["kind"] != "title":
        warns.append("the last scene is not a card (title)")
    if header["format"] == "short" and total >= 60:
        errors.append("a Short must stay under 60 seconds (this one is %.1f)" % total)
    return errors, warns, total


def main():
    args = sys.argv[1:]
    raw = ROOT / "store" / "videos" / "raw"
    if "--raw" in args:
        k = args.index("--raw")
        raw = pathlib.Path(args[k + 1])
        del args[k:k + 2]
    if not args or args == ["all"]:
        folder = ROOT / "store" / "videos" / "specs"
        args = sorted(str(p) for p in list(folder.glob("*.txt")) + list((folder / "shorts").glob("*.txt")) if not p.name.startswith("_"))
    bad = 0
    for item in args:
        spec = pathlib.Path(item)
        errors, warns, total = lint(spec, raw)
        status = "ERROR" if errors else ("warn" if warns else "ok")
        print("%-5s %-34s %5.1f s" % (status, spec.name, total))
        for line in errors:
            print("      ERROR " + line)
        for line in warns:
            print("      warn  " + line)
        bad += 1 if errors else 0
    print("%d spec(s) checked, %d with errors" % (len(args), bad))
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
