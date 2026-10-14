"""Checks the Play listing text against Play's character limits. Run from the project root: python -I tools/store-check.py"""
EM, EN = chr(0x2014), chr(0x2013)  # built from code points so this file never contains a dash itself
import re
import sys

text = open('store/listing.txt', encoding='utf-8').read()


def block(header):
    m = re.search(re.escape(header) + r'[^\n]*\n(.*?)\n\n(?=[A-Z][A-Z /]+(?:\(|\n)|\Z)', text, re.S)
    return m.group(1).strip() if m else ''


limits = [('APP NAME', 30), ('SHORT DESCRIPTION', 80), ('FULL DESCRIPTION', 4000), ("WHAT'S NEW", 500)]
bad = False
for name, limit in limits:
    # The full description runs until the TAGS heading because it contains blank lines.
    if name == 'FULL DESCRIPTION':
        m = re.search(r'FULL DESCRIPTION[^\n]*\n(.*?)\nTAGS / CATEGORY', text, re.S)
        value = m.group(1).strip() if m else ''
    elif name == "WHAT'S NEW":
        m = re.search(r"WHAT'S NEW[^\n]*\n(.*?)\n\nGRAPHICS", text, re.S)
        value = m.group(1).strip() if m else ''
    else:
        value = block(name)
    ok = 0 < len(value) <= limit
    bad = bad or not ok
    print(f'{name:18} {len(value):5} / {limit:<5} {"ok" if ok else "OVER OR MISSING"}')
if EM in text or EN in text:
    print('listing contains an em or en dash')
    bad = True
sys.exit(1 if bad else 0)
