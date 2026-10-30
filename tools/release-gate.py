"""Release gate: do the words, the manifest and the code say the same thing? (APP_QUALITY_STANDARD.txt: P8, P10, lesson L14, U6, U20)

Reads the finished release manifest and compares it with the privacy page, the store texts, the What's new list and the
Gradle settings, then scans the manifest and the tracked files for the usual security mistakes. Prints only failures.
Exit code 1 if any check fails.   Usage: python -I tools/release-gate.py   (after a release build)
"""
import glob
import re
import subprocess
import sys
from pathlib import Path

fails = []


def fail(msg):
    fails.append(msg)
    print('FAIL', msg)


def read(p):
    return Path(p).read_text(encoding='utf-8', errors='replace')


def newest(pattern):
    files = glob.glob(pattern, recursive=True)
    return max(files, key=lambda f: Path(f).stat().st_mtime) if files else None


manifest_path = newest('app/build/intermediates/**/release/**/AndroidManifest.xml')
if not manifest_path:
    fail('no release manifest found: run a release build first')
    sys.exit(1)
manifest = read(manifest_path)
privacy = read('site/privacy.html')
strings = read('app/src/main/res/values/strings.xml')

# ---- permissions against the privacy page -----------------------------------------------------------------
perms = set(re.findall(r'<uses-permission[^>]*android:name="android\.permission\.(\w+)"', manifest))
# The finished APK is the authority on permissions: ask aapt2 what is really in it.
aapt = newest('.toolchain/android-sdk/build-tools/*/aapt2.exe')
apk = newest('app/build/outputs/apk/release/*.apk')
if aapt and apk:
    dumped = subprocess.run([aapt, 'dump', 'permissions', apk], capture_output=True, text=True).stdout
    perms = set(re.findall(r"name='android\.permission\.(\w+)'", dumped))
asks = {
    'POST_NOTIFICATIONS': 'Notifications', 'RECEIVE_BOOT_COMPLETED': 'boot completed', 'USE_BIOMETRIC': 'Biometric',
    'USE_FINGERPRINT': 'Biometric', 'WAKE_LOCK': 'Wake lock', 'INTERNET': 'Internet', 'ACCESS_NETWORK_STATE': 'network state',
}
for perm in sorted(perms):
    word = asks.get(perm)
    if word is None:
        fail(f'permission {perm} is in the manifest and nothing in this gate knows it: justify it in the privacy page, then add it here')
    elif word.lower() not in privacy.lower():
        fail(f'permission {perm} is in the manifest but the privacy page never mentions "{word}"')
online = 'INTERNET' in perms
says_offline = bool(re.search(r'(no|not request the) (permission to use the )?internet', privacy, re.I)) and 'Internet and network state' not in privacy
if online and says_offline:
    fail('the manifest has INTERNET but the privacy page says there is none (publish docs/drafts/privacy-policy-online.html with this build)')
if not online and 'Internet and network state' in privacy:
    fail('the privacy page lists an internet permission the manifest does not have')

# ---- the name, in every place ------------------------------------------------------------------------------
name = re.search(r'<string name="app_name">([^<]+)</string>', strings).group(1)
if name not in privacy:
    fail(f'the privacy page does not use the app name "{name}"')
listing = read('store/listing.txt')
if name.split()[0] not in listing:
    fail(f'store/listing.txt does not use the app name "{name}"')
for old in ('S-IB',):
    for f in ['site/privacy.html', 'store/listing.txt', 'app/src/main/res/values/strings.xml']:
        if old in read(f):
            fail(f'{f} still uses the old name {old}')

# ---- What's new has an entry for this release --------------------------------------------------------------
gradle = read('app/build.gradle.kts')
code = int(re.search(r'versionCode\s*=\s*(\d+)', gradle).group(1))
if not re.search(r'Entry\(\s*%d\s*,' % code, read('app/src/main/kotlin/com/hsib/sib/ui/WhatsNew.kt')):
    fail(f'WhatsNew.kt has no entry for versionCode {code}')

# ---- security scan of the manifest -------------------------------------------------------------------------
if 'android:debuggable="true"' in manifest:
    fail('the release manifest is debuggable')
if 'android:allowBackup="true"' in manifest:
    fail('the release manifest allows Android backup')
if 'android:usesCleartextTraffic="true"' in manifest:
    fail('the release manifest allows cleartext traffic')
expected_exported = {'MainActivity', 'ShareReceiverActivity', 'NextUpWidgetReceiver', 'NotificationReaderService', 'NextUpTileService',
                     # Firebase Authentication's own sign in redirect screens; they only accept their own library's links.
                     'GenericIdpActivity', 'RecaptchaActivity'}
for m in re.finditer(r'<(activity|service|receiver|provider)\b(.*?)>', manifest, re.S):
    body = m.group(2)
    if 'android:exported="true"' not in body:
        continue
    comp = re.search(r'android:name="([^"]+)"', body)
    short = comp.group(1).split('.')[-1] if comp else '?'
    if short not in expected_exported and 'android:permission' not in body:
        fail(f'exported {m.group(1)} {short} has no permission and is not on the expected list')

# ---- secrets in tracked files ------------------------------------------------------------------------------
tracked = subprocess.run(['git', 'ls-files'], capture_output=True, text=True).stdout.split()
bad = re.compile(r"""-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----|(storePassword|keyPassword)\s*=\s*["'][^"'$]{4,}["']|AIza[0-9A-Za-z_\-]{35}""")
for f in tracked:
    if f.startswith(('release/', 'app/build/')) or not f.endswith(('.kt', '.kts', '.xml', '.txt', '.json', '.properties', '.html', '.py', '.ps1', '.js')):
        continue
    if f == 'tools/release-gate.py':
        continue
    try:
        if bad.search(read(f)):
            fail(f'{f} looks like it holds a secret')
    except OSError:
        pass
if any(f.startswith('release/') for f in tracked) or any(f.endswith(('.p12', '.jks', '.keystore')) for f in tracked):
    fail('a signing key or the release folder is tracked by git')
if not Path('release/keystore.properties').exists():
    fail('release/keystore.properties is missing: the build is not signed with the upload key')

print(f'release gate: {len(fails)} failure(s); network permission {"present" if online else "absent"}')
sys.exit(1 if fails else 0)
