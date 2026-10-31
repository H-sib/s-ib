"""Gives every app its OWN creative direction, so that the videos of ten different apps (or ten sessions given the same instruction) do not look, sound, move or read alike.
Usage: python tools\\video_direction.py roll <package id> "<app name>" [again]   rolls a direction card for the app, writes it to the shared ledger and prints it (the same package always gives the same card; 'again' gives the next one)
       python tools\\video_direction.py show <package id>                          prints the card of an app that already has one
       python tools\\video_direction.py list                                       one line per direction already used or claimed (the Workshop's seven are in it)
       python tools\\video_direction.py done <package id> "<one line of what was made>"
How it works: ten axes (concept, hook, rhythm, palette, type, backdrop and frame, transitions, sonic world, caption voice, ending), each with 12 to 16 options. The roll starts from a hash of the package id, so two apps
start in different places, then moves until the card differs from EVERY card in the ledger on at least 7 of the 10 axes. The ledger is a plain text file outside every project, so all sessions share it:
C:\\Users\\lordhasib\\Desktop\\HSIB_VIDEO_DIRECTION_LEDGER.txt  (set HSIB_LEDGER to use another path). Options that start with ~ are the Workshop's own and are never rolled.
The card is a starting direction, not a cage: the session may swap an option when the app's truth demands it (a serious banking app does not get confetti), must say which one and why, and writes the change into the ledger line (python tools\\video_direction.py show).
Every card tells the session what to build; tools\\make_video.py (newlook), tools\\make_music.py (--gen) and tools\\make_shorts.py need no code changes for a new direction."""
import datetime
import hashlib
import os
import pathlib
import sys

LEDGER = pathlib.Path(os.environ.get("HSIB_LEDGER", r"C:\Users\lordhasib\Desktop\HSIB_VIDEO_DIRECTION_LEDGER.txt"))
MIN_DIFFERENT = 7

