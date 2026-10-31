"""Looks through the raw recordings for a grey card over the top of the screen (a notification banner, the same test that make_video.py uses to hide them)
and prints the times where one shows. Usage: python tools/video_banner_scan.py <raw folder> <file> [<file> ...]"""
import pathlib
import sys

import cv2
import numpy as np

folder = pathlib.Path(sys.argv[1])
for name in sys.argv[2:]:
    cap = cv2.VideoCapture(str(folder / name))
    hits, n = [], 0
    while True:
        ok, frame = cap.read()
        if not ok:
            break
        n += 1
        t = cap.get(cv2.CAP_PROP_POS_MSEC) / 1000.0
        region = frame[110:340:3, 40:1040:3].astype(np.int16)
        b, g, r = region[:, :, 0], region[:, :, 1], region[:, :, 2]
        if float(((np.abs(r - g) < 7) & (np.abs(g - b) < 7) & (r > 24) & (r < 75)).mean()) > 0.04:
            hits.append(round(t, 1))
    print("%s: %d frames, grey card at the top at: %s" % (name, n, hits[:12] if hits else "never"))
