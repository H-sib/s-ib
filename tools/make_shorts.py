"""Makes the YouTube Shorts (vertical 1080 by 1920) from the raw phone recordings that the long videos use.
Usage: python tools\\make_shorts.py specs            writes store\\videos\\specs\\shorts\\<id>_<name>.txt for every Short and store\\videos\\SHORTS.txt (titles, descriptions, tags, post dates)
       python tools\\make_shorts.py render [id ...]  renders the Shorts that are missing or older than their spec into store\\videos\\shorts (all of them, or only the ids named)
       python tools\\make_shorts.py sheets            writes a contact sheet of every rendered Short into store\\videos\\raw\\shots (six frames per Short)
Each Short keeps the look and the music of the video it comes from (so the channel has series: pastel, loud, play, code, noir, bright, gallery), starts with its hook
as the caption, shows one idea, and ends on a three second card. The cuts fall on the beat: the lengths below are seconds and are rounded to whole beats of the music.
The times are seconds in the raw recordings (see tools\\video_sheet.py, which prints real times and the touch times). make_video.py (format|short) does the drawing."""
import datetime
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SPECS = ROOT / "store" / "videos" / "specs" / "shorts"
OUT = ROOT / "store" / "videos" / "shorts"
PY = sys.executable

# look: (music file, bpm, volume, transition between shots, end card line)
FAMILY = {
    "bright": ("trailer.wav", 128, 0.9, "pop"),
    "code": ("code.wav", 100, 0.9, "glitch"),
    "pastel": ("pastel.wav", 72, 0.9, "dissolve"),
    "loud": ("street.wav", 118, 0.9, "whip"),
    "play": ("play.wav", 112, 0.9, "pop"),
    "noir": ("teaser.wav", 76, 0.95, "flash"),
    "gallery": ("gallery.wav", 92, 0.95, "dissolve"),
}

