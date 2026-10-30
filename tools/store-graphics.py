"""Draws the Google Play graphics: the 512 px icon, the 1024 x 500 feature graphic, and phone screenshots in a size Play accepts.

Run after the screenshot tests (app\\build\\shots exists):   python tools\\store-graphics.py
Output goes to store\\graphics\\. The mark is drawn on the same 108 point grid as the app icon.
"""
import glob
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'store', 'graphics')
os.makedirs(OUT, exist_ok=True)

PAPER = (13, 11, 31)
INK = (247, 244, 255)
SOFT = (207, 200, 240)
GLOW_A = (155, 134, 255)
GLOW_B = (255, 138, 112)
SS = 4  # supersampling


def mix(a, b, f):
    return tuple(int(a[i] + (b[i] - a[i]) * f) for i in range(3))


def vgradient(w, h, top, bottom):
    img = Image.new('RGB', (w, h))
    px = img.load()
    for y in range(h):
        c = mix(top, bottom, y / max(h - 1, 1))
        for x in range(w):
            px[x, y] = c
    return img


def background(w, h):
    """The app's night colour with the two soft glows of the intro."""
    base = Image.new('RGB', (w, h), PAPER)
    glow = Image.new('RGB', (w, h), PAPER)
    g = ImageDraw.Draw(glow)
    g.ellipse([w * 0.18, -h * 0.35, w * 0.82, h * 0.65], fill=mix(PAPER, GLOW_A, 0.34))
    g.ellipse([w * 0.62, h * 0.45, w * 1.12, h * 1.15], fill=mix(PAPER, GLOW_B, 0.2))
    glow = glow.filter(ImageFilter.GaussianBlur(max(w, h) * 0.12))
    return Image.blend(base, glow, 1.0)


def mark(size):
    """The H-sib mark on a transparent square of [size] pixels."""
    s = size * SS
    u = s / 108.0
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))

    def rrect(x0, y0, x1, y1, fill_img=None, fill=None):
        layer = Image.new('RGBA', (s, s), (0, 0, 0, 0))
        mask = Image.new('L', (s, s), 0)
        ImageDraw.Draw(mask).rounded_rectangle([x0 * u, y0 * u, x1 * u, y1 * u], radius=4 * u, fill=255)
        src = fill_img if fill_img is not None else Image.new('RGB', (s, s), fill)
        layer.paste(src, (0, 0), mask)
        img.alpha_composite(layer)

    rrect(34, 36, 42, 76, fill=INK)
    rrect(66, 36, 74, 76, fill_img=vgradient(s, s, GLOW_B, GLOW_A).crop((0, 0, s, s)))
    d = ImageDraw.Draw(img)
    for (x, y, r, f) in [(49.2, 57, 2.2, 0.0), (54.8, 52.5, 2.8, 0.5), (60.9, 48.8, 3.4, 1.0)]:
        d.ellipse([(x - r) * u, (y - r) * u, (x + r) * u, (y + r) * u], fill=mix(GLOW_A, GLOW_B, f))
    d.ellipse([(70 - 5) * u, (23.5 - 5) * u, (70 + 5) * u, (23.5 + 5) * u], fill=INK)
    return img.resize((size, size), Image.LANCZOS)


def font(px, weight):
    path = os.path.join(ROOT, 'app', 'src', 'main', 'res', 'font', 'sora.ttf')
    f = ImageFont.truetype(path, px)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


# ---- icon 512 ------------------------------------------------------------------------------------------------------
icon = background(512, 512).convert('RGBA')
m = mark(380)
icon.alpha_composite(m, (66, 78))
icon.convert('RGB').save(os.path.join(OUT, 'icon-512.png'))

# ---- feature graphic 1024 x 500 -------------------------------------------------------------------------------------
fg = background(1024, 500).convert('RGBA')
fg.alpha_composite(mark(330), (70, 85))
d = ImageDraw.Draw(fg)
# The name at one size: "sib" (Start It Before) in the signature colours, "H-" and "Reminder" faded beside it, one baseline.
base = 200
fade = mix(PAPER, INK, 0.5)
f_h = font(64, 500)
f_sib = font(64, 800)
f_rem = font(64, 500)
d.text((440, base), 'H-', font=f_h, fill=fade, anchor='ls')
x_sib = 440 + f_h.getlength('H-')
mask = Image.new('L', fg.size, 0)
ImageDraw.Draw(mask).text((x_sib, base), 'sib', font=f_sib, fill=255, anchor='ls')
w_sib = int(f_sib.getlength('sib'))
grad = Image.new('RGB', fg.size)
gd = ImageDraw.Draw(grad)
for gx in range(int(x_sib), int(x_sib) + w_sib + 1):
    gd.line([(gx, 0), (gx, fg.height)], fill=mix(GLOW_A, GLOW_B, (gx - x_sib) / max(w_sib, 1)))
fg.paste(grad, (0, 0), mask)
d = ImageDraw.Draw(fg)
d.text((x_sib + w_sib + 20, base), 'Reminder', font=f_rem, fill=fade, anchor='ls')
d.text((444, 244), 'Start it before.', font=font(40, 600), fill=GLOW_B)
d.text((444, 312), 'Finds your deadlines. Plans when to start.', font=font(25, 400), fill=SOFT)
d.text((444, 350), 'Private by design.', font=font(25, 400), fill=SOFT)
fg.convert('RGB').save(os.path.join(OUT, 'feature-1024x500.png'))

# ---- screenshots: each full screen on the night colour, 1080 x 2160 (the tallest shape Play accepts) ----------------------
shots = os.path.join(ROOT, 'app', 'build', 'shots')
wanted = ['today-dark', 'review-dark', 'plan-dark', 'detail-dark', 'chat-dark', 'close-home-dark', 'settings-home-dark']
n = 0
for name in wanted:
    hits = [p for p in glob.glob(os.path.join(shots, '*.png')) if os.path.basename(p).startswith(name.split('-')[0]) and p.endswith('-dark.png')]
    exact = os.path.join(shots, name + '.png')
    src = exact if os.path.exists(exact) else (sorted(hits)[0] if hits else None)
    if not src:
        print('no screenshot for', name, file=sys.stderr)
        continue
    n += 1
    shot = Image.open(src).convert('RGB')
    scale = 2160 / shot.height
    shot = shot.resize((int(shot.width * scale), 2160), Image.LANCZOS)
    canvas = background(1080, 2160)
    canvas.paste(shot, ((1080 - shot.width) // 2, 0))
    canvas.save(os.path.join(OUT, f'screenshot-{n}-{name.replace("-dark", "")}.png'))
print('wrote', len(os.listdir(OUT)), 'files to', OUT)
