"""Prints the words on the phone screen and saves a small screenshot: python -I tools/phone_look.py [name]  -> store/videos/raw/shots/name_small.png (360 px wide)."""
import pathlib, subprocess, sys, io
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import phone_drive as p
args = [a for a in sys.argv[1:] if not a.startswith("-")]
name = args[0] if args else "look"
for label, x, y, y1, y2 in p.nodes():
    print("%5d %5d %s" % (y, x, label.replace("\n", " / ")[:100]))
if "-n" in sys.argv:
    sys.exit()
png = p.adb("exec-out", "screencap", "-p", binary=True)
from PIL import Image
im = Image.open(io.BytesIO(png)).convert("RGB")
im = im.resize((360, 840))
out = p.HERE / (name + "_small.png")
im.save(out)
print(out)
