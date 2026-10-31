"""ONE picture that proves a finished video matches its spec: one tile per scene (taken from the middle of the scene), labelled with the scene number, the caption and where the footage came from.
Looking at this single sheet answers the question that cost hours on the first set (does the picture match its caption?) with one image instead of dozens.
Usage: python tools\\video_review.py <spec.txt> <video.mp4> <out.png>      (a Short has 8 tiles in a row, a long video 6)
The finished video is constant frame rate, so seeking it by time is exact (only the raw phone recordings are not: never seek those)."""
import pathlib
import sys

import cv2
import numpy as np

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import make_video as mv          # noqa: E402


def main():
    spec, video, out = pathlib.Path(sys.argv[1]), sys.argv[2], sys.argv[3]
    scenes, header = mv.parse(spec)
    bpm = header["bpm"]
    counts = [mv.frames_of(s, bpm) for s in scenes]
    starts = np.cumsum([0] + counts[:-1]) / mv.FPS
    cap = cv2.VideoCapture(video)
    total = cap.get(cv2.CAP_PROP_FRAME_COUNT) / (cap.get(cv2.CAP_PROP_FPS) or 30)
    short = header["format"] == "short"
    width, cols = (150, 8) if short else (300, 5)
    tiles = []
    for number, (scene, count, t0) in enumerate(zip(scenes, counts, starts), 1):
        cap.set(cv2.CAP_PROP_POS_MSEC, min(total - 0.05, t0 + count / mv.FPS * 0.55) * 1000)
        ok, frame = cap.read()
        if not ok:
            frame = np.zeros((1080, 1920, 3), np.uint8)
        tile = cv2.resize(frame, (width, int(frame.shape[0] * width / frame.shape[1])), interpolation=cv2.INTER_AREA)
        bar = np.zeros((44, width, 3), np.uint8)
        where = scene.get("file", "card")
        if scene["kind"] == "clip":
            where = "%s %g-%g" % (scene["file"].replace(".mp4", ""), scene["start"], scene["end"])
        cv2.putText(bar, "%d %s" % (number, scene.get("text", "").replace("*", "")[:30]), (3, 16), cv2.FONT_HERSHEY_SIMPLEX, 0.38, (255, 255, 255), 1, cv2.LINE_AA)
        cv2.putText(bar, where[:36], (3, 36), cv2.FONT_HERSHEY_SIMPLEX, 0.34, (0, 220, 255), 1, cv2.LINE_AA)
        tiles.append(np.vstack([tile, bar]))
    blank = np.zeros_like(tiles[0])
    while len(tiles) % cols:
        tiles.append(blank)
    rows = [np.hstack(tiles[i:i + cols]) for i in range(0, len(tiles), cols)]
    cv2.imwrite(out, np.vstack(rows))
    print("%d scenes, %.1f s, sheet %s" % (len(scenes), total, out))


if __name__ == "__main__":
    main()
