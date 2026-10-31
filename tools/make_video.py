"""Builds a promo or tutorial video (1920 by 1080, 30 fps, H.264 with AAC sound) from raw screen recordings of the phone.
Usage: python tools\\make_video.py <spec file> <folder with the raw clips> <output .mp4>
It needs numpy, OpenCV and Pillow in the python that runs it, and the FFmpeg program that the pip package imageio-ffmpeg put in
C:\\Users\\lordhasib\\hsib-toolchain\\video-venv (see FFMPEG below; it mixes the sound and writes the H.264 file).

Every video has its OWN look (colours, type, background, phone frame, caption style, transitions, sound effects): the first line of a spec names it.
  noir (black and gold, slow, cinematic)   bright (flat colour per beat, fast)   pastel (soft, calm)   loud (poster colours, thick type)
  play (confetti and bubbles)   code (dark editor, typed captions)   gallery (ivory wall, serif type, gold: for The Atelier)
A spec can DEFINE ITS OWN LOOK (so every app and every video can have a new one without editing this file), then select it:
    newlook|dusk|base=pastel|bg=#10121C|ink=#F2F0EA|accent=#FF8A5C|dim=#8A8CA0|font=georgiab.ttf|backdrop=waves|phone=thin|frame=#FF8A5C|caption=stack|upper=0|tap=chime|pace=0.2|caption_size=120|tilt=0
    look|dusk
  keys: colours bg ink accent dim frame flash ripple lit lit_outline outline title_lit (#RRGGBB); font (a file in C:\\Windows\\Fonts or  bricolage:ExtraBold ); upper (0 or 1); caption (stack sticker bubble typed); phone (thin solid soft);
  tap (soft pop bloop chime click); cut (hit pop soft glitch); numbers track shadow pulse flash_amount grain vignette tilt pace caption_size title_size; layouts (right,left,mid);
  tones (#a,#b,#c: the colours of the beat in bright and confetti); backdrop (plain waves dots rings stripes confetti grid blobs: palette driven, or one of the stock ones noir pastel loud play code gallery bright).
A spec can start with  format|short  to make a YouTube Short (1080 by 1920, vertical). Then the phone is a window onto the screen (layout slice, the default: a 4 to 5 slice of the
screen that follows the finger; focus=1200 or focus=600>1800 sets it by hand, in raw pixels) or the whole screen (layout=tall), and the caption sits above it, centred.

A spec file has one scene per line, fields separated by |; blank lines and lines starting with # are ignored. Durations are seconds (2.5) or beats (4b).
Options come last as key=value. A caption can mark a word to light up with *stars* and break a line with /.
    look|noir                                                      once, first
    music|file.wav|volume|bpm|start                                once; the file is in store\\videos\\music; the bpm is the music's, so cuts and pulses fall on the beat; start = seconds to skip
    title|Words of the card|small line|dur|options                 a card of big words that arrive one by one (a hairline, then the small line)
    clip|file.mp4|start|end|caption|options                        part of a raw clip (seconds in the recording), shown on the phone with the caption
    still|image.png|caption|dur|options                            a picture of the phone screen (a reveal), slowly drifting
    options  dur=2b   how long it lasts (a clip is sped up or slowed to fit; without dur it plays at speed)
             speed=auto   real time around each touch and fast in between (default with dur), or speed=8 (constant), or ramp=2>12 (speed changes smoothly)
             zoom=540,1200,1.25   push in on a point of the screen (raw pixels 1080 by 2520), zoom=x,y,s0,s1 to move from one scale to another; never closer than 1.15
             trans=flash|whip|zoom|dip|glitch|wipe|dissolve|cut   how this scene arrives (a hard cut by default)
             layout=right|left|mid|big|window   where the phone stands and which side the caption takes (window = the screen fills the picture)
             tone=0..5   the colour of the beat (bright, play), or  pace=0.14  seconds between caption words
             capage=5   the caption is already fully on screen when the shot starts (a second shot of the same feature keeps the caption of the one before)
             boom=1   (still) a riser before it and a boom as it arrives
             tap=540,2300,1.0;120,500,2.5   (still) a touch ring at raw pixel x,y, t seconds after the scene starts (as many as you like, separated by ;)
             focus=1200   (short) the raw pixel row the slice looks at, or focus=600>1800 to pan during the clip
Sound: the music, plus synthesised sounds that belong to each look (taps, whooshes, hits). The first scene fades in from black and the last fades out."""
import collections
import json
import math
import pathlib
import subprocess
import sys
import tempfile
import wave

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
MUSIC = ROOT / "store" / "videos" / "music"
FFMPEG = pathlib.Path(r"C:\Users\lordhasib\hsib-toolchain\video-venv\Lib\site-packages\imageio_ffmpeg\binaries\ffmpeg-win-x86_64-v7.1.exe")
WIN = pathlib.Path(r"C:\Windows\Fonts")
W, H, FPS, SR = 1920, 1080, 30, 44100
CUT_TOP, CUT_BOTTOM = 80 / 2520, 128 / 2520        # the share of the phone screen taken by the status bar and the navigation bar
LATENCY = 0.35                                      # a recording starts about a third of a second after the recorder was launched
BANNER_ROWS = 420
PACE_MUL = 1.0                                      # 0.6 in a Short: the caption words come faster
MAX_ZOOM = 1.15                                     # the closest a zoom may go in on the phone screen (a closer one cut words off at the left and right edges)


def bgr(hexcode):
    h = hexcode.lstrip("#")
    return (int(h[4:6], 16), int(h[2:4], 16), int(h[0:2], 16))


# ---------------------------------------------------------------- the looks, one per video

LOOKS = {
    "noir": dict(
        bg=bgr("#09090B"), ink=bgr("#F2EEE6"), accent=bgr("#D9B25A"), dim=bgr("#8C877D"), caption_font=("constanb.ttf", None), upper=True, track=0.16,
        phone="thin", frame=bgr("#D9B25A"), shadow=0.8, pulse=0.010, flash=bgr("#D9B25A"), flash_amount=0.10, grain=3, vignette=0.55,
        layouts=["mid", "mid"], tilt=0.0, caption_size=112, caption_style="stack", pace=0.22, tap="soft", cut="hit", title_size=150, ripple=bgr("#D9B25A"),
    ),
    "bright": dict(
        bg=bgr("#FFF7EA"), ink=bgr("#15131A"), accent=bgr("#FF4D5E"), dim=bgr("#6B6475"), caption_font=("bricolage", "ExtraBold"), upper=False, track=0.0,
        phone="solid", frame=bgr("#15131A"), shadow=0.55, pulse=0.018, flash=bgr("#FFFFFF"), flash_amount=0.0, grain=1, vignette=0.0,
        layouts=["right", "left"], tilt=3.0, lit=bgr("#FFFFFF"), lit_outline=bgr("#15131A"), caption_size=150, caption_style="stack", pace=0.10, tap="pop", cut="pop", title_size=170, ripple=bgr("#FF4D5E"),
        tones=[bgr("#FFD23F"), bgr("#5BC0EB"), bgr("#FF6B6B"), bgr("#9BE564"), bgr("#B79CED"), bgr("#FF9F1C")],
    ),
    "pastel": dict(
        bg=bgr("#F4EEFB"), ink=bgr("#3A2F66"), accent=bgr("#D4558F"), dim=bgr("#6F6596"), caption_font=("candarab.ttf", None), upper=False, track=0.02,
        phone="soft", frame=bgr("#FFFFFF"), shadow=0.35, pulse=0.006, flash=bgr("#FFFFFF"), flash_amount=0.0, grain=1, vignette=0.12,
        layouts=["mid", "mid"], tilt=0.0, caption_size=108, caption_style="stack", pace=0.28, tap="chime", cut="soft", title_size=130, ripple=bgr("#E58BB4"),
    ),
    "loud": dict(
        bg=bgr("#E4262B"), ink=bgr("#FFFFFF"), accent=bgr("#FFD500"), dim=bgr("#FFE9A0"), caption_font=("impact.ttf", None), upper=True, track=0.01,
        phone="solid", frame=bgr("#111111"), shadow=0.7, pulse=0.022, flash=bgr("#FFD500"), flash_amount=0.12, grain=2, vignette=0.2,
        layouts=["left", "right"], tilt=5.0, caption_size=128, caption_style="sticker", pace=0.12, tap="pop", cut="pop", title_size=210, ripple=bgr("#FFD500"),
        outline=bgr("#111111"), lit=bgr("#E4262B"), title_lit=bgr("#FFD500"),
    ),
    "play": dict(
        bg=bgr("#2B1B63"), ink=bgr("#FFFFFF"), accent=bgr("#FFD24A"), dim=bgr("#C9B8FF"), caption_font=("segoeprb.ttf", None), upper=False, track=0.0,
        phone="solid", frame=bgr("#FFFFFF"), shadow=0.6, pulse=0.016, flash=bgr("#FFFFFF"), flash_amount=0.0, grain=1, vignette=0.15,
        layouts=["right", "left"], tilt=4.0, caption_size=100, caption_style="bubble", pace=0.14, tap="bloop", cut="pop", title_size=160, ripple=bgr("#FFD24A"),
        tones=[bgr("#FF5FA2"), bgr("#2EC4B6"), bgr("#FF8A3D"), bgr("#8E7DFF")], lit=bgr("#FFE066"),
    ),
    "gallery": dict(
        bg=bgr("#F3ECDD"), ink=bgr("#1B1610"), accent=bgr("#A8741A"), dim=bgr("#6F6453"), caption_font=("georgiab.ttf", None), upper=False, track=0.0,
        phone="solid", frame=bgr("#14110D"), shadow=0.55, pulse=0.008, flash=bgr("#FFFFFF"), flash_amount=0.0, grain=1, vignette=0.18,
        layouts=["right", "left"], tilt=0.0, lit=bgr("#A8741A"), caption_size=118, caption_style="stack", pace=0.2, tap="chime", cut="soft", title_size=150, ripple=bgr("#C9983A"),
    ),
    "code": dict(
        bg=bgr("#070B16"), ink=bgr("#DDE6F5"), accent=bgr("#4DF0B0"), dim=bgr("#5E6C8C"), caption_font=("consolab.ttf", None), upper=False, track=0.0,
        phone="thin", frame=bgr("#4DF0B0"), shadow=0.5, pulse=0.008, flash=bgr("#4DF0B0"), flash_amount=0.06, grain=2, vignette=0.35,
        layouts=["right", "left"], tilt=0.0, caption_size=92, caption_style="typed", pace=0.05, tap="soft", cut="glitch", title_size=120, ripple=bgr("#4DF0B0"),
    ),
}