# id, look, file name, title, one line for the description, [shots], end card text
# a shot is ("clip", file, start, end, caption, seconds, options) or ("still", file, caption, seconds, options) or ("title", text, small line, seconds)
SHORTS = [
    ("N1", "noir", "every_app_starts", "Every app starts as nothing", "A page, a spark, colour, shape, and then a real app with your name on it.", [
        ("title", "Every app starts as *nothing*.", "", 4),
        ("clip", "s_designer.mp4", 98, 109, "A *spark*.", 4, ""),
        ("clip", "s_designer.mp4", 16, 41, "*Colour*.", 4, ""),
        ("clip", "s_make.mp4", 4.5, 22, "Make it *real*.", 6, ""),
    ], "Made in *H-sib Workshop*"),
    ("B1", "bright", "no_code", "Make an Android app with no code", "Pick a design, tap, change anything. No code needed.", [
        ("clip", "s_picker.mp4", 14, 40, "No *code* needed.", 7, ""),
        ("clip", "s_designer.mp4", 11.5, 37, "Tap a *look*.", 7, ""),
    ], "Made in *H-sib Workshop*"),
    ("B2", "bright", "glow_title", "One tap and your title glows", "Looks for every element: neon text, big title, highlighter, sunset. Then drag it anywhere.", [
        ("clip", "s_designer.mp4", 11.5, 37, "One tap. *Glow*.", 9, ""),
        ("clip", "s_designer.mp4", 43, 60, "Drag it *anywhere*.", 8, ""),
    ], "Made in *H-sib Workshop*"),
    ("B3", "bright", "whole_page", "Change the whole look of your app in one tap", "Midnight, Paper, Deep space: a page look changes everything at once. Then press Play.", [
        ("clip", "s_designer.mp4", 64, 92, "Change the *whole page*.", 9, ""),
        ("clip", "s_designer.mp4", 92, 109, "Then press *Play*.", 7, ""),
    ], "Made in *H-sib Workshop*"),
    ("B4", "bright", "real_signed_app", "A real, signed Android app from your phone", "Make it: the Workshop packs the app, signs it with a key of its own and shows you the details.", [
        ("clip", "s_make.mp4", 4.5, 22, "Make a *real app*.", 14, ""),
    ], "Made in *H-sib Workshop*"),
    ("B5", "bright", "credit_line", "Your name stays in the credit line", "Every app you make carries your name in its credit line, and nobody can take it out.", [
        ("clip", "s_credit.mp4", 4.5, 12.8, "Your name, in the *credit line*.", 7, ""),
        ("clip", "s_make.mp4", 9, 16, "Nobody can take it *out*.", 6, ""),
    ], "Made in *H-sib Workshop*"),
    ("B6", "bright", "rename_category", "Rename a category and your old expenses follow", "Inside an app you made: the categories are yours to edit, and the records follow the new name.", [
        ("clip", "s_categories.mp4", 7, 26, "Rename a *category*.", 8, ""),
        ("clip", "s_categories.mp4", 26, 45, "Old expenses *follow*.", 8, ""),
    ], "Made in *H-sib Workshop*"),
    ("C1", "code", "lessons", "Learn to code on your phone, step by step", "New in version 1.6: lessons with code you can copy and a live picture under it.", [
        ("clip", "s_lessons2.mp4", 4, 38, "Learn it *step by step*.", 12, ""),
    ], "Made in *H-sib Workshop*"),
    ("C2", "code", "bouncing_balls", "Bouncing balls and a golden spiral, in a few lines", "The code editor in H-sib Workshop shows the picture live while you read the code.", [
        ("clip", "s_code.mp4", 18, 31, "Bouncing *balls*.", 8, "focus=900"),
        ("clip", "s_code.mp4", 31, 42, "A golden *spiral*.", 8, "focus=900"),
    ], "Made in *H-sib Workshop*"),
    ("C3", "code", "calculator", "A calculator made of code", "New in version 1.6: a ready made calculator whose work is done by code you can read and change.", [
        ("clip", "s_calc.mp4", 4, 47, "A calculator made of *code*.", 14, "focus=1000"),
    ], "Made in *H-sib Workshop*"),
    ("C4", "code", "waiting_line", "A waiting line app made of code", "New in version 1.6: people join a line and a button that runs code calls the next one.", [
        ("clip", "s_queue.mp4", 16, 52, "A waiting line made of *code*.", 14, "focus=1000"),
    ], "Made in *H-sib Workshop*"),
    ("C5", "code", "type_code", "Typing code on a phone", "Yes, you can write code on the phone. The editor keeps your indent and shows the picture live.", [
        ("clip", "s_streak_b2.mp4", 20, 58, "Type *code* on a phone.", 14, ""),
    ], "Made in *H-sib Workshop*"),
    ("C6", "code", "change_number", "Change a number and see it move", "Change one number in the code and the picture answers at once.", [
        ("clip", "s_code.mp4", 48, 66, "Change a number. See it *move*.", 12, "focus=900"),
    ], "Made in *H-sib Workshop*"),
    ("P1", "pastel", "fill_a_week", "Fill a week of study streak dots", "A study streak counter made from a blank page. Press the button and the dots fill in.", [
        ("clip", "s_streak_d.mp4", 17, 62, "Fill a *week*.", 20, "focus=1400"),
    ], "Full tutorial on the *H-sib* channel"),
    ("P2", "pastel", "pastel_backdrop", "A soft pastel backdrop for your app", "Pick a colour for the page and the whole app changes mood.", [
        ("clip", "s_streak_a.mp4", 57, 83, "A *pastel* backdrop.", 14, ""),
    ], "Full tutorial on the *H-sib* channel"),
    ("P3", "pastel", "blank_page", "Start an app from a blank page", "A blank page, a name, a look. That is how a study streak app starts.", [
        ("clip", "s_streak_a.mp4", 0.3, 27, "From a *blank* page.", 16, ""),
    ], "Full tutorial on the *H-sib* channel"),
    ("P4", "pastel", "button_counts", "A button that counts your study days", "A drawing, a few lines of code and a button that runs them.", [
        ("clip", "s_streak_c.mp4", 3, 58, "A button that *counts*.", 16, "focus=1300>2000"),
    ], "Full tutorial on the *H-sib* channel"),
    ("L1", "loud", "name_dishes", "Name every dish on your menu board", "A street food menu app: start from the Playful design and say what is on the menu.", [
        ("clip", "s_menu_b.mp4", 4, 50, "Name each *dish*.", 14, ""),
    ], "Full tutorial on the *H-sib* channel"),
    ("L2", "loud", "tap_a_dish", "Tap a dish and a message pops up", "Each dish on the menu board has its own message when you tap it.", [
        ("clip", "s_menu_c.mp4", 21, 37, "Tap a *dish*.", 12, ""),
    ], "Full tutorial on the *H-sib* channel"),
    ("L3", "loud", "slide_in", "Make your menu slide in", "One entrance animation per element, picked with a tap.", [
        ("clip", "s_menu_b.mp4", 91, 160, "Make it *slide in*.", 12, ""),
    ], "Full tutorial on the *H-sib* channel"),
    ("L4", "loud", "start_playful", "Start a menu app from the Playful design", "Pick a ready made design, then change every part of it.", [
        ("clip", "s_menu_a.mp4", 20, 33, "Pick *Playful*.", 12, ""),
    ], "Full tutorial on the *H-sib* channel"),
    ("Q1", "play", "write_questions", "Write your own quiz questions", "A family trivia night: the questions are about your own family.", [
        ("clip", "s_trivia_a2.mp4", 3, 32, "Write *your own* questions.", 14, ""),
    ], "Full tutorial on the *H-sib* channel"),
    ("Q2", "play", "right_answer", "Tick the right answer", "Every question has answers and one right one. You tick it.", [
        ("clip", "s_trivia_a2.mp4", 112, 143, "Tick the *right* answer.", 12, ""),
    ], "Full tutorial on the *H-sib* channel"),
    ("Q3", "play", "three_rounds", "Three rounds of family trivia. Who wins?", "Play the quiz you just made and watch the scores build up.", [
        ("clip", "s_trivia_b.mp4", 12, 39, "Everyone *answers*.", 9, ""),
        ("clip", "s_trivia_b.mp4", 100, 112, "Who wins *Friday*?", 7, ""),
    ], "Full tutorial on the *H-sib* channel"),
    ("Q4", "play", "start_a_quiz", "Start a quiz app on your phone", "Pick Quiz, name it, pick the mood. Your own questions come next.", [
        ("clip", "s_trivia_a1.mp4", 0.5, 24, "Start a *quiz*.", 12, ""),
    ], "Full tutorial on the *H-sib* channel"),
    ("G1", "gallery", "atelier_gallery", "A gallery for the apps you make", "The Atelier is a place in H-sib Workshop to show your apps: browse by newest, most remixed or most viewed.", [
        ("clip", "s_atelier_gate.mp4", 5.6, 13.5, "Where makers *shine*.", 6, "focus=1000"),
        ("still", "atelier_apps.png", "A gallery of *apps*.", 6, "focus=1100"),
    ], "The *Atelier*, in H-sib Workshop"),
    ("G2", "gallery", "atelier_rankings", "Rankings for app makers", "The Atelier ranks makers by remixes, apps and views. Every maker also gets a studio page.", [
        ("still", "atelier_rank.png", "Ranked by *remixes*.", 7, "focus=900"),
        ("still", "at_a9.png", "Your own *studio*.", 5, "layout=tall"),
    ], "The *Atelier*, in H-sib Workshop"),
    ("G3", "gallery", "atelier_remix", "You decide who may remix your app", "When you publish to the Atelier you choose if others may open a copy and change it. You are credited.", [
        ("still", "at_ah5.png", "You decide who may *remix*.", 7, "layout=tall"),
        ("still", "at_r1.png", "The maker gets *credit*.", 6, "layout=tall"),
    ], "The *Atelier*, in H-sib Workshop"),
    ("G4", "gallery", "atelier_safe", "Report anything unsafe, hide any maker", "The Atelier has a report button on every app and a way to hide a maker.", [
        ("still", "at_r2.png", "Report anything *unsafe*.", 8, "layout=tall"),
    ], "The *Atelier*, in H-sib Workshop"),
]

