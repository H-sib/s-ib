"""Deletes the test apps that the filming made on the phone (drafts and made records called below); never touches any other app.
Usage: python tools/video_cleanup.py   (the Workshop must be in front and the phone unlocked)"""
import pathlib
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import phone_drive as drive

MINE = ["Family Trivia", "Bite Street", "Line Up", "Pocket Calc", "Study Streak", "Neon Night", "Spend Smart"]


def names_on_list():
    return [n[0] for n in drive.nodes()]


for name in MINE:
    drive.run("tohub")
    drive.run("tapx:My apps")
    time.sleep(1.5)
    if name not in names_on_list():
        print("not there:", name)
        continue
    drive.run("tapx:" + name)
    time.sleep(3)
    found = names_on_list()
    if "App settings" in found:                      # a draft: the editor opened
        drive.run("tapx:App settings")
        time.sleep(1.2)
        drive.run("tap:Delete draft")
    elif "Delete record" in found:                   # a made app: its details page opened
        drive.run("tapx:Delete record")
    else:
        print("could not find a delete button for", name)
        continue
    time.sleep(1.2)
    drive.run("tapx:Delete record")
    time.sleep(1.5)
    print("deleted", name)
drive.run("tohub")
