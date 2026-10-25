#!/usr/bin/env python3
"""Check the packaged binary manifest, not a source manifest or the artifact filename."""
import argparse
import os
from pathlib import Path
import subprocess
import re
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('apk', type=Path)
parser.add_argument('--output', type=Path)
args = parser.parse_args()
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
candidates = list((sdk / 'cmdline-tools').glob('*/bin/apkanalyzer'))
assert candidates, 'Android SDK apkanalyzer is required'
text = subprocess.check_output([str(sorted(candidates)[-1]), 'manifest', 'print', str(args.apk)], text=True)
root = ET.fromstring(text)
a = '{http://schemas.android.com/apk/res/android}'
assert root.get('package') == 'dev.prestwich.autv', 'Wrong package: refuse to publish a Lab APK'
app = root.find('application')
assert app is not None and app.get(a+'label') == 'AUTV', 'Wrong application name'
launchers = []
for node in list(app.findall('activity')) + list(app.findall('activity-alias')):
    if node.get(a+'enabled', 'true') == 'false':
        continue
    for f in node.findall('intent-filter'):
        actions = {x.get(a+'name') for x in f.findall('action')}
        categories = {x.get(a+'name') for x in f.findall('category')}
        if 'android.intent.action.MAIN' in actions:
            for category in ('android.intent.category.LAUNCHER', 'android.intent.category.LEANBACK_LAUNCHER'):
                if category in categories:
                    launchers.append((node.get(a+'name'), category))
assert {name for name, _ in launchers} == {'dev.prestwich.autv.MainActivity'}, f'Wrong launchers: {launchers}'
assert {category for _, category in launchers} == {'android.intent.category.LAUNCHER','android.intent.category.LEANBACK_LAUNCHER'}, 'Phone/TV launcher missing'
assert app.get(a+'debuggable') == 'true', 'Expected debug/test signing build'
summary = 'Application: AUTV\nPackage: dev.prestwich.autv\nLauncher: dev.prestwich.autv.MainActivity\nPhone and Android TV launchers: PASS\nBuild: debug; not a permanent distribution update\n'
signer = sorted((sdk / 'build-tools').glob('*/apksigner'))[-1]
certificate = subprocess.check_output([str(signer), 'verify', '--print-certs', str(args.apk)], text=True)
match = re.search(r'certificate SHA-256 digest: ([0-9a-fA-F]+)', certificate)
assert match, 'APK debug certificate could not be verified'
summary += 'Signing certificate SHA-256: ' + match.group(1) + '\n'
if args.output:
    args.output.write_text(summary + '\n' + text)
print(summary)