# the order they are posted in, from Sunday 1 November 2026, five a week (Sun Mon Wed Thu Fri) at 7 pm, after the long videos they belong to
ORDER = ["N1", "B1", "B2", "B4", "B3", "G1", "G2", "G3", "G4", "L1", "L2", "L3", "L4", "Q1", "Q2", "Q3", "Q4", "P1", "P2", "P3", "P4", "C1", "C2", "C3", "C4", "C5", "C6", "B5", "B6"]
FIRST = datetime.date(2026, 11, 1)
WEEK = (0, 1, 3, 4, 5)      # days after the Sunday: Sun, Mon, Wed, Thu, Fri
TIMES = ("8:00 pm", "12:30 pm", "12:30 pm", "8:00 pm", "12:30 pm")      # two slots on purpose, so the analytics can show which one the viewers prefer


def post_dates():
    dates = []
    for i, _ in enumerate(ORDER):
        dates.append(FIRST + datetime.timedelta(days=7 * (i // 5) + WEEK[i % 5]))
    return dates


# the long videos: file, date, time, title, description, tags, playlist, the first (pinned) comment
LONG = [
    ("teaser.mp4", datetime.date(2026, 10, 31), "7:00 pm", "H-sib Workshop: every app starts as nothing (teaser)",
     "Design your own app. Make it real.\nA page, a spark, colour, shape, movement, code, and then a real app with your name on it, made on the phone. H-sib Workshop is an Android app maker with no code needed.",
     "app maker, no code, android, design your own app, app builder, H-sib Workshop", "H-sib Workshop: trailers",
     "Which app would you make first? Tell me and I might build it on camera."),
    ("trailer.mp4", datetime.date(2026, 11, 3), "7:00 pm", "H-sib Workshop: make your own app on your phone | Official trailer",
     "What you get in H-sib Workshop, one thing at a time:\nNo code needed for normal apps. A real signed Android app made on your phone. Your data stays on your phone. A design studio with one tap looks, dragging and whole page looks.\n"
     "Categories you can edit inside the app you make. Your name in a credit line nobody can remove. The Atelier, with rankings and remixes by makers (the makers and apps shown in the Atelier are made up demo data).\n"
     "New in version 1.6: Draw and code, a Drawing element with a code editor and a live picture, step by step lessons, and two ready made apps written in code, a Calculator and a Waiting line.\n"
     "H-sib Workshop. Design your own app. Make it real.",
     "app maker, no code app builder, android app, design app on phone, make an app, H-sib Workshop", "H-sib Workshop: trailers",
     "Which of these would you try first? The Atelier, the code editor or a real signed app?"),
    ("atelier.mp4", datetime.date(2026, 11, 7), "7:00 pm", "The Atelier: a showcase for the apps you make (H-sib Workshop)",
     "The Atelier is the place inside H-sib Workshop to show what you make. Claim a handle, publish an app, choose whether others may remix it, and be credited when they do.\n"
     "Browse apps by newest, most remixed or most viewed, see an app run live before you use it, rate it and leave a word, and see the rankings of makers. Report anything unsafe, or hide a maker.\n"
     "Think of it as a portfolio page for your apps. You sign in with Google to join.\n"
     "What you see here comes from test accounts and from a demo build with made up makers. There is no real community in this video yet.",
     "app showcase, app portfolio, share your apps, remix apps, app makers, android, no code, H-sib Workshop", "H-sib Workshop: trailers",
     "Would you let others remix your app, or keep it look only? Tell me why."),
    ("tutorial_menu.mp4", datetime.date(2026, 11, 10), "7:00 pm", "Make a street food menu board app in minutes (H-sib Workshop)",
     "Start from the Playful design, say what is on the menu, give each dish a message, put the offer in, let things slide in, then press Play and tap a dish.\nThe waiting between taps is sped up. The app runs in real time.",
     "menu app, food app, make an app, no code, android, small business app, H-sib Workshop", "H-sib Workshop: tutorials",
     "What would be on YOUR menu? Write your best dish below."),
    ("tutorial_trivia.mp4", datetime.date(2026, 11, 14), "7:00 pm", "Make your own family trivia night quiz app (H-sib Workshop)",
     "Start a Quiz, write your own questions about your own family, tick the right answers, then play three rounds and watch the scores build up.\nThe waiting between taps is sped up. The app runs in real time.",
     "quiz app, trivia night, family game, make an app, no code, android, H-sib Workshop", "H-sib Workshop: tutorials",
     "Write one question about your family below and I will pick my favourite."),
    ("tutorial_streak.mp4", datetime.date(2026, 11, 17), "7:00 pm", "Make a study streak counter on your phone, from a blank page (H-sib Workshop)",
     "A blank page, a soft backdrop colour, a title, a drawing whose few lines of code you type, and a button that adds a day. Press Play and fill the week.\n"
     "The code is optional. Normal apps in H-sib Workshop need none. The waiting between taps is sped up. The app runs in real time.",
     "study streak, habit tracker, make an app, no code, android, code for beginners, H-sib Workshop", "H-sib Workshop: tutorials",
     "How many days is your longest streak? Tell me below."),
    ("tutorial_code.mp4", datetime.date(2026, 11, 21), "7:00 pm", "Write a few lines of code and watch it move (H-sib Workshop 1.6)",
     "New in version 1.6: the Drawing element. Write a few lines, see the picture live, make a button answer your code, learn it step by step, or open the ready made Calculator written in code.",
     "learn to code, code for beginners, creative coding, android, make an app, H-sib Workshop", "H-sib Workshop: tutorials",
     "What should I draw with code next? A clock, a rocket, a game?"),
]

PARENT = {"bright": "trailer.mp4", "code": "tutorial_code.mp4", "pastel": "tutorial_streak.mp4", "loud": "tutorial_menu.mp4", "play": "tutorial_trivia.mp4", "gallery": "atelier.mp4", "noir": "teaser.mp4"}
SHORT_PLAYLIST = {"bright": "H-sib Workshop Shorts: what you can do", "code": "H-sib Workshop Shorts: code", "pastel": "H-sib Workshop Shorts: study streak app",
                  "loud": "H-sib Workshop Shorts: menu app", "play": "H-sib Workshop Shorts: trivia app", "gallery": "H-sib Workshop Shorts: The Atelier", "noir": "H-sib Workshop Shorts: what you can do"}
SHORT_TAGS = {"bright": "design your own app, app design", "code": "learn to code, code for beginners, creative coding", "pastel": "study streak, habit tracker", "loud": "menu app, food business app",
              "play": "quiz app, trivia night, family game", "gallery": "app showcase, remix apps, app portfolio", "noir": "teaser, design your own app"}
BASE_TAGS = "app maker, no code, android, make an app, H-sib Workshop, shorts"


def indent(text):
    return "\n".join("    " + line if line else "" for line in text.split("\n"))


def upload_sheet():
    by_id = {item[0]: item for item in SHORTS}
    files, seen = {}, {}
    for item in SHORTS:
        index = seen.get(item[1], 0)
        seen[item[1]] = index + 1
        files[item[0]] = spec_text(item, index)[1]
    rows = []                                                         # (date, hour for sorting, text)
    for file, date, time, title, desc, tags, playlist, comment in LONG:
        special = "turn monetisation OFF (this is the video for the Play Store listing; upload it as Unlisted earlier and switch it to Public on this date)" if file == "trailer.mp4" else "none"
        rows.append((date, 19, "LONG VIDEO store\\videos\\%s\n  Date and time: %s, %s\n  Title: %s\n  Description:\n%s\n  Tags: %s\n  Playlist: %s\n  Pinned comment: %s\n  Thumbnail: your own picture, 1280 by 720 (YouTube's pick is the fallback)\n  Special: %s" % (
            file, date.strftime("%a %d %b %Y"), time, title, indent(desc + "\n\nMade by H-sib. Apps, tools and ideas. Subscribe for the next one."), tags, playlist, comment, special)))
    for date, time, sid in zip(post_dates(), (TIMES * 6)[:len(ORDER)], ORDER):
        item = by_id[sid]
        extra = ""
        if item[1] == "gallery":
            extra = "\nShown with test accounts and demo data."
        elif sid in ("C1", "C2", "C5", "C6", "P4"):
            extra = "\nThe code is optional. Normal apps in H-sib Workshop need none."
        desc = "%s\nMade in H-sib Workshop, an Android app maker with no code needed.%s\nThe full video is on the channel: see the pinned comment.\n#Shorts #hsib #nocode #android #makeanapp" % (item[4], extra)
        rows.append((date, 12 if "12:30" in time else 20, "SHORT store\\videos\\shorts\\%s.mp4\n  Date and time: %s, %s\n  Title: %s\n  Description:\n%s\n  Tags: %s, %s\n  Playlist: %s\n  Pinned comment: Full video: <paste the YouTube link of %s here once it is public>\n  Thumbnail: automatic (Shorts need none)" % (
            files[sid], date.strftime("%a %d %b %Y"), time, item[3], indent(desc), BASE_TAGS, SHORT_TAGS[item[1]], SHORT_PLAYLIST[item[1]], PARENT[item[1]])))
    rows.sort(key=lambda r: (r[0], r[1]))
    head = [
        "UPLOAD SHEET FOR YOUTUBE (H-sib), %d videos in the order they are posted: 7 long videos and %d Shorts. Times are in YOUR time zone (YouTube Studio asks for it when you schedule)." % (len(rows), len(SHORTS)),
        "Written 2026-10-29 by the assistant. Nothing was uploaded by it. Nobody has listened to the music yet: watch every video once with sound before you upload it.",
        "",
        "HOW TO SCHEDULE: YouTube Studio, Create, Upload videos, pick the file, fill the details below, then Visibility, choose Schedule, set the date and the time, and press Schedule.",
        "The Shorts: a vertical video of 60 seconds or less is treated as a Short by itself. The first three hashtags of a description show above the title.",
        "",
        "FIELDS THAT ARE THE SAME ON EVERY UPLOAD",
        "  Audience: No, it is not made for kids.   Age restriction: none.   Paid promotion: No.   Altered content (AI made, looks real): No.",
        "  Category: Science and Technology.   Language: English.   Licence: Standard YouTube licence.   Allow embedding: yes.   Publish to subscriptions feed: yes.",
        "  Comments: on, and hold potentially inappropriate comments for review.   Show how many people like the video: yes.   Subtitles: none (the words are on the screen).",
        "  Recording date and location: leave empty.   Game title: leave empty.   Monetisation: leave as it is (the channel is not monetised yet), except the trailer: OFF.",
        "  Playlists: make them once (Studio, Content, Playlists): H-sib Workshop: trailers, H-sib Workshop: tutorials, and one per series of Shorts (named in the sheet).",
        "  Pinned comment: after the video is public, write it as your own comment, then use the three dots, Pin. Shorts: paste the link of the long video it comes from.",
        "  Related video (Shorts only, if Studio shows the field): the long video it comes from, once that is public.",
        "  Thumbnail: long videos only. The Shorts need none.   End screens and cards: skip for now (the videos end on their own closing card).",
        "  Put NO Google Play link in any description until the app is public.",
        "", "-" * 100, ""]
    return "\n".join(head) + "\n\n".join(r[2] for r in rows) + "\n"


def beats(seconds, bpm):
    return max(2, round(seconds * bpm / 60.0))


def spec_text(item, index_in_family):
    sid, look, name, title, line, shots, end = item
    music, bpm, volume, trans = FAMILY[look]
    start = round((index_in_family % 4) * 4 * 4 * 60.0 / bpm, 2)          # four bars further into the music for each Short of the same look
    out = ["# SHORT %s: %s. Look %s, music %s at %d bpm (starts %.1f s in). Rebuild: python tools\\make_shorts.py render %s" % (sid, title, look, music, bpm, start, sid),
           "format|short", "look|" + look, "music|%s|%.2f|%d|%.2f" % (music, volume, bpm, start)]
    for number, shot in enumerate(shots):
        arrive = "" if number == 0 else "|trans=" + trans
        if shot[0] == "title":
            out.append("title|%s|%s|%db%s" % (shot[1], shot[2], beats(shot[3], bpm), arrive))
        elif shot[0] == "clip":
            _, file, a, b, caption, seconds, opts = shot
            out.append("clip|%s|%s|%s|%s|dur=%db%s%s" % (file, a, b, caption, beats(seconds, bpm), arrive, "|" + opts if opts else ""))
        else:
            _, file, caption, seconds, opts = shot
            out.append("still|%s|%s|%db%s%s" % (file, caption, beats(seconds, bpm), arrive, "|" + opts if opts else ""))
    out.append("title|%s||%db|trans=%s" % (end, beats(3, bpm), trans if trans != "glitch" else "glitch"))
    return "\n".join(out) + "\n", "%s_%s" % (sid, name)


def write_specs():
    SPECS.mkdir(parents=True, exist_ok=True)
    seen, files = {}, {}
    for item in SHORTS:
        index = seen.get(item[1], 0)
        seen[item[1]] = index + 1
        text, stem = spec_text(item, index)
        target = SPECS / (stem + ".txt")
        if not target.exists() or target.read_text(encoding="utf-8") != text:          # an unchanged spec keeps its time, so render does not redo its Short
            target.write_text(text, encoding="utf-8")
        files[item[0]] = stem
    print("wrote %d specs" % len(SHORTS))


def render(names):
    OUT.mkdir(parents=True, exist_ok=True)
    for spec in sorted(SPECS.glob("*.txt")):
        if names and spec.stem.split("_")[0] not in names:
            continue
        target = OUT / (spec.stem + ".mp4")
        if target.exists() and target.stat().st_mtime > spec.stat().st_mtime and not names:
            print("up to date:", target.name)
            continue
        print("rendering", target.name, flush=True)
        subprocess.run([PY, "-u", str(ROOT / "tools" / "make_video.py"), str(spec), str(ROOT / "store" / "videos" / "raw"), str(target)], check=False)


def sheets():
    import cv2
    import numpy as np
    folder = ROOT / "store" / "videos" / "raw" / "shots"
    rows, done = [], []
    for video in sorted(OUT.glob("*.mp4")):
        cap = cv2.VideoCapture(str(video))
        total = cap.get(cv2.CAP_PROP_FRAME_COUNT) / (cap.get(cv2.CAP_PROP_FPS) or 30)
        tiles = []
        for k in range(6):
            cap.set(cv2.CAP_PROP_POS_MSEC, (0.4 + (total - 0.9) * k / 5) * 1000)
            ok, frame = cap.read()
            tiles.append(cv2.resize(frame, (162, 288)) if ok else np.zeros((288, 162, 3), np.uint8))
        cv2.putText(tiles[0], video.stem[:14], (4, 16), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 255, 255), 1, cv2.LINE_AA)
        rows.append(np.hstack(tiles))
        done.append(video.stem)
    for i in range(0, len(rows), 6):
        cv2.imwrite(str(folder / ("shorts_sheet_%d.png" % (i // 6))), np.vstack(rows[i:i + 6]))
    print("sheets for", len(done), "Shorts in", folder)


if __name__ == "__main__":
    command = sys.argv[1] if len(sys.argv) > 1 else "specs"
    if command == "specs":
        write_specs()
    elif command == "upload":
        (ROOT / "store" / "videos" / "UPLOAD_SHEET.txt").write_text(upload_sheet(), encoding="utf-8")
        print("wrote store\\videos\\UPLOAD_SHEET.txt")
    elif command == "render":
        render(sys.argv[2:])
    elif command == "sheets":
        sheets()
    else:
        sys.exit(__doc__)
