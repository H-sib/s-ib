"""Project hygiene scan: em/en dashes in text files and unused Kotlin imports. Run from the project root."""
EM, EN = chr(0x2014), chr(0x2013)  # built from code points so this file never contains a dash itself
import os
import re

skip = {'.toolchain', 'build', '.gradle', '.git', 'release'}
dashes, unused = [], []
for dp, dn, fn in os.walk('.'):
    dn[:] = [d for d in dn if d not in skip]
    for f in fn:
        p = os.path.join(dp, f).replace(os.sep, '/')
        if f.endswith(('.kt', '.kts', '.xml', '.txt', '.html', '.tsv', '.json', '.toml', '.properties', '.pro', '.ps1', '.py')):
            try:
                s = open(p, encoding='utf-8').read()
            except Exception:
                continue
            for i, line in enumerate(s.splitlines(), 1):
                if EM in line or EN in line:
                    dashes.append((p, i, line.strip()[:90]))
        if f.endswith('.kt'):
            s = open(p, encoding='utf-8').read()
            body = '\n'.join(l for l in s.splitlines() if not l.startswith('import '))
            for line in s.splitlines():
                m = re.match(r'import ([\w.]+?)(?: as (\w+))?$', line)
                if m:
                    name = m.group(2) or m.group(1).split('.')[-1]
                    if name != '*' and not re.search(r'\b' + re.escape(name) + r'\b', body):
                        unused.append((p, line))
print('DASHES:', len(dashes))
for d in dashes[:40]:
    print('  ', d)
print('UNUSED IMPORTS:', len(unused))
for p, line in unused:
    print('  ', p.split('/kotlin/')[-1], '|', line)