CONCEPT = [
    "Countdown: ten to one on big numbers, one feature per number, the last one is the biggest",
    "One tap: the whole video follows the consequences of ONE tap; each cut is the next consequence",
    "Myth busting: three things people believe about this kind of app, each broken by one shot",
    "Before and after: two halves, the same task done the old way and done in the app (the old way is shown as plain text cards, never as a real product)",
    "Question chain: every caption is a question, the next shot answers it and raises the next",
    "The stopwatch: a visible timer is part of the picture, a real task is finished before it ends",
    "Cold open then rewind: start on the finished result, rewind in three jumps to the first tap",
    "Silent film: title cards between shots, no caption inside a shot",
    "The loop: the last frame is the first frame, so the video can replay forever",
    "The one detail: one tiny detail per shot (a switch, a sound, a corner): a love letter to details",
    "Three people: three made up uses as three short chapters with a name card each (names are made up and labelled)",
    "The hidden thing: show what most people never find (a real gesture or setting)",
    "A day in the life: hour cards from morning to night, the app used at each (made up data)",
    "Cheat sheet: five tips, each shown once, the five tips become the end card",
    "Challenge: a challenge to the viewer, shown being done at speed, ends with the challenge line",
    "Rhymes: pairs of shots that rhyme visually (a tap in one mood, a tap in another)",
    "~Feature tour: one feature per beat group (H-sib Workshop)",
]
HOOK = [
    "The finished result on screen in the first second, no title",
    "A number alone on a black card",
    "A question",
    "A contradiction in four words that the app really satisfies",
    "An extreme close up of one control",
    "One word on black, then the app slams in",
    "The app already in mid action, no title at all",
    "A countdown, three two one",
    "A real problem sentence (something people really say), then the app",
    "Half a second of black with only a tap sound, then the picture",
    "A swipe that wipes the title away",
    "Text typing itself, letter by letter",
    "~A big caption arriving word by word over the phone (H-sib Workshop)",
]
RHYTHM = [
    "Accelerating: shots of 4 beats, 2, 1, half, quarter, then a full stop",
    "Decelerating: fast opening, ends on one long calm hold",
    "Stop and start: bursts of four cuts, then a full beat of silence and a hold",
    "Off beat: cuts fall on the 'and' of each beat",
    "Long takes with one sudden burst of cuts in the middle",
    "Call and response: pairs of shots of equal length, a question shot then an answer shot",
    "Swung: long, short, long, short (3 to 1)",
    "Breathing: six slow beats, two fast beats, repeat",
    "Pyramid: shot lengths 1, 2, 3, 4, 3, 2, 1 beats",
    "Blocks: eight calm beats, eight fast beats, repeat",
    "Match cuts: every shot starts where the last one ended (the same screen position)",
    "Heartbeat: two quick cuts, a rest, two quick cuts, a rest",
    "~Metronome: every cut on a beat (H-sib Workshop)",
]
PALETTE = [
    "Dark ground, ONE accent taken from the app's most saturated colour",
    "Light paper ground, ink text, one accent from the app",
    "Duotone: only two of the app's colours",
    "Analogous: three neighbours of the app's main hue on the colour wheel",
    "Complementary: the app's main hue against its opposite",
    "Monochrome in the app's hue, five lightness steps",
    "Two neons on near black",
    "Earthy and muted: the app's colours at half saturation on warm paper",
    "A pastel tint of each of the app's colours on white",
    "High contrast black and white plus one signal colour",
    "A gradient ground between two of the app's colours",
    "A new colour for every feature (the ground changes colour at each group of cuts)",
    "~Black and one gold accent, ~flat colour per feature, ~pastel, ~poster red and yellow, ~violet confetti, ~dark editor green, ~ivory and gold (H-sib Workshop)",
]
TYPE = [
    "Heavy grotesk: font=bricolage:ExtraBold (open licence)",
    "High contrast serif bold: font=georgiab.ttf",
    "Condensed display capitals: font=impact.ttf upper=1",
    "Monospace: font=consolab.ttf",
    "Humanist friendly: font=candarab.ttf",
    "Hand script: font=segoeprb.ttf",
    "Classic serif: font=cambriab.ttf",
    "Rounded geometric: font=trebucbd.ttf",
    "Industrial DIN like, capitals: font=bahnschrift.ttf upper=1",
    "Wide black sans: font=verdanab.ttf",
    "Light elegant: font=segoeuisl.ttf",
    "Warm serif: font=constanb.ttf",
]
BACKDROP = [
    "backdrop=waves phone=thin", "backdrop=dots phone=solid", "backdrop=rings phone=soft", "backdrop=stripes phone=solid", "backdrop=confetti phone=soft", "backdrop=grid phone=thin",
    "backdrop=blobs phone=soft", "backdrop=plain phone=thin", "backdrop=plain phone=solid", "backdrop=waves phone=solid", "backdrop=dots phone=thin", "backdrop=rings phone=thin",
    "backdrop=blobs phone=solid", "backdrop=grid phone=solid",
]
TRANSITIONS = [
    "pop, flash, cut", "whip, zoom, dissolve", "dip, dissolve, cut", "glitch, flash, wipe", "wipe, whip, pop", "zoom, dip, hit", "dissolve, wipe, cut", "flash, dip, whip",
    "pop, glitch, zoom", "hit, wipe, dissolve", "hard cuts only, one dip at the reveal", "whip only (continuous motion)",
]
# name, mode, bpm range, prog, drums, bass, lead, arp, density, swing, pad
SONIC = [
    ("dusty lo fi", "dorian", (70, 82), "2,5,1,6", "brush", "long", "epiano", "skip", 1, 0.20, "on"),
    ("sunny afrobeat", "mixolydian", (100, 112), "1,4,5,4", "latin", "syncopated", "marimba", "updown", 2, 0.08, "on"),
    ("neon synthwave", "minor", (100, 112), "1,6,3,7", "fourfloor", "pulse", "saw", "up", 3, 0.0, "on"),
    ("minimal techno", "phrygian", (120, 128), "1,1,2,1", "fourfloor", "offbeat", "square", "skip", 1, 0.0, "off"),
    ("jazz brush", "dorian", (90, 100), "2,5,1,4", "brush", "walking", "epiano", "skip", 2, 0.25, "on"),
    ("acoustic folk pluck", "major", (92, 104), "1,5,6,4", "shuffle", "root", "pluck", "updown", 2, 0.10, "on"),
    ("ambient glass", "lydian", (60, 72), "1,2,6,5", "none", "long", "bell", "skip", 1, 0.0, "on"),
    ("trap hats", "minor", (130, 142), "1,6,4,5", "trap", "pulse", "bell", "up", 2, 0.0, "on"),
    ("dub reggae", "mixolydian", (74, 84), "1,4,1,5", "dub", "offbeat", "epiano", "up", 1, 0.12, "on"),
    ("bright pop", "major", (112, 124), "1,5,6,4", "fourfloor", "root", "pluck", "updown", 2, 0.0, "on"),
    ("playful march", "pentatonic", (108, 120), "1,4,5,1", "boomclap", "walking", "marimba", "skip", 2, 0.0, "off"),
    ("tense cinematic pulse", "harmonic", (88, 100), "1,6,4,5", "halftime", "pulse", "harp", "none", 0, 0.0, "on"),
    ("breakbeat energy", "dorian", (128, 140), "1,3,4,3", "breakbeat", "syncopated", "saw", "skip", 3, 0.05, "on"),
    ("tender music box", "major", (66, 78), "1,6,4,5", "none", "long", "bell", "updown", 1, 0.0, "on"),
]
VOICE = [
    "Imperative verbs: Tap. Hold. Done.", "Questions", "Numbers and facts: 3 taps. 0 code.", "One word per shot", "Lowercase whisper, no punctuation",
    "Full friendly sentences of five or six words", "Museum labels: the name of the thing and one fact", "First person: I tapped once. (a made up persona, never a real person)",
    "Sound words only: tap, swish, pop", "The app speaks: Hi, I am a timer.", "Time stamps: 0:03 first tap", "Pairs: a problem word then the answer word. Late? Logged.",
]
ENDING = [
    "Name and one line, held three seconds", "The first frame returns (the loop)", "A question to the viewer", "The icon grows on a boom", "A final tap triggers the end card",
    "A counter counts up to a TRUE number (features, screens, size)", "A blank screen, a cursor blinks, the name is typed", "The screen shrinks to an icon in the corner, then the card",
    "Silence, one sound, the name", "Three things in three words, then the name", "A slow zoom out that reveals the whole phone on a coloured ground", "A wipe to the channel colour: Made by H-sib and the app name",
]
AXES = [("CONCEPT", CONCEPT), ("HOOK", HOOK), ("RHYTHM", RHYTHM), ("PALETTE", PALETTE), ("TYPE", TYPE), ("BACKDROP AND FRAME", BACKDROP), ("TRANSITIONS", TRANSITIONS),
        ("SONIC WORLD", SONIC), ("CAPTION VOICE", VOICE), ("ENDING", ENDING)]


