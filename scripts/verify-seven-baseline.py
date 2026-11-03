#!/usr/bin/env python3
"""Protect owner-accepted Seven implementation at ebbf7ad / PR #139."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
expected = json.loads((root / 'docs/providers/seven-working-baseline.json').read_text())
changed = [name for name, digest in expected.items()
           if not (root / name).is_file() or hashlib.sha256((root / name).read_bytes()).hexdigest() != digest]
if changed:
    raise SystemExit('Protected Seven baseline changed; separate owner approval required: ' + ', '.join(changed))
print(f'Seven baseline PASS: {len(expected)} source/test files unchanged from ebbf7ad')