_FONTS = {}


def font(spec, size):
    name, weight = spec
    key = (name, weight, size)
    if key not in _FONTS:
        if name == "bricolage":
            face = ImageFont.truetype(str(ROOT / "design" / "fonts" / "bricolage-grotesque" / "BricolageGrotesque-Variable.ttf"), size)
            try:
                face.set_variation_by_name(weight or "Bold")
            except Exception:
                face.set_variation_by_name("Bold")
        else:
            face = ImageFont.truetype(str(WIN / name), size)
        _FONTS[key] = face
    return _FONTS[key]


def ease(x):
    x = min(1.0, max(0.0, x))
    return 1 - (1 - x) ** 3


def ease_back(x):
    x = min(1.0, max(0.0, x))
    c = 1.7
    return 1 + (c + 1) * (x - 1) ** 3 + c * (x - 1) ** 2


def ease_io(x):
    x = min(1.0, max(0.0, x))
    return x * x * (3 - 2 * x)


# ---------------------------------------------------------------- backgrounds: one function per look, called with the time and the scene's tone

def soft_sprite(radius):
    y, x = np.mgrid[-radius:radius, -radius:radius]
    return (np.clip(1 - np.sqrt(x * x + y * y) / radius, 0, 1) ** 2).astype(np.float32)


