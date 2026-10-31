"""A contact sheet of a video: python tools/video_sheet.py <video> <out.png> [start] [end] [step seconds] [columns] [tile width]
Each tile shows the picture that was on screen at that time (the latest frame stored at or before it) and is labelled with the real time in seconds.
The raw phone recordings only store a frame when the screen changes, so they are read from the start, frame by frame, and the times are the real ones
(the same times that tools/make_video.py and the touch logs *.taps.json use). Also prints the touch log of the recording next to the sheet."""
import json
import pathlib
import sys

import cv2
import numpy as np

path, out = sys.argv[1], sys.argv[2]
start = float(sys.argv[3]) if len(sys.argv) > 3 else 0.0
end = float(sys.argv[4]) if len(sys.argv) > 4 and float(sys.argv[4]) > 0 else 1e9
step = float(sys.argv[5]) if len(sys.argv) > 5 else 1.0
cols = int(sys.argv[6]) if len(sys.argv) > 6 else 10
tw = int(sys.argv[7]) if len(sys.argv) > 7 else 0

cap = cv2.VideoCapture(path)
frames = []                                    # (pts seconds, small picture)
while True:
    ok, frame = cap.read()
    if not ok:
        break
    t = cap.get(cv2.CAP_PROP_POS_MSEC) / 1000.0
    h, w = frame.shape[:2]
    width = tw or (150 if h > w else 300)
    frames.append((t, cv2.resize(frame, (width, int(h * width / w)), interpolation=cv2.INTER_AREA)))
if not frames:
    sys.exit("no frames")
last = frames[-1][0]
end = min(end, last)
tiles, at, i = [], start, 0
while at <= end:
    while i + 1 < len(frames) and frames[i + 1][0] <= at:
        i += 1
    tile = frames[i][1].copy()
    cv2.putText(tile, "%.1f" % at, (4, 18), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (0, 255, 255), 2, cv2.LINE_AA)
    tiles.append(tile)
    at += step
while len(tiles) % cols:
    tiles.append(np.zeros_like(tiles[0]))
rows = [np.hstack(tiles[r * cols:(r + 1) * cols]) for r in range(len(tiles) // cols)]
cv2.imwrite(out, np.vstack(rows))
print("clip: %d frames, last frame at %.1f s, %d tiles, step %.2f s -> %s" % (len(frames), last, len(tiles), step, out))
side = pathlib.Path(path).with_name(pathlib.Path(path).stem + ".taps.json")
if side.exists():
    log = json.loads(side.read_text())
    print("touches (s): " + " ".join("%.1f%s" % (t["t"] - 0.35, {"tap": "", "swipe": "~", "key": "k", "mark": "m"}.get(t["kind"], "")) for t in log if start <= t["t"] <= end + 1))