def rollable(table):
    return [i for i, option in enumerate(table) if not (isinstance(option, str) and option.startswith("~"))]


def workshop_entries():
    """The Workshop's seven videos as ledger lines (picks are table indices; a ~ option is the Workshop's own)."""
    def last(table):
        return len(table) - 1
    base = [last(CONCEPT), last(HOOK), last(RHYTHM), last(PALETTE), 0, 0, 0, 0, 0, 0]
    looks = [("teaser noir", {4: 11, 5: 7, 6: 10, 7: 11, 8: 11, 9: 8}), ("trailer bright", {4: 0, 5: 4, 6: 0, 7: 9, 8: 0, 9: 0}), ("tutorial pastel", {4: 4, 5: 6, 6: 2, 7: 0, 8: 3, 9: 0}),
             ("tutorial loud", {4: 2, 5: 3, 6: 1, 7: 1, 8: 0, 9: 0}), ("tutorial play", {4: 5, 5: 4, 6: 0, 7: 10, 8: 5, 9: 0}), ("tutorial code", {4: 3, 5: 5, 6: 3, 7: 3, 8: 5, 9: 0}),
             ("atelier gallery", {4: 1, 5: 8, 6: 2, 7: 6, 8: 6, 9: 0})]
    out = []
    for name, change in looks:
        picks = list(base)
        for axis, value in change.items():
            picks[axis] = value
        out.append(("app.hsib.workshop", "H-sib Workshop (%s)" % name, picks))
    return out


def read_ledger():
    if not LEDGER.exists():
        lines = ["H-SIB VIDEO DIRECTION LEDGER. One line per video direction already used or claimed, shared by every session. Written by tools\\video_direction.py in the Workshop project.",
                 "Never edit a line by hand except to correct a note. Format: ENTRY | package | app | status | date | picks (an index per axis, in the order of the axes) | note", ""]
        for package, app, picks in workshop_entries():
            lines.append("ENTRY | %s | %s | DONE | 2026-10-30 | %s | used by the first set of videos" % (package, app, ",".join(map(str, picks))))
        LEDGER.write_text("\n".join(lines) + "\n", encoding="utf-8")
    entries = []
    for line in LEDGER.read_text(encoding="utf-8").splitlines():
        if line.startswith("ENTRY |"):
            part = [x.strip() for x in line.split("|")]
            entries.append(dict(package=part[1], app=part[2], status=part[3], date=part[4], picks=[int(x) for x in part[5].split(",")], note=part[6] if len(part) > 6 else ""))
    return entries


def card_for(package, entries, again):
    digest = hashlib.sha256(package.encode()).digest()
    pools = [rollable(table) for _, table in AXES]
    used = [e["picks"] for e in entries if e["package"] != package]
    for attempt in range(again * 7, again * 7 + 4000):
        picks = [pools[a][(digest[a] + attempt * (2 * a + 3)) % len(pools[a])] for a in range(len(AXES))]
        if all(sum(1 for a in range(len(AXES)) if picks[a] != u[a]) >= MIN_DIFFERENT for u in used):
            return picks
    return picks