def vignette_of(strength):
    yy, xx = np.mgrid[0:H, 0:W]
    return (1 - strength * (np.sqrt(((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2) / 1.41) ** 2.2).astype(np.float32)[:, :, None]


GENERIC = ("plain", "waves", "dots", "rings", "stripes", "confetti", "grid", "blobs")      # backdrops drawn from the look's own colours (the stock ones above are fixed designs)


def soft(image, sigma):
    """A big soft blur done on a picture one eighth the size (the same look, far faster)."""
    small = cv2.resize(image, (image.shape[1] // 8, image.shape[0] // 8), interpolation=cv2.INTER_AREA)
    return cv2.resize(cv2.GaussianBlur(small, (0, 0), sigma), (image.shape[1], image.shape[0]), interpolation=cv2.INTER_LINEAR)


def mix_colour(a, b, k):
    return tuple(int(a[i] * (1 - k) + b[i] * k) for i in range(3))


class Backdrop:
    def __init__(self, name, look):
        self.name, self.look = name, look
        rng = np.random.default_rng(11)
        self.vignette = vignette_of(look["vignette"]) if look["vignette"] else None
        if name in GENERIC:
            self.rng = rng
            bg, acc, ink, dim = look["bg"], look["accent"], look["ink"], look["dim"]
            ramp = np.linspace(0, 1, H, dtype=np.float32)[:, None, None]
            top, bottom = np.array(mix_colour(bg, acc, 0.07), np.float32), np.array(mix_colour(bg, (0, 0, 0), 0.22), np.float32)
            self.base = (top * (1 - ramp) + bottom * ramp).repeat(W, axis=1).astype(np.uint8)
            self.tones = look.get("tones") or [acc, dim, ink]
            self.items = [(rng.uniform(0, W), rng.uniform(0, H), rng.uniform(40, 220), rng.uniform(-24, 24), rng.uniform(-24, 24), rng.uniform(0, 6.28), rng.uniform(-0.6, 0.6), int(rng.integers(0, 3))) for _ in range(36 if name == "confetti" else 7)]
        elif name == "noir":
            base = np.zeros((H, W, 3), np.float32)
            base[:] = look["bg"]
            glow = Image.new("RGB", (W, H), (0, 0, 0))
            ImageDraw.Draw(glow).ellipse((W * 0.25, -H * 0.9, W * 0.95, H * 0.35), fill=(26, 20, 10))
            self.base = np.clip(base + np.array(glow.filter(ImageFilter.GaussianBlur(190)), np.float32)[:, :, ::-1], 0, 255).astype(np.uint8)
            self.dust = [(rng.uniform(0, W), rng.uniform(0, H), rng.uniform(-6, 6), rng.uniform(-22, -6), int(rng.integers(5, 22)), rng.uniform(0.10, 0.34)) for _ in range(46)]
            self.sprites = {r: soft_sprite(r) for r in range(5, 22)}
        elif name == "pastel":
            big = Image.new("RGB", (W + 700, H + 500), bgr("#F4EEFB")[::-1])
            d = ImageDraw.Draw(big)
            for (cx, cy, r, col) in ((400, 300, 520, "#FFD9E6"), (1500, 250, 480, "#D7E8FF"), (900, 900, 560, "#E3D5FF"), (2100, 900, 520, "#D5F5E3"), (200, 1000, 420, "#FFF1C9")):
                d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=col)
            self.big = np.array(big.filter(ImageFilter.GaussianBlur(140)))[:, :, ::-1].copy()
        elif name == "loud":
            size = 160
            stripe = np.zeros((size * 2, size * 2, 3), np.uint8)
            stripe[:] = look["bg"]
            for k in range(-4, 6):
                pts = np.array([[k * size, 0], [k * size + size // 2, 0], [k * size + size // 2 + size * 2, size * 2], [k * size + size * 2, size * 2]], np.int32)
                cv2.fillPoly(stripe, [pts], bgr("#FF5B1F"))
            self.tile = stripe
            self.mid = np.tile(stripe, (H // (size * 2) + 3, W // (size * 2) + 3, 1))
        elif name == "play":
            top, bottom = np.array(bgr("#4B2BA8"), np.float32), np.array(bgr("#1B1048"), np.float32)
            ramp = np.linspace(0, 1, H, dtype=np.float32)[:, None, None]
            self.base = (top * (1 - ramp) + bottom * ramp).repeat(W, axis=1).astype(np.uint8)
            self.confetti = [(rng.uniform(0, W), rng.uniform(0, H), rng.uniform(-30, 30), rng.uniform(40, 120), rng.uniform(10, 26), rng.uniform(0, 6.28), rng.uniform(-3, 3), int(rng.integers(0, 5))) for _ in range(70)]
            self.colors = [bgr("#FF5FA2"), bgr("#2EC4B6"), bgr("#FFD24A"), bgr("#8E7DFF"), bgr("#FFFFFF")]
        elif name == "gallery":
            yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
            light = 1 - 0.20 * (np.sqrt(((xx - W * 0.5) / W) ** 2 + ((yy - H * 0.4) / H) ** 2)) ** 1.3
            ivory = np.array(look["bg"], np.float32)
            self.base = np.clip(ivory[None, None, :] * light[:, :, None] + np.array(bgr("#FFFFFF"), np.float32) * 0.05, 0, 255).astype(np.uint8)
            self.frames = [(rng.uniform(0, W), rng.uniform(0, H), rng.uniform(180, 420), rng.uniform(240, 520), rng.uniform(-9, 9), rng.uniform(-7, 7)) for _ in range(7)]
        elif name == "code":
            base = np.zeros((H, W, 3), np.uint8)
            base[:] = look["bg"]
            for x in range(0, W, 48):
                cv2.line(base, (x, 0), (x, H), (30, 24, 16), 1)
            for y in range(0, H, 48):
                cv2.line(base, (0, y), (W, y), (30, 24, 16), 1)
            self.base = base
        elif name == "bright":
            self.shapes = [(rng.uniform(0, W), rng.uniform(0, H), rng.uniform(140, 420), rng.uniform(-20, 20), rng.uniform(-14, 14), rng.uniform(0, 6.28), rng.uniform(-0.5, 0.5), int(rng.integers(0, 3))) for _ in range(9)]

    def generic_frame(self, t, tone):
        look, name = self.look, self.name
        bg, acc, ink, dim = look["bg"], look["accent"], look["ink"], look["dim"]
        frame = self.base.copy()
        layer = frame.copy()
        if name == "plain":
            sweep = int((t * 90) % (W + 600)) - 300
            cv2.ellipse(layer, (sweep, H // 2), (420, H), 20, 0, 360, mix_colour(bg, acc, 0.16), -1, cv2.LINE_AA)
            layer = soft(layer, 15)
            return cv2.addWeighted(layer, 0.55, frame, 0.45, 0)
        if name == "waves":
            xs = np.arange(0, W + 40, 40)
            for k in range(5):
                ys = H * (0.30 + 0.12 * k) + 55 * np.sin(xs / (210 + 40 * k) + t * (0.5 + 0.12 * k) + k)
                pts = np.concatenate([np.stack([xs, ys], 1), [[W + 40, H], [0, H]]]).astype(np.int32)
                cv2.fillPoly(layer, [pts], mix_colour(bg, acc if k % 2 == 0 else dim, 0.10 + 0.05 * k), cv2.LINE_AA)
            return cv2.addWeighted(layer, 0.8, frame, 0.2, 0)
        if name == "dots":
            step = 64
            for gy in range(0, H + step, step):
                for gx in range(0, W + step, step):
                    r = 2 + 9 * (0.5 + 0.5 * math.sin(gx * 0.011 + gy * 0.008 + t * 1.3))
                    cv2.circle(layer, (gx + (step // 2 if (gy // step) % 2 else 0), gy), int(r), mix_colour(bg, acc, 0.30), -1, cv2.LINE_AA)
            return cv2.addWeighted(layer, 0.7, frame, 0.3, 0)
        if name == "rings":
            cx, cy = W * 0.5, H * 0.45
            for k in range(9):
                r = ((t * 70 + k * 130) % 1200) + 20
                fade = 1 - r / 1220
                cv2.circle(layer, (int(cx), int(cy)), int(r), mix_colour(bg, acc if k % 3 else dim, 0.55 * fade), 3, cv2.LINE_AA)
            return layer
        if name == "stripes":
            size = 150
            off = int(t * 70) % (size * 2)
            for k in range(-4, (W + H) // size + 4):
                x0 = k * size * 2 - off
                pts = np.array([[x0, 0], [x0 + size, 0], [x0 + size - H, H], [x0 - H, H]], np.int32)
                cv2.fillConvexPoly(layer, pts, mix_colour(bg, acc, 0.12), cv2.LINE_AA)
            return layer
        if name == "confetti":
            for (x, y, sz, vx, vy, a, spin, kind) in self.items:
                cx, cy = (x + vx * t * 2) % W, (y + (abs(vy) + 30) * t) % H
                ang, s2 = a + spin * t, sz * 0.18
                pts = np.array([[cx + s2 * math.cos(ang + q * math.pi / 2 + 0.78), cy + s2 * math.sin(ang + q * math.pi / 2 + 0.78) * 0.6] for q in range(4)], np.int32)
                cv2.fillConvexPoly(layer, pts, self.tones[kind % len(self.tones)], cv2.LINE_AA)
            return cv2.addWeighted(layer, 0.55, frame, 0.45, 0)
        if name == "grid":
            step = 72
            off = int(t * 24) % step
            for x in range(-step, W + step, step):
                cv2.line(layer, (x + off, 0), (x + off, H), mix_colour(bg, acc, 0.16), 1, cv2.LINE_AA)
            for y in range(-step, H + step, step):
                cv2.line(layer, (0, y + off), (W, y + off), mix_colour(bg, acc, 0.16), 1, cv2.LINE_AA)
            y = int((t * 170) % (H + 300)) - 150
            cv2.rectangle(layer, (0, y), (W, y + 110), mix_colour(bg, acc, 0.10), -1)
            return layer
        for (x, y, sz, vx, vy, a, spin, kind) in self.items:                    # blobs
            cx, cy = (x + vx * t) % (W + 400) - 200, (y + vy * t) % (H + 400) - 200
            cv2.circle(layer, (int(cx), int(cy)), int(sz * 2.2), mix_colour(bg, self.tones[kind % len(self.tones)], 0.30), -1, cv2.LINE_AA)
        layer = soft(layer, 11)
        return cv2.addWeighted(layer, 0.85, frame, 0.15, 0)

    def frame(self, t, tone=0):
        look = self.look
        if self.name in GENERIC:
            frame = self.generic_frame(t, tone)
        elif self.name == "noir":
            frame = self.base.copy()
            for (x, y, vx, vy, r, power) in self.dust:
                cx, cy = int((x + vx * t) % W), int((y + vy * t) % H)
                x0, y0, x1, y1 = max(0, cx - r), max(0, cy - r), min(W, cx + r), min(H, cy + r)
                if x1 > x0 and y1 > y0:
                    sprite = self.sprites[r][y0 - (cy - r): y1 - (cy - r), x0 - (cx - r): x1 - (cx - r)]
                    tint = (sprite[:, :, None] * np.array(look["accent"], np.float32) * power * 0.7).astype(np.int16)
                    frame[y0:y1, x0:x1] = np.clip(frame[y0:y1, x0:x1].astype(np.int16) + tint, 0, 255).astype(np.uint8)
        elif self.name == "pastel":
            ox, oy = int(350 + 320 * math.sin(t * 0.17)), int(250 + 220 * math.cos(t * 0.13))
            frame = self.big[oy: oy + H, ox: ox + W].copy()
        elif self.name == "loud":
            size = 320
            off = int(t * 90) % size
            frame = self.mid[off: off + H, off: off + W].copy()
            tint = np.array(bgr("#B8141A"), np.float32)
            frame = (frame.astype(np.float32) * 0.92 + tint * 0.08).astype(np.uint8)
        elif self.name == "play":
            frame = self.base.copy()
            for (x, y, vx, vy, s, a, spin, ci) in self.confetti:
                cx, cy = (x + vx * t) % W, (y + vy * t) % H
                ang = a + spin * t
                c, s_ = math.cos(ang) * s, math.sin(ang) * s
                pts = np.array([[cx - c, cy - s_ * 0.5], [cx + s_, cy - c * 0.5], [cx + c, cy + s_ * 0.5], [cx - s_, cy + c * 0.5]], np.int32)
                cv2.fillConvexPoly(frame, pts, self.colors[ci], cv2.LINE_AA)
            frame = cv2.addWeighted(frame, 0.82, self.base, 0.18, 0)
        elif self.name == "gallery":                      # picture frames on a gallery wall, drifting very slowly
            frame = self.base.copy()
            layer = frame.copy()
            edge = tuple(int(c * 0.74) for c in look["bg"])
            for (x, y, w, h, vx, vy) in self.frames:
                x0, y0 = int((x + vx * t) % (W + 400)) - 200, int((y + vy * t) % (H + 400)) - 200
                cv2.rectangle(layer, (x0, y0), (x0 + int(w), y0 + int(h)), edge, 3, cv2.LINE_AA)
                cv2.rectangle(layer, (x0 + 16, y0 + 16), (x0 + int(w) - 16, y0 + int(h) - 16), edge, 1, cv2.LINE_AA)
            frame = cv2.addWeighted(layer, 0.45, frame, 0.55, 0)
        elif self.name == "code":
            frame = self.base.copy()
            y = int((t * 160) % (H + 200)) - 100
            cv2.rectangle(frame, (0, y), (W, y + 90), (28, 22, 12), -1)
            frame = cv2.addWeighted(frame, 0.7, self.base, 0.3, 0)
        else:  # bright: a flat colour per beat with big drifting shapes
            colour = np.array(look["tones"][tone % len(look["tones"])], np.float32)
            frame = np.empty((H, W, 3), np.uint8)
            frame[:] = colour.astype(np.uint8)
            layer = frame.copy()
            for (x, y, r, vx, vy, a, spin, kind) in self.shapes:
                cx, cy = (x + vx * t) % W, (y + vy * t) % H
                ang = a + spin * t
                if kind == 0:
                    cv2.circle(layer, (int(cx), int(cy)), int(r), (255, 255, 255), -1, cv2.LINE_AA)
                else:
                    pts = np.array([[cx + r * math.cos(ang + k * math.pi / 2 + math.pi / 4), cy + r * math.sin(ang + k * math.pi / 2 + math.pi / 4)] for k in range(4)], np.int32)
                    cv2.fillConvexPoly(layer, pts, (255, 255, 255) if kind == 1 else (int(colour[0] * 0.8), int(colour[1] * 0.8), int(colour[2] * 0.8)), cv2.LINE_AA)
            frame = cv2.addWeighted(layer, 0.16, frame, 0.84, 0)
        if self.vignette is not None:
            frame = (frame.astype(np.float32) * self.vignette).astype(np.uint8)
        return frame


# ---------------------------------------------------------------- animated words

def render_word(text, face, colour, outline=None, stroke=0):
    probe = ImageDraw.Draw(Image.new("L", (4, 4)))
    box = probe.textbbox((0, 0), text, font=face, stroke_width=stroke)
    pad = 10 + stroke
    w, h = box[2] - box[0] + pad * 2, box[3] - box[1] + pad * 2
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(img).text((pad - box[0], pad - box[1]), text, font=face, fill=colour[::-1] + (255,), stroke_width=stroke, stroke_fill=(outline[::-1] + (255,)) if outline else None)
    arr = np.array(img).astype(np.float32)
    return arr[:, :, 2::-1].copy(), arr[:, :, 3] / 255.0, w, h


def blit(frame, color, alpha, cx, cy, scale=1.0, opacity=1.0):
    if opacity <= 0.003 or scale <= 0.02:
        return
    if abs(scale - 1.0) > 0.01:
        color = cv2.resize(color, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA if scale < 1 else cv2.INTER_LINEAR)
        alpha = cv2.resize(alpha, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA if scale < 1 else cv2.INTER_LINEAR)
    h, w = alpha.shape
    x0, y0 = int(round(cx - w / 2)), int(round(cy - h / 2))
    xa, ya, xb, yb = max(0, x0), max(0, y0), min(W, x0 + w), min(H, y0 + h)
    if xb <= xa or yb <= ya:
        return
    a = (alpha[ya - y0: yb - y0, xa - x0: xb - x0] * opacity)[:, :, None]
    region = frame[ya:yb, xa:xb].astype(np.float32)
    frame[ya:yb, xa:xb] = (region * (1 - a) + color[ya - y0: yb - y0, xa - x0: xb - x0] * a).astype(np.uint8)


class Caption:
    """Words that arrive one by one (pop, slide or typed), laid out inside a box (x0, y0, x1, y1)."""
    def __init__(self, text, look, box, align="left", size=None, pace=None, style=None, tone_colour=None):
        self.look, self.style = look, style or look["caption_style"]
        self.pace = (look["pace"] if pace is None else pace) * PACE_MUL
        face_spec = look["caption_font"]
        size = size or look["caption_size"]
        x0, y0, x1, y1 = box
        raw = text.upper() if look["upper"] else text
        lines = [[w for w in part.split()] for part in raw.split("/")]
        ink, accent = look["ink"], look.get("lit", look["accent"])
        self.tone_colour = tone_colour
        probe = ImageDraw.Draw(Image.new("L", (4, 4)))
        for trial in (size, size * 0.88, size * 0.76, size * 0.66, size * 0.56):
            face = font(face_spec, int(trial))
            flows = []
            for words in lines:                                  # wrap each author line to the box
                row, rows = [], []
                for word in words:
                    trial_row = row + [word]
                    width = sum(probe.textlength(w.replace("*", ""), font=face) for w in trial_row) + face.size * 0.3 * (len(trial_row) - 1)
                    if width > x1 - x0 and row:
                        rows.append(row)
                        row = [word]
                    else:
                        row = trial_row
                if row:
                    rows.append(row)
                flows += rows
            if len(flows) * face.size * 1.12 <= y1 - y0:
                break
        self.words = []
        line_h = face.size * 1.12
        total_h = len(flows) * line_h
        top = (y0 + y1) / 2 - total_h / 2
        outline = look.get("outline")
        order = 0
        for i, row in enumerate(flows):
            widths = [probe.textlength(w.replace("*", ""), font=face) for w in row]
            gap = face.size * 0.3
            row_w = sum(widths) + gap * (len(row) - 1)
            x = x0 if align == "left" else (x0 + x1) / 2 - row_w / 2
            for word, width in zip(row, widths):
                lit = "*" in word
                clean = word.replace("*", "")
                colour = accent if lit else ink
                edge = look.get("lit_outline") if lit else outline
                stroke = int(face.size * 0.07) if edge else 0
                col, alpha, w, h = render_word(clean, face, colour, edge, stroke)
                self.words.append(dict(col=col, alpha=alpha, cx=x + width / 2, cy=top + i * line_h + line_h / 2, t0=order * self.pace, lit=lit, text=clean, width=width))
                order += 1
                x += width + gap
        self.bounds = (min(w["cx"] - w["width"] / 2 for w in self.words), top, max(w["cx"] + w["width"] / 2 for w in self.words), top + total_h)
        self.face = face

    def draw(self, frame, age):
        look = self.look
        if self.style in ("sticker", "bubble"):
            bx0, by0, bx1, by1 = self.bounds
            k = ease_back((age) / 0.35)
            pad = 36
            tone = look["accent"] if self.style == "sticker" else (self.tone_colour or look["accent"])
            layer = frame.copy()
            cx, cy = (bx0 + bx1) / 2, (by0 + by1) / 2
            half_w, half_h = (bx1 - bx0) / 2 + pad, (by1 - by0) / 2 + pad * 0.7
            if self.style == "sticker":
                ang = -4
                pts = cv2.boxPoints(((cx, cy), (2 * half_w * k, 2 * half_h * k), ang)).astype(np.int32)
                cv2.fillConvexPoly(layer, pts, (0, 0, 0), cv2.LINE_AA)
                pts2 = cv2.boxPoints(((cx - 10, cy - 10), (2 * half_w * k, 2 * half_h * k), ang)).astype(np.int32)
                cv2.fillConvexPoly(layer, pts2, tone, cv2.LINE_AA)
            else:
                cv2.ellipse(layer, (int(cx), int(cy)), (int(half_w * k * 1.04), int(half_h * k * 1.12)), 0, 0, 360, tone, -1, cv2.LINE_AA)
            frame[:] = cv2.addWeighted(layer, 0.96, frame, 0.04, 0)
        for w in self.words:
            k = (age - w["t0"]) / (0.18 if self.style == "typed" else 0.32)
            if k <= 0:
                continue
            if self.style == "typed":
                if w["t0"] <= age:
                    blit(frame, w["col"], w["alpha"], w["cx"], w["cy"], 1.0, 1.0)
                continue
            scale = 0.55 + 0.45 * ease_back(k) if self.style != "stack" else 1.0
            lift = (1 - ease(k)) * (26 if self.style == "stack" else 0)
            col = w["col"]
            blit(frame, col, w["alpha"], w["cx"], w["cy"] + lift, scale, min(1.0, k * 2.2))
        if self.style == "typed":
            last = max((w["t0"] for w in self.words if w["t0"] <= age), default=None)
            if last is not None and int(age * 3) % 2 == 0:
                w = [x for x in self.words if x["t0"] == last][0]
                x = int(w["cx"] + w["width"] / 2 + 8)
                cv2.rectangle(frame, (x, int(w["cy"] - self.face.size * 0.45)), (x + 14, int(w["cy"] + self.face.size * 0.45)), self.look["accent"], -1)


# ---------------------------------------------------------------- the phone

def rounded_mask(w, h, radius):
    m = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w - 1, h - 1), radius=radius, fill=255)
    return (np.array(m).astype(np.float32) / 255)[:, :, None]


SHADOWS = {}


def phone_tile(screen, height, look):
    """The recorded screen (status and navigation bars cut off) in a frame of the look's kind: (BGR float tile, alpha, shadow alpha, pad)."""
    sh = int(height)
    sw = int(round(screen.shape[1] * sh / screen.shape[0]))
    scaled = cv2.resize(screen, (sw, sh), interpolation=cv2.INTER_AREA).astype(np.float32)
    kind = look["phone"]
    bezel = {"thin": 5, "solid": 18, "soft": 14}[kind]
    radius = {"thin": 46, "solid": 56, "soft": 64}[kind]
    tw, th = sw + bezel * 2, sh + bezel * 2
    outer = rounded_mask(tw, th, radius + bezel)
    inner = rounded_mask(sw, sh, radius)
    tile = np.zeros((th, tw, 3), np.float32)
    tile[:] = look["frame"]
    tile[bezel: bezel + sh, bezel: bezel + sw] = scaled * inner + tile[bezel: bezel + sh, bezel: bezel + sw] * (1 - inner)
    if kind == "solid" and look["frame"] != (255, 255, 255):
        cv2.rectangle(tile, (tw // 2 - 40, 5), (tw // 2 + 40, 12), (60, 60, 60), -1)        # a speaker slit
    if kind not in SHADOWS:
        pad = 120
        box = np.zeros((th + pad * 2, tw + pad * 2), np.float32)
        box[pad:pad + th, pad:pad + tw] = outer[:, :, 0]
        SHADOWS[kind] = (cv2.GaussianBlur(box, (0, 0), 46), tw)
    blur, tw0 = SHADOWS[kind]
    k = tw / tw0
    shadow = (cv2.resize(blur, None, fx=k, fy=k, interpolation=cv2.INTER_LINEAR) if abs(k - 1) > 0.02 else blur, 0)
    return tile, outer[:, :, 0], shadow, tw, th


def paste_phone(frame, tile, alpha, shadow, cx, cy, roll=0.0, yaw=0.0, shadow_strength=0.6):
    th, tw = tile.shape[:2]
    blur, pad = shadow
    # shadow first (a soft dark patch under the phone, a little lower than the phone)
    sx0, sy0 = int(cx - blur.shape[1] / 2 + 10), int(cy - blur.shape[0] / 2 + 34)
    xa, ya, xb, yb = max(0, sx0), max(0, sy0), min(W, sx0 + blur.shape[1]), min(H, sy0 + blur.shape[0])
    if xb > xa and yb > ya:
        part = blur[ya - sy0: yb - sy0, xa - sx0: xb - sx0, None] * shadow_strength
        frame[ya:yb, xa:xb] = (frame[ya:yb, xa:xb].astype(np.float32) * (1 - part)).astype(np.uint8)
    cyaw = math.cos(math.radians(yaw))
    hw, hh = tw / 2 * cyaw, th / 2
    skew = 0.08 * math.sin(math.radians(yaw))
    corners = np.array([[-hw, -hh * (1 + skew)], [hw, -hh * (1 - skew)], [hw, hh * (1 - skew)], [-hw, hh * (1 + skew)]], np.float32)
    ang = math.radians(roll)
    rot = np.array([[math.cos(ang), -math.sin(ang)], [math.sin(ang), math.cos(ang)]], np.float32)
    target = corners @ rot.T + np.array([cx, cy], np.float32)
    x0, y0 = int(max(0, target[:, 0].min() - 2)), int(max(0, target[:, 1].min() - 2))
    x1, y1 = int(min(W, target[:, 0].max() + 3)), int(min(H, target[:, 1].max() + 3))
    if x1 <= x0 or y1 <= y0:
        return
    source = np.float32([[0, 0], [tw, 0], [tw, th], [0, th]])
    matrix = cv2.getPerspectiveTransform(source, target - np.float32([x0, y0]))
    box = cv2.warpPerspective(np.dstack([tile, alpha * 255]), matrix, (x1 - x0, y1 - y0), flags=cv2.INTER_LINEAR)
    a = box[:, :, 3:4] / 255.0
    frame[y0:y1, x0:x1] = (box[:, :, :3] * a + frame[y0:y1, x0:x1].astype(np.float32) * (1 - a)).astype(np.uint8)


def window_band(screen, zoom, progress):
    """A 16 by 9 band across the width of the screen (the screen fills the picture), centred on the zoom point; a larger scale makes the band narrower."""
    sh, sw = screen.shape[:2]
    x, y, s0, s1 = zoom if zoom else (540, 700, 1.0, 1.0)
    s = s0 + (s1 - s0) * ease_io(progress)
    vw = sw / max(1.0, s)
    vh = vw * 9 / 16
    scale_y = sh / (2520 * (1 - CUT_TOP - CUT_BOTTOM))
    cx, cy = x * sw / 1080, (y - CUT_TOP * 2520) * scale_y
    x0 = min(max(0, cx - vw / 2), sw - vw)
    y0 = min(max(0, cy - vh / 2), sh - vh)
    return screen[int(y0): int(y0 + vh), int(x0): int(x0 + vw)]


def crop_screen(raw):
    h = raw.shape[0]
    return raw[int(h * CUT_TOP): h - int(h * CUT_BOTTOM)]


def zoomed(screen, zoom, progress):
    """A window of the screen: zoom = (x, y, s0, s1) in raw pixels (1080 by 2520); the scale moves from s0 to s1 as progress goes 0 to 1."""
    if not zoom:
        return screen
    x, y, s0, s1 = zoom
    s = min(MAX_ZOOM, s0 + (s1 - s0) * ease_io(progress))      # never closer than MAX_ZOOM, so text at the edge of the screen is not cut off
    if s <= 1.001:
        return screen
    sh, sw = screen.shape[:2]
    scale_y = sh / (2520 * (1 - CUT_TOP - CUT_BOTTOM))
    cx, cy = x * sw / 1080, (y - CUT_TOP * 2520) * scale_y
    vw, vh = sw / s, sh / s
    x0 = min(max(0, cx - vw / 2), sw - vw)
    y0 = min(max(0, cy - vh / 2), sh - vh)
    return screen[int(y0): int(y0 + vh), int(x0): int(x0 + vw)]


# ---------------------------------------------------------------- touches, the speed plan

def touch_point(tap_x, tap_y, shape):
    height, width = shape[:2]
    return int(tap_x / 1080 * width), int((tap_y - CUT_TOP * 2520) / (2520 * (1 - CUT_TOP - CUT_BOTTOM)) * height)


def ripple(screen, point, k, colour, scale=1.0):
    radius, alpha = int((30 + 70 * ease(k)) * scale), 0.75 * (1 - k)
    layer = screen.copy()
    cv2.circle(layer, point, radius, (255, 255, 255), -1, cv2.LINE_AA)
    cv2.circle(layer, point, radius, colour, max(2, int(6 * scale)), cv2.LINE_AA)
    cv2.addWeighted(layer, alpha * 0.55, screen, 1 - alpha * 0.55, 0, dst=screen)


def draw_touches(raw, touches, at, colour):
    """Soft rings where a finger touched, drawn on the full recording before it is cropped and scaled."""
    for touch in touches:
        age = at - (touch["t"] - LATENCY)
        if touch["kind"] == "tap" and 0 <= age < 0.6:
            ripple(raw, (touch["x"], touch["y"]), age / 0.6, colour)
        elif touch["kind"] == "swipe":
            seconds = touch["ms"] / 1000
            if 0 <= age < seconds + 0.3:
                progress = min(1.0, age / max(0.05, seconds))
                ripple(raw, (int(touch["x"] + (touch["x2"] - touch["x"]) * progress), int(touch["y"] + (touch["y2"] - touch["y"]) * progress)), 0.18, colour)


def load_touches(path):
    sidecar = path.with_name(path.stem + ".taps.json")
    return json.loads(sidecar.read_text()) if sidecar.exists() else []


def source_times(touches, start, end, frames, speed, ramp):
    """The recording time shown by each output frame. speed: a number (constant), 'auto' (real time around every touch, fast in between) or None (fit);
    ramp (a, b): the speed moves smoothly from a to b. Either way the clip fills exactly `frames` output frames."""
    n = max(2, frames)
    grid = np.arange(start, end, 0.02)
    if ramp:
        a, b = ramp
        rel = np.linspace(a, b, len(grid))
    elif speed == "auto":
        rel = np.full(len(grid), 12.0)
        for touch in touches:
            lo = touch["t"] - LATENCY - 0.25
            hi = touch["t"] - LATENCY + (touch["ms"] / 1000 if touch["kind"] == "swipe" else 0) + 0.9
            rel[(grid >= lo) & (grid <= hi)] = 1.0
        k = np.ones(25) / 25
        rel = np.convolve(np.pad(rel, 12, mode="edge"), k, mode="valid")[: len(grid)]      # a smooth ramp in and out of the fast parts
    else:
        return [start + (end - start) * i / (n - 1) for i in range(n)]
    cumulative = np.cumsum(0.02 / rel)
    cumulative = cumulative / cumulative[-1]
    return [float(np.interp(i / (n - 1), cumulative, grid)) for i in range(n)]


def beats_to_seconds(text, bpm):
    text = text.strip()
    if text.endswith("b") and not bpm:
        sys.exit("refused: a duration in beats (%s) needs the bpm in the music line: music|file.wav|0.9|<bpm>" % text)
    return float(text[:-1]) * 60.0 / bpm if text.endswith("b") else float(text)


# ---------------------------------------------------------------- banners and shared compositing

def cover_banner(shown, state):
    """A notification banner that popped up while the phone was recorded (a neutral dark grey card over the top of the screen) must never reach a
    public video: while one is there, and for a moment after, the top of the picture is taken from the last frame without one."""
    region = shown[110:340:3, 40:1040:3].astype(np.int16)
    b, g, r = region[:, :, 0], region[:, :, 1], region[:, :, 2]
    ratio = float(((np.abs(r - g) < 7) & (np.abs(g - b) < 7) & (r > 24) & (r < 75)).mean())
    if ratio > 0.04:
        state["hold"] = 5
    if state["hold"] > 0:
        state["hold"] -= 1
        if state["clean"] is not None:
            edge = shown[BANNER_ROWS - 30: BANNER_ROWS].astype(np.float32)
            fade = np.linspace(1, 0, 30, dtype=np.float32)[:, None, None]
            shown[:BANNER_ROWS] = state["clean"]
            shown[BANNER_ROWS - 30: BANNER_ROWS] = (state["clean"][BANNER_ROWS - 30:] * fade + edge * (1 - fade)).astype(np.uint8)
    else:
        state["clean"] = shown[:BANNER_ROWS].copy()


class Studio:
    """Everything a look needs to draw a frame: background, phone, captions, beat pulse, grain."""
    def __init__(self, name, bpm):
        self.name, self.look, self.bpm = name, LOOKS[name], bpm
        self.back = Backdrop(self.look.get("backdrop", name), self.look)
        self.grain = [np.random.default_rng(100 + k).integers(0, 1 + 2 * self.look["grain"], (H, W, 1), dtype=np.uint8).repeat(3, axis=2) for k in range(5)]
        self.clock = 0

    def pulse(self, t):
        if not self.bpm:
            return 0.0
        phase = (t * self.bpm / 60.0) % 1.0
        return math.exp(-6 * phase)

    def center_y(self, name):
        if H > W:
            return 975 if name != "tall" else 1020
        return H / 2

    def layout(self, name, phone_w):
        """(phone centre x, phone height, roll, caption box, caption alignment)"""
        look = self.look
        if H > W:                                                    # a Short: the caption on top, the phone under it, the bottom fifth left free for YouTube's own buttons
            if name == "tall":
                return W / 2, 1240, look["tilt"] * 0.4, (70, 90, W - 70, 380), "center"
            return W / 2, 1150, look["tilt"] * 0.4, (70, 90, W - 70, 380), "center"
        h = 940
        if name == "right":
            return W - 130 - phone_w / 2 - 40, h, look["tilt"], (110, 150, W - 140 - phone_w - 120, H - 150), "left"
        if name == "left":
            return 130 + phone_w / 2 + 40, h, -look["tilt"], (140 + phone_w + 140, 150, W - 110, H - 150), "left"
        if name == "big":
            return W - 430, 1500, look["tilt"], (110, 150, W - 430 - phone_w * 0.8 - 80, H - 150), "left"
        return W * 0.66, 980, look["tilt"] * 0.4, (130, 170, W * 0.66 - phone_w / 2 - 110, H - 170), "left"

    def compose(self, t, local, screen, layout, caption, tone, zoom_progress, scene_age, roll_mod=0.0, intro=True, window=False):
        look = self.look
        frame = self.back.frame(t, tone)
        pulse = self.pulse(t)
        if window and screen is not None:
            full = cv2.resize(screen, (W, H), interpolation=cv2.INTER_LINEAR)
            frame = full
            if caption:
                caption.draw(frame, scene_age)
        elif screen is not None:
            sw = int(round(screen.shape[1] * 940 / screen.shape[0]))
            cx, ph, roll, box, align = self.layout(layout, sw)
            grow = (0.94 + 0.06 * ease(scene_age / 0.5)) * (1 + look["pulse"] * pulse)
            tile, alpha, shadow, tw, th = phone_tile(screen, ph * grow, look)
            drift = 5.0 * math.sin(t * 0.8)
            paste_phone(frame, tile, alpha, shadow, cx, self.center_y(layout) + (6 * math.sin(t * 1.1) if look["tilt"] else 0), roll=roll + drift * (0.3 if look["tilt"] else 0) + roll_mod, yaw=0.0, shadow_strength=look["shadow"])
            if caption:
                caption.draw(frame, scene_age)
        elif caption:
            caption.draw(frame, scene_age)
        if look["flash_amount"] and pulse > 0.02:
            a = look["flash_amount"] * pulse
            frame = cv2.addWeighted(frame, 1 - a, np.full_like(frame, look["flash"]), a, 0)
        return frame

    def finish(self, frame):
        self.clock += 1
        return cv2.subtract(cv2.add(frame, self.grain[self.clock % 5]), self.look["grain"] // 2 + 1)


# ---------------------------------------------------------------- scenes (each yields frames)

def title_scene(studio, text, sub, frames, start_t, opts):
    look = studio.look
    pace = float(opts.get("pace", look["pace"] * 1.4))
    portrait = H > W
    look = dict(look, lit=look.get("title_lit", look.get("lit", look["accent"])))       # the lit word of a card must show on the page colour (red on red did not)
    cap = Caption(text, look, (80, 260, W - 80, H - 700) if portrait else (160, 200, W - 160, H - 340 if sub else H - 200), align="center", size=int(look["title_size"] * (0.62 if portrait else 1)), pace=pace, style="stack" if look["caption_style"] != "typed" else "typed")
    face = font(look["caption_font"], 54)
    sub_sprite = render_word(sub.upper() if look["upper"] else sub, face, look["dim"] if look["bg"][0] < 128 else look["ink"], None, 0) if sub else None
    total = frames
    for n in range(total):
        t = start_t + n / FPS
        age = n / FPS
        frame = studio.back.frame(t, int(opts.get("tone", 0)))
        pulse = studio.pulse(t)
        cap.draw(frame, age)
        if sub_sprite is not None:
            k = ease((age - 0.4 - len(cap.words) * cap.pace) / 0.6)
            blit(frame, sub_sprite[0], sub_sprite[1], W / 2, (H - 560 if portrait else H - 230) + (1 - k) * 18, 1.0, k)
        if look["phone"] == "thin" and look["bg"][0] < 128:           # a gold hairline that draws itself
            k = ease((age - 0.2) / 1.2)
            half = int(260 * k)
            cv2.line(frame, (W // 2 - half, H - 300), (W // 2 + half, H - 300), look["accent"], 2, cv2.LINE_AA)
        if look["flash_amount"]:
            a = look["flash_amount"] * pulse * 0.7
            frame = cv2.addWeighted(frame, 1 - a, np.full_like(frame, look["flash"]), a, 0)
        zoom = 1.0 + 0.035 * n / max(1, total)
        matrix = cv2.getRotationMatrix2D((W / 2, H / 2), 0, zoom)
        yield cv2.warpAffine(frame, matrix, (W, H), flags=cv2.INTER_LINEAR)


SLICE_ASPECT = 0.8                                  # width over height of the part of the screen a Short shows (a little over half of the screen's height)


def slice_view(screen, focus, aspect=SLICE_ASPECT):
    """The part of the screen (full width) that is `aspect` wide for its height, with the row `focus` at 45 percent of the way down it."""
    sh, sw = screen.shape[:2]
    vh = min(sh, int(sw / aspect))
    y0 = int(min(max(0, focus - vh * 0.45), sh - vh))
    return screen[y0: y0 + vh]


def raw_row_to_screen(y, screen_height):
    return (y - CUT_TOP * 2520) * screen_height / (2520 * (1 - CUT_TOP - CUT_BOTTOM))


def touch_row(touches, wanted):
    """The raw pixel row of the latest touch at or before `wanted` (a swipe counts at its end), or None."""
    row = None
    for touch in touches:
        if touch["kind"] in ("tap", "swipe") and touch["t"] - LATENCY <= wanted + 0.15 and touch.get("y") is not None:
            row = touch["y2"] if touch["kind"] == "swipe" and touch.get("y2") is not None else touch["y"]
    return row


class Focus:
    """The row of the screen a Short looks at: it eases towards the latest touch (or towards the pan set by hand with focus=a or focus=a>b)."""
    def __init__(self, touches, start, opts, screen_height, frames):
        self.touches, self.start, self.sh, self.frames = touches, start, screen_height, max(1, frames - 1)
        self.manual = None
        if "focus" in opts:
            a = [float(v) for v in opts["focus"].split(">")]
            self.manual = (a[0], a[-1])
        first = touch_row(touches, start)
        if first is None:
            ahead = [t for t in touches if t["kind"] in ("tap", "swipe") and t["t"] - LATENCY >= start]
            first = ahead[0]["y"] if ahead else 1100
        self.now = raw_row_to_screen(self.manual[0] if self.manual else first, screen_height)

    def at(self, wanted, n):
        if self.manual:
            target = raw_row_to_screen(self.manual[0] + (self.manual[1] - self.manual[0]) * ease_io(n / self.frames), self.sh)
        else:
            row = touch_row(self.touches, wanted)
            target = raw_row_to_screen(row, self.sh) if row is not None else self.now
        self.now += (target - self.now) * 0.10
        return self.now


PTS_CACHE = {}


def pts_index(path):
    """The real time of every frame of a recording. The phone's recorder stores a frame only when the screen changes, so the frame rate is not constant and
    seeking by time (or by frame number) lands seconds away from where it should: the only exact way is to count frames from the start (a few seconds per file)."""
    key = str(path)
    if key not in PTS_CACHE:
        cache = path.with_name(path.stem + ".pts.json")
        if cache.exists() and cache.stat().st_mtime >= path.stat().st_mtime:
            PTS_CACHE[key] = json.loads(cache.read_text())
        else:
            cap = cv2.VideoCapture(str(path))
            if not cap.isOpened():
                sys.exit("cannot open " + key)
            times = []
            while cap.grab():
                times.append(round(cap.get(cv2.CAP_PROP_POS_MSEC) / 1000.0, 3))
            cap.release()
            cache.write_text(json.dumps(times))
            PTS_CACHE[key] = times
    return PTS_CACHE[key]


class Reader:
    """Reads a recording forward, starting at the frame that was on screen at `start`. The picture at a later time is the latest frame stored at or before it
    (the next stored frame may be many seconds away)."""
    def __init__(self, path, start):
        times = pts_index(path)
        k = 0
        while k + 1 < len(times) and times[k + 1] <= start:
            k += 1
        self.times, self.k = times, k
        self.cap = cv2.VideoCapture(str(path))
        for _ in range(k):
            self.cap.grab()
        ok, first = self.cap.read()
        if not ok:
            sys.exit("empty recording " + str(path))
        self.raw, self.at = first, times[k]
        self.ahead = None
        self._fetch()

    def _fetch(self):
        ok, frame = self.cap.read()
        self.k += 1
        self.ahead = (self.times[self.k], frame) if ok and self.k < len(self.times) else None

    def get(self, wanted):
        while self.ahead is not None and self.ahead[0] <= wanted:
            self.at, self.raw = self.ahead
            self._fetch()
        return self.raw


def clip_scene(studio, path, start, end, caption_text, frames, speed, ramp, zoom, layout, tone, start_t, opts):
    look = studio.look
    touches = load_touches(path)
    times = source_times(touches, start, end, frames, speed, ramp)
    reader = Reader(path, start)
    state = {"clean": None, "hold": 0}
    probe = crop_screen(reader.raw)
    sw = int(round(probe.shape[1] * 940 / probe.shape[0]))
    window = layout == "window"
    portrait = H > W
    focus = Focus(touches, start, opts, probe.shape[0], len(times)) if portrait and layout == "slice" else None
    box, align = studio.layout("mid" if window else layout, sw)[3:5]
    if window:
        box = (110, H - 360, W - 110, H - 80)
    cap = Caption(caption_text, look, box, align=align, pace=float(opts["pace"]) if "pace" in opts else None, tone_colour=(look["tones"][tone % len(look["tones"])] if look.get("tones") else None)) if caption_text else None
    if cap and window:
        cap = Caption(caption_text, look, (110, H - 340, W - 110, H - 70), align="left", size=int(look["caption_size"] * 0.8), pace=float(opts["pace"]) if "pace" in opts else None)
    for n, wanted in enumerate(times):
        raw = reader.get(wanted).copy()
        cover_banner(raw, state)
        draw_touches(raw, touches, wanted, look["ripple"])
        screen = crop_screen(raw)
        if focus is not None:
            screen = slice_view(screen, focus.at(wanted, n))
        elif not portrait:
            screen = window_band(screen, zoom, n / max(1, len(times) - 1)) if window else zoomed(screen, zoom, n / max(1, len(times) - 1))
        t = start_t + n / FPS
        yield studio.compose(t, n, screen, layout, cap, tone, n / FPS, n / FPS + float(opts.get("capage", 0)), window=window)


def still_scene(studio, path, caption_text, frames, zoom, layout, tone, start_t, opts):
    look = studio.look
    picture = cv2.imread(str(path))
    if picture is None:
        sys.exit("cannot read " + str(path))
    ph, pw = picture.shape[:2]
    scale = pw / 1080.0
    taps = []
    for item in opts.get("tap", "").split(";"):
        if item.strip():
            x, y, at = [float(v) for v in item.split(",")]
            taps.append((int(x * scale), int(y * scale), at))
    probe = crop_screen(picture) if ph / pw > 1.8 else picture
    sw = int(round(probe.shape[1] * 940 / probe.shape[0]))
    cap = Caption(caption_text, look, studio.layout(layout, sw)[3], align=studio.layout(layout, sw)[4], pace=float(opts["pace"]) if "pace" in opts else None) if caption_text else None
    focus_row = float(opts["focus"].split(">")[0]) * ph / 2520 if "focus" in opts else None
    for n in range(frames):
        shown = picture
        for (x, y, at) in taps:
            age = n / FPS - at
            if 0 <= age < 0.6:
                if shown is picture:
                    shown = picture.copy()
                ripple(shown, (x, y), age / 0.6, look["ripple"], scale)
        screen0 = crop_screen(shown) if ph / pw > 1.8 else shown
        if H > W and layout == "slice":
            screen = slice_view(screen0, raw_row_to_screen(focus_row, screen0.shape[0]) if focus_row is not None else screen0.shape[0] * 0.4)
        else:
            screen = zoomed(screen0, zoom, n / max(1, frames - 1)) if H <= W else screen0
        t = start_t + n / FPS
        yield studio.compose(t, n, screen, layout, cap, tone, n / FPS, n / FPS + float(opts.get("capage", 0)), roll_mod=-4 * (1 - ease(n / FPS / 1.2)))


# ---------------------------------------------------------------- transitions (they work on the last and first few frames around a cut)

def lerp(a, b, k):
    return (a.astype(np.float32) * (1 - k) + b.astype(np.float32) * k).astype(np.uint8)


def transition_frames(kind, tail, head, look):
    """tail: the last k frames of the scene that ends, head: the first k frames of the scene that begins. Returns 2k frames (the scene that ends
    plays on for the first k, frozen on its last picture after that; the new one starts frozen on its first picture and plays for the last k),
    so the length of the video does not change."""
    k = len(head)
    out = []
    for i in range(2 * k):
        p = (i + 1) / (2 * k + 1)
        a = tail[i] if i < k else tail[-1]
        b = head[i - k] if i >= k else head[0]
        if kind == "flash":
            if p < 0.5:
                out.append(a)
                continue
            amount = (1 - (p - 0.5) * 2) ** 1.6
            out.append(lerp(b, np.full_like(b, look["flash"] if look["flash"] != look["bg"] else (255, 255, 255)), amount * 0.9))
        elif kind == "dip":
            plain = np.full_like(a, look["bg"])
            out.append(lerp(a, plain, p * 2) if p < 0.5 else lerp(plain, b, (p - 0.5) * 2))
        elif kind == "whip":
            e = ease_io(p)
            canvas = np.concatenate([a, b], axis=1)
            x0 = int(e * W)
            frame = canvas[:, x0: x0 + W]
            size = max(1, int(abs(math.sin(math.pi * p)) * 90)) | 1
            out.append(cv2.blur(frame, (size, 1)))
        elif kind == "zoom":
            if p < 0.5:
                s = 1 + 0.6 * ease(p * 2)
                m = cv2.getRotationMatrix2D((W / 2, H / 2), 0, s)
                warped = cv2.warpAffine(a, m, (W, H), flags=cv2.INTER_LINEAR)
                out.append(cv2.blur(warped, (1 + int(40 * p), 1 + int(40 * p))))
            else:
                s = 1.5 - 0.5 * ease((p - 0.5) * 2)
                m = cv2.getRotationMatrix2D((W / 2, H / 2), 0, s)
                out.append(cv2.warpAffine(b, m, (W, H), flags=cv2.INTER_LINEAR))
        elif kind == "glitch":
            src = a if p < 0.5 else b
            frame = src.copy()
            shift = int(30 * math.sin(i * 7.3 + 1) + 8)
            frame[:, :, 2] = np.roll(src[:, :, 2], shift, axis=1)
            frame[:, :, 0] = np.roll(src[:, :, 0], -shift, axis=1)
            rng = np.random.default_rng(i * 31 + 5)
            for _ in range(5):
                y0 = int(rng.integers(0, H - 60))
                h = int(rng.integers(8, 60))
                frame[y0:y0 + h] = np.roll(frame[y0:y0 + h], int(rng.integers(-120, 120)), axis=1)
            out.append(frame)
        elif kind == "wipe":
            edge = int(ease_io(p) * (W + 400)) - 200
            mask = np.clip((np.arange(W)[None, :, None] - (edge - np.arange(H)[:, None, None] * 0.35)) / -60.0, 0, 1).astype(np.float32)
            out.append((a.astype(np.float32) * (1 - mask) + b.astype(np.float32) * mask).astype(np.uint8))
        elif kind == "dissolve":
            out.append(lerp(a, b, ease_io(p)))
        else:
            out.append(b)
    return out


TRANSITION_FRAMES = {"flash": 4, "dip": 6, "whip": 5, "zoom": 5, "glitch": 4, "wipe": 6, "dissolve": 7, "cut": 0, "pop": 0, "hit": 0}      # half the length of the transition, in frames


# ---------------------------------------------------------------- sound effects

def tone(freq, seconds, decay):
    t = np.arange(int(seconds * SR)) / SR
    return np.sin(2 * np.pi * freq * t) * np.exp(-decay * t)


def sfx_click(rng):
    t = np.arange(int(0.05 * SR)) / SR
    noise = rng.standard_normal(len(t))
    noise = noise - np.concatenate(([0], noise[:-1])) * 0.95
    return noise * np.exp(-90 * t) * 0.5 + tone(1800, 0.05, 80) * 0.25


def sfx_pop(rng):
    t = np.arange(int(0.16 * SR)) / SR
    return np.sin(2 * np.pi * (520 * np.exp(-14 * t) + 160) * t) * np.exp(-22 * t)


def sfx_bloop(rng):
    t = np.arange(int(0.22 * SR)) / SR
    return np.sin(2 * np.pi * (330 + 780 * (1 - np.exp(-18 * t))) * t) * np.exp(-12 * t) * 0.8


def sfx_chime(rng):
    t = np.arange(int(1.1 * SR)) / SR
    notes = (784, 1175, 1568, 2349)
    return sum(np.sin(2 * np.pi * f * t) * np.exp(-(3.5 + i) * t) * (0.5 / (i + 1)) for i, f in enumerate(notes)) * 0.5


def sfx_soft(rng):
    t = np.arange(int(0.09 * SR)) / SR
    return np.sin(2 * np.pi * 900 * t) * np.exp(-55 * t) * 0.35 + rng.standard_normal(len(t)) * np.exp(-120 * t) * 0.05


def whoosh(rng, seconds=0.7):
    n = int(seconds * SR)
    noise = rng.standard_normal(n)
    out, state = np.zeros(n), 0.0
    for i in range(n):
        state += (noise[i] - state) * (0.015 + 0.42 * math.sin(math.pi * i / n) ** 2)
        out[i] = state
    return out * np.sin(math.pi * np.arange(n) / n) ** 1.6 * 3.0


def boom(rng, seconds=2.4):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    low = np.sin(2 * np.pi * (34 + 78 * np.exp(-9 * t)) * t) * np.exp(-2.1 * t)
    burst = rng.standard_normal(n) * np.exp(-14 * t) * 0.45
    tail = rng.standard_normal(n) * np.exp(-2.6 * t) * 0.07
    return (low + burst + tail) * 0.95


def thud(rng):
    return boom(rng, 1.1) * 0.9


def riser(rng, seconds=2.0):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    noise = rng.standard_normal(n)
    out, state = np.zeros(n), 0.0
    for i in range(n):
        state += (noise[i] - state) * (0.02 + 0.5 * (i / n) ** 2)
        out[i] = state
    tone_ = np.sin(2 * np.pi * (180 + 900 * (t / seconds) ** 2) * t) * 0.25
    return (out * 3 + tone_) * (t / seconds) ** 2.2 * 0.5


def sfx_glitch(rng):
    n = int(0.25 * SR)
    t = np.arange(n) / SR
    gate = (np.sin(2 * np.pi * 38 * t) > 0).astype(float)
    return rng.standard_normal(n) * gate * np.exp(-9 * t) * 0.5


SFX = {"click": sfx_click, "pop": sfx_pop, "bloop": sfx_bloop, "chime": sfx_chime, "soft": sfx_soft, "whoosh": whoosh, "boom": boom, "thud": thud, "riser": riser, "glitch": sfx_glitch}


def sfx_file(seconds, hits):
    rng = np.random.default_rng(5)
    track = np.zeros((2, int((seconds + 3) * SR)))
    for at, kind, gain in hits:
        sound = SFX[kind](rng)
        start = max(0, int(at * SR))
        sound = sound[: track.shape[1] - start]
        if len(sound) == 0:
            continue
        pan = 0.5 + 0.2 * math.sin(at * 3.1)
        track[0, start: start + len(sound)] += sound * gain * (1 - pan) ** 0.5 * 1.4
        track[1, start: start + len(sound)] += sound * gain * pan ** 0.5 * 1.4
    track = track[:, : int(seconds * SR)]
    track *= 0.95 / max(0.95, np.abs(track).max())
    handle = tempfile.NamedTemporaryFile(suffix=".wav", delete=False)
    with wave.open(handle, "wb") as out:
        out.setnchannels(2)
        out.setsampwidth(2)
        out.setframerate(SR)
        out.writeframes((track.T * 32767).astype("<i2").tobytes())
    return handle.name


# ---------------------------------------------------------------- the spec and the join

def parse_options(parts):
    opts = {}
    for part in parts:
        if "=" in part:
            k, _, v = part.partition("=")
            opts[k.strip()] = v.strip()
    return opts


HEX_KEYS = ("bg", "ink", "accent", "dim", "frame", "flash", "ripple", "lit", "lit_outline", "outline", "title_lit")
FLOAT_KEYS = ("track", "shadow", "pulse", "flash_amount", "vignette", "tilt", "pace")
INT_KEYS = ("grain", "caption_size", "title_size")


def register_looks(header):
    """Adds the looks a spec defines with newlook|name|base=stock|key=value|... to LOOKS (a stock look is copied and the keys given change it). Returns a list of problems."""
    problems = []
    for part in header.get("newlooks", []):
        name, pairs = part[0], dict(item.split("=", 1) for item in part[1:] if "=" in item)
        base = pairs.pop("base", "bright")
        if base not in LOOKS:
            problems.append("newlook %s: unknown base %s" % (name, base))
            continue
        look = dict(LOOKS[base])
        look.pop("outline", None)
        look.pop("lit_outline", None)
        look.pop("title_lit", None)
        for key, value in pairs.items():
            try:
                if key in HEX_KEYS:
                    look[key] = bgr(value)
                elif key in FLOAT_KEYS:
                    look[key] = float(value)
                elif key in INT_KEYS:
                    look[key] = int(value)
                elif key == "upper":
                    look[key] = value in ("1", "true", "yes")
                elif key == "font":
                    look["caption_font"] = ("bricolage", value.split(":", 1)[1]) if value.startswith("bricolage") else (value, None)
                elif key == "caption":
                    look["caption_style"] = value
                elif key in ("layouts",):
                    look[key] = value.split(",")
                elif key == "tones":
                    look[key] = [bgr(v) for v in value.split(",")]
                elif key in ("phone", "tap", "cut", "backdrop"):
                    look[key] = value
                else:
                    problems.append("newlook %s: unknown key %s" % (name, key))
            except (ValueError, IndexError):
                problems.append("newlook %s: bad value for %s: %s" % (name, key, value))
        if look.get("backdrop") not in (None,) + GENERIC and look.get("backdrop") not in LOOKS:
            problems.append("newlook %s: unknown backdrop %s" % (name, look.get("backdrop")))
        if look["phone"] not in ("thin", "solid", "soft"):
            problems.append("newlook %s: phone must be thin, solid or soft" % name)
        if look["caption_style"] not in ("stack", "sticker", "bubble", "typed"):
            problems.append("newlook %s: caption must be stack, sticker, bubble or typed" % name)
        if look["tap"] not in (None, "soft", "pop", "bloop", "chime", "click"):
            problems.append("newlook %s: unknown tap sound %s" % (name, look["tap"]))
        if look.get("backdrop") in ("bright", "play") or ("tones" not in look and base in ("bright", "play")):
            look.setdefault("tones", [look["accent"], look["dim"], look["ink"]])
        if look.get("backdrop") in LOOKS and look.get("backdrop") not in GENERIC and not problems:
            pass
        LOOKS[name] = look
    return problems


def parse(spec):
    scenes, header = [], {"look": "noir", "music": None, "bpm": 0.0, "format": "wide", "start": 0.0}
    for line in spec.read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        part = [p.strip() for p in line.split("|")]
        kind = part[0]
        if kind == "newlook":
            header.setdefault("newlooks", []).append(part[1:])
        elif kind == "look":
            header["look"] = part[1]
        elif kind == "format":
            header["format"] = part[1]
        elif kind == "music":
            header["music"] = (part[1], float(part[2]) if len(part) > 2 else 0.6)
            header["start"] = float(part[4]) if len(part) > 4 else 0.0
            header["bpm"] = float(part[3]) if len(part) > 3 else 0.0
        elif kind == "title":
            scenes.append(dict(kind="title", text=part[1], sub=part[2] if len(part) > 2 else "", dur=part[3], opts=parse_options(part[4:])))
        elif kind == "clip":
            opts = parse_options(part[5:])
            scenes.append(dict(kind="clip", file=part[1], start=float(part[2]), end=float(part[3]), text=part[4], opts=opts))
        elif kind == "still":
            scenes.append(dict(kind="still", file=part[1], text=part[2], dur=part[3], opts=parse_options(part[4:])))
        else:
            sys.exit("unknown scene: " + line)
    return scenes, header


def zoom_of(opts):
    if "zoom" not in opts:
        return None
    v = [float(x) for x in opts["zoom"].split(",")]
    return (v[0], v[1], v[2], v[3] if len(v) > 3 else v[2])


def frames_of(scene, bpm, touches=None):
    opts = scene["opts"]
    if "dur" in opts:
        return max(2, int(round(beats_to_seconds(opts["dur"], bpm) * FPS)))
    if scene["kind"] in ("title", "still"):
        return max(2, int(round(beats_to_seconds(scene["dur"], bpm) * FPS)))
    speed = float(opts["speed"]) if opts.get("speed", "auto") != "auto" else 1.0
    return max(2, int(round((scene["end"] - scene["start"]) / speed * FPS)))


def main():
    global W, H, PACE_MUL
    spec, folder, target = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), pathlib.Path(sys.argv[3])
    target.parent.mkdir(parents=True, exist_ok=True)
    scenes, header = parse(spec)
    short = header["format"] == "short"
    if short:
        W, H, PACE_MUL = 1080, 1920, 0.6
    blocked = folder / "blocked.txt"
    if blocked.exists():                                   # stretches of the recordings that must never be shown (see blocked.txt)
        for line in blocked.read_text(encoding="utf-8").splitlines():
            part = [x.strip() for x in line.split("|")]
            if len(part) >= 3 and not line.startswith("#"):
                for scene in scenes:
                    if scene["kind"] == "clip" and scene["file"] == part[0] and scene["start"] < float(part[2]) and scene["end"] > float(part[1]):
                        sys.exit("refused: %s %.1f to %.1f overlaps a blocked stretch (%s to %s: %s)" % (scene["file"], scene["start"], scene["end"], part[1], part[2], part[3] if len(part) > 3 else ""))
    for problem in register_looks(header):
        sys.exit("refused: " + problem)
    studio = Studio(header["look"], header["bpm"])
    look = studio.look
    bpm = header["bpm"]
    counts = [frames_of(s, bpm) for s in scenes]
    starts = np.cumsum([0] + counts[:-1]) / FPS
    seconds = sum(counts) / FPS

    hits = []
    tap_gain = {"soft": 0.35, "pop": 0.5, "chime": 0.45, "bloop": 0.5}.get(look["tap"], 0.4)
    for index, scene in enumerate(scenes):
        trans = scene["opts"].get("trans", "cut" if index else "cut")
        at = float(starts[index])
        if index and trans in ("whip", "zoom", "wipe"):
            hits.append((at - 0.12, "whoosh", 0.5))
        if index and trans in ("flash", "pop", "hit"):
            hits.append((at, "thud" if look["cut"] == "hit" else "pop", 0.55 if trans == "flash" else 0.35))
        if index and trans == "glitch":
            hits.append((at, "glitch", 0.5))
        if index and trans == "dip":
            hits.append((at - 0.25, "whoosh", 0.3))
        if scene["kind"] == "title":
            hits.append((at + 0.1, "thud" if look["cut"] == "hit" else "pop", 0.5))
        if scene["kind"] == "still":
            for item in scene["opts"].get("tap", "").split(";"):
                if item.strip():
                    hits.append((at + float(item.split(",")[2]), look["tap"], tap_gain))
        if scene["kind"] == "still" and scene["opts"].get("boom"):
            hits.append((at - 1.6, "riser", 0.6))
            hits.append((at + 0.05, "boom", 0.9))
        if scene["kind"] == "clip" and look["tap"]:
            path = folder / scene["file"]
            touches = load_touches(path)
            frames = counts[index]
            opts = scene["opts"]
            speed = None if opts.get("speed", "auto") == "auto" else float(opts["speed"])
            ramp = tuple(float(x) for x in opts["ramp"].split(">")) if "ramp" in opts else None
            times = source_times(touches, scene["start"], scene["end"], frames, "auto" if (speed is None and "dur" in opts) else speed, ramp)
            for touch in touches:
                if touch["kind"] == "tap" and scene["start"] <= touch["t"] - LATENCY <= scene["end"]:
                    n = int(np.searchsorted(times, touch["t"] - LATENCY))
                    hits.append((at + n / FPS, look["tap"] if look["tap"] != "chime" else "chime", tap_gain))
    if scenes and scenes[-1]["kind"] == "title" and look["cut"] != "soft":
        hits.append((float(starts[-1]) - 1.5, "riser", 0.5))
    sfx = sfx_file(seconds, hits)

    command = [str(FFMPEG), "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "bgr24", "-s", "%dx%d" % (W, H), "-r", str(FPS), "-i", "-"]
    if header["music"]:
        command += ["-stream_loop", "-1", "-ss", "%.2f" % header["start"], "-i", str(MUSIC / header["music"][0]), "-i", sfx]
        command += ["-filter_complex", "[1:a]volume=%.2f,afade=t=in:d=%.1f,afade=t=out:st=%.2f:d=%.1f[m];[2:a]volume=0.7[s];[m][s]amix=inputs=2:duration=longest:normalize=0,alimiter=limit=0.9[a]" % (header["music"][1], 0.3 if short else 1.2, max(0, seconds - (1.2 if short else 2.2)), 1.2 if short else 2.2), "-map", "0:v", "-map", "[a]"]
    command += ["-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p", "-movflags", "+faststart"]
    if header["music"]:
        command += ["-c:a", "aac", "-b:a", "192k", "-t", "%.2f" % seconds]
    command += [str(target)]
    sink = subprocess.Popen(command, stdin=subprocess.PIPE)
    emitted = 0
    black = np.zeros((H, W, 3), np.uint8)

    def emit(frame):
        nonlocal emitted
        out = studio.finish(frame)
        if emitted < 10:
            out = (out.astype(np.float32) * (emitted + 1) / 10).astype(np.uint8)
        sink.stdin.write(np.ascontiguousarray(out).tobytes())
        emitted += 1

    pending = collections.deque()
    HOLD = 12
    for index, scene in enumerate(scenes):
        opts = scene["opts"]
        start_t = float(starts[index])
        tone_index = int(opts["tone"]) if "tone" in opts else index
        layout = opts.get("layout", look["layouts"][index % len(look["layouts"])])
        if short:
            layout = "tall" if layout == "tall" else "slice"
        zoom = zoom_of(opts)
        if scene["kind"] == "title":
            frames = title_scene(studio, scene["text"], scene["sub"], counts[index], start_t, opts)
        elif scene["kind"] == "still":
            frames = still_scene(studio, folder / scene["file"], scene["text"], counts[index], zoom, layout, tone_index, start_t, opts)
        else:
            speed = None if opts.get("speed", "auto") == "auto" else float(opts["speed"])
            ramp = tuple(float(x) for x in opts["ramp"].split(">")) if "ramp" in opts else None
            frames = clip_scene(studio, folder / scene["file"], scene["start"], scene["end"], scene["text"], counts[index], "auto" if (speed is None and "dur" in opts) else speed, ramp, zoom, layout, tone_index, start_t, opts)
        trans = opts.get("trans", "cut")
        k = TRANSITION_FRAMES.get(trans, 0) if index else 0
        if k and len(pending) >= k:
            head = [next(frames) for _ in range(k)]
            tail = [pending.pop() for _ in range(k)][::-1]
            while pending:
                emit(pending.popleft())
            for out in transition_frames(trans, tail, head, look):
                emit(out)
        else:
            k = 0
        for n, frame in enumerate(frames, start=k):
            if trans in ("pop", "hit") and n < 6 and index:
                m = cv2.getRotationMatrix2D((W / 2, H / 2), 0, 1 + 0.07 * (1 - n / 6) ** 2)
                frame = cv2.warpAffine(frame, m, (W, H), flags=cv2.INTER_LINEAR)
            pending.append(frame)
            while len(pending) > HOLD:
                emit(pending.popleft())
    rest = list(pending)
    tail_n = int(0.9 * FPS)
    for n, frame in enumerate(rest):
        left = len(rest) - n
        emit((frame.astype(np.float32) * (left / tail_n)).astype(np.uint8) if left < tail_n else frame)
    sink.stdin.close()
    sink.wait()
    pathlib.Path(sfx).unlink(missing_ok=True)
    print("wrote %s: %.1f s, %.1f MB, look %s, music %s, %d sounds" % (target, emitted / FPS, target.stat().st_size / 1e6, header["look"], header["music"][0] if header["music"] else "none", len(hits)))


if __name__ == "__main__":
    main()
