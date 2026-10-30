"""Checks the colour contrast of every theme against the maker's numbers (APP_QUALITY_STANDARD.txt, section 14).

  7 to 1   body and secondary text (ink and inkSoft on paper, raised and lifted)
  4.5 to 1 other text and buttons (accent colours on paper and raised, text on its own wash, text on the action fill)
  3 to 1   shapes (the action fill, and the accent colours, against paper)

Reads the palettes straight out of Theme.kt, so a changed colour is checked without anyone remembering to. Prints only
failures (and a one line summary). Exit code 1 if anything fails.   Usage: python -I tools/contrast-check.py [--all]
"""
import re
import sys

SRC = 'app/src/main/kotlin/com/hsib/sib/ui/Theme.kt'


def lum(h):
    def ch(v):
        v /= 255
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)


def ratio(a, b):
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def palettes(text):
    out = {}
    for m in re.finditer(r'private val (\w+) = SibColors\((.*?)\n\)', text, re.S):
        tokens = dict(re.findall(r'(\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)', m.group(2)))
        out[m.group(1)] = tokens
    return out


def checks(p):
    """(label, foreground, background, needed)"""
    c = []
    for fg in ('ink', 'inkSoft'):
        for bg in ('paper', 'raised', 'lifted'):
            c.append((f'{fg} on {bg}', fg, bg, 7.0))
    for fg in ('vermilion', 'ochre', 'verdigris', 'violet'):
        for bg in ('paper', 'raised'):
            c.append((f'{fg} on {bg}', fg, bg, 4.5))
    for name in ('vermilion', 'ochre', 'verdigris', 'violet'):
        c.append((f'{name} on {name}Wash', name, name + 'Wash', 4.5))
    c.append(('onAction on action', 'onAction', 'action', 4.5))
    c.append(('onGlow on glowA', 'onGlow', 'glowA', 4.5))
    c.append(('onGlow on glowB', 'onGlow', 'glowB', 4.5))
    c.append(('action against paper', 'action', 'paper', 3.0))
    c.append(('violet against paper', 'violet', 'paper', 3.0))
    return [x for x in c if x[1] in p and x[2] in p]


def main():
    show_all = '--all' in sys.argv
    pals = palettes(open(SRC, encoding='utf-8').read())
    bad = 0
    total = 0
    for name, p in pals.items():
        for label, fg, bg, need in checks(p):
            total += 1
            r = ratio(p[fg], p[bg])
            if r < need:
                bad += 1
                print(f'FAIL {name}: {label} is {r:.2f} to 1, needs {need:g}')
            elif show_all:
                print(f'ok   {name}: {label} {r:.2f}')
    print(f'contrast: {len(pals)} palettes, {total} pairs, {bad} below the floor')
    sys.exit(1 if bad else 0)


main()