def render(package, app, picks):
    digest = hashlib.sha256(package.encode()).digest()
    world = SONIC[picks[7]]
    lo, hi = world[2]
    bpm = lo + digest[11] % (hi - lo + 1)
    key = ["C", "D", "Eb", "E", "F", "G", "Ab", "A", "Bb", "B", "F#", "Db"][digest[12] % 12]
    out = ["DIRECTION CARD for %s (%s). It differs from every direction in the ledger on at least %d of 10 axes." % (app, package, MIN_DIFFERENT), ""]
    for a, (name, table) in enumerate(AXES):
        option = table[picks[a]]
        out.append("%2d %-18s %s" % (a + 1, name, option if isinstance(option, str) else "%s (%s, %d to %d bpm)" % (option[0], option[1], option[2][0], option[2][1])))
    seconds = {"teaser": 40, "trailer": 90, "tutorial": 100}
    out += ["",
            "BUILD IT (nothing here needs a code change):",
            "  Look:   newlook|<name>|base=bright|bg=#<app dark or light ground>|ink=#<text>|accent=#<the app's accent>|dim=#<quiet colour>|%s|%s|caption=stack|tap=chime|tilt=0   then   look|<name>"
            % (TYPE[picks[4]].split(": ", 1)[1].split(" (")[0].replace(" upper=1", "|upper=1"), BACKDROP[picks[5]].replace(" ", "|")),
            "  Music:  python tools\\make_music.py store\\videos\\music --gen <name> key=%s mode=%s bpm=%d seconds=%d prog=%s drums=%s bass=%s lead=%s arp=%s density=%d swing=%s pad=%s seed=%d"
            % (key, world[1], bpm, seconds["trailer"], world[3], world[4], world[5], world[6], world[7], world[8], world[9], world[10], digest[13]),
            "          the key and the bpm are starting points: change them to the app's mood (calm lower, urgent higher). Use music|<name>.wav|0.9|%d in the spec." % bpm,
            "  Transitions allowed in the whole set of this app: %s (use nothing else)." % TRANSITIONS[picks[6]],
            "THE DIFFERENCE CONTRACT (a session that breaks it has to redo the storyboard):",
            "  1. The storyboard is built from CONCEPT, HOOK, RHYTHM, VOICE and ENDING above. Existing specs (the Workshop's) are examples of SYNTAX only: copy no scene order, no caption, no beat plan, no title card from them.",
            "  2. Every video of this app has its own look, track and rhythm, but all stay inside this card's palette logic, type and transitions so the app has one recognisable personality.",
            "  3. Write ONE sentence first: the premise of the campaign, drawn from what is true and special in THIS app (not from the card). If the card fights the app's truth, change that one option, say which and why.",
            "  4. The first 3 and the last 3 seconds must not resemble the Workshop's (a slow title card of words arriving, a flash cut, a gold or flat colour end card) unless the card says so.",
            "  5. The channel keeps only these constants across apps: the safety and honesty rules, the footer line 'Made by H-sib. Apps, tools and ideas.', a small H-sib mark on the closing card of long videos, the posting rhythm. Everything else varies.",
            "  6. After the work: python tools\\video_direction.py done %s \"<one line: what was made>\"" % package]
    return "\n".join(out)


def main():
    args = sys.argv[1:]
    if not args:
        sys.exit(__doc__)
    command = args[0]
    entries = read_ledger()
    if command == "list":
        for e in entries:
            print("%-10s %-24s %-40s %s  %s" % (e["status"], e["package"][-24:], e["app"][:40], e["date"], e["note"][:40]))
        print("%d directions in %s" % (len(entries), LEDGER))
    elif command in ("roll", "show"):
        package = args[1]
        app = args[2] if len(args) > 2 else package
        mine = [e for e in entries if e["package"] == package]
        if command == "show" or (mine and "again" not in args):
            if not mine:
                sys.exit("no direction for " + package)
            print(render(package, mine[-1]["app"], mine[-1]["picks"]))
            return
        again = 1 + len(mine) if "again" in args else 0
        picks = card_for(package, entries, again)
        with LEDGER.open("a", encoding="utf-8") as ledger:
            ledger.write("ENTRY | %s | %s | CLAIMED | %s | %s | %s\n" % (package, app, datetime.date.today().isoformat(), ",".join(map(str, picks)), "rolled" + (" again" if again else "")))
        print(render(package, app, picks))
    elif command == "done":
        package, note = args[1], args[2] if len(args) > 2 else "done"
        text = LEDGER.read_text(encoding="utf-8").splitlines()
        for i in range(len(text) - 1, -1, -1):
            if text[i].startswith("ENTRY | " + package + " |"):
                part = [x.strip() for x in text[i].split("|")]
                part[3], part[6] = "DONE", note
                text[i] = " | ".join(part)
                break
        LEDGER.write_text("\n".join(text) + "\n", encoding="utf-8")
        print("marked done:", package)
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
