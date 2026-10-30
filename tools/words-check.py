"""Scans the words a person reads (string literals in the UI, notifications, store texts and the privacy page) for what the
maker bans (APP_QUALITY_STANDARD.txt, hard stop H5 and the stop list): exclamation marks, emoji, filler and cute words, and
text that trails off in dots. Prints only findings. Exit code 1 if there are any.   Usage: python -I tools/words-check.py
"""
import re
import sys
from pathlib import Path

ROOT = Path('.')
KOTLIN = [p for p in (ROOT / 'app/src/main/kotlin').rglob('*.kt') if '/social/' not in p.as_posix() or 'FakeBackend' not in p.name]
TEXTS = list((ROOT / 'store').glob('*.txt')) + [ROOT / 'site/privacy.html', ROOT / 'PROJECT_SUMMARY.txt']

BANNED_WORDS = re.compile(r"\b(oops|whoops|sorry|please|awesome|amazing|magical|seamless(ly)?|simply|easily|just|blazing|supercharge|unleash|delightful|journey|revolutionary|cutting[- ]edge)\b", re.I)
EMOJI = re.compile('[\U0001F300-\U0001FAFF☀-➿⭐✅❌]')
STRING = re.compile(r'"((?:[^"\\\n]|\\.)*)"')
CODEISH = re.compile(r'^[\w./:#@${}()\[\]<>=+*,;|&%-]*$')  # identifiers, paths, formats: not words a person reads


def is_text_a_person_reads(s: str) -> bool:
    if ' ' not in s.strip() and len(s) < 14:
        return False  # one short token, almost always a key or a label code
    if CODEISH.match(s) or '(?' in s:
        return False
    return any(ch.isalpha() for ch in s)


def scan_kotlin():
    out = []
    for p in KOTLIN:
        for n, line in enumerate(p.read_text(encoding='utf-8').splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith(('//', '*', '/*', 'import ', 'package ')):
                continue
            for m in STRING.finditer(line):
                s = m.group(1)
                if not is_text_a_person_reads(s):
                    continue
                where = f'{p.as_posix()}:{n}'
                if '!' in s.replace('!=', '').replace('!!', ''):
                    out.append((where, 'exclamation mark', s))
                if EMOJI.search(s):
                    out.append((where, 'emoji', s))
                w = BANNED_WORDS.search(s)
                if w:
                    out.append((where, f'banned word "{w.group(0)}"', s))
                if s.rstrip().endswith('...') or s.rstrip().endswith('…'):
                    out.append((where, 'trails off in dots', s))
    return out


def scan_texts():
    out = []
    for p in TEXTS:
        if not p.exists():
            continue
        for n, line in enumerate(p.read_text(encoding='utf-8').splitlines(), 1):
            where = f'{p.as_posix()}:{n}'
            if re.search(r'\w!(\s|$)', line) and '<!' not in line:
                out.append((where, 'exclamation mark', line.strip()[:100]))
            if EMOJI.search(line):
                out.append((where, 'emoji', line.strip()[:100]))
            w = BANNED_WORDS.search(line)
            if w:
                out.append((where, f'banned word "{w.group(0)}"', line.strip()[:100]))
    return out


def main():
    found = scan_kotlin() + scan_texts()
    for where, why, text in found:
        print(f'{where}: {why}: {text[:90]}')
    print(f'words: {len(found)} findings')
    sys.exit(1 if found else 0)


main()
