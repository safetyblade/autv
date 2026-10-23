#!/usr/bin/env python3
"""Exercise signing continuity using disposable keys and a local mock GitHub CLI."""
import json
import os
from pathlib import Path
import subprocess
import tempfile

script = Path(__file__).resolve().with_name("setup-android-signing.sh")
with tempfile.TemporaryDirectory(prefix="autv-signing-test-") as temporary:
    root = Path(temporary)
    mock = root / "bin"
    mock.mkdir()
    gh = mock / "gh"
    gh.write_text('''#!/usr/bin/env python3
import json,os,sys
from pathlib import Path
root=Path(os.environ["AUTV_MOCK_REPO"])
a=sys.argv[1:]
if a[:2]==["auth","status"]: pass
elif a[:2]==["secret","list"]: print("AUTV_SIGNING_KEYSTORE_BASE64" if (root/"AUTV_SIGNING_KEYSTORE_BASE64").exists() else "")
elif a[:2]==["secret","set"]: (root/a[2]).write_bytes(sys.stdin.buffer.read())
elif a[:2]==["variable","list"]: print(json.dumps([{"name":"AUTV_SIGNING_CERT_SHA256","value":(root/"certificate").read_text()}] if (root/"certificate").exists() else []))
elif a[:2]==["variable","set"]: (root/"certificate").write_text(a[a.index("--body")+1])
else: raise SystemExit("Unexpected mock CLI call")
''')
    gh.chmod(0o700)
    remote = root / "remote"
    remote.mkdir()
    environment = dict(os.environ, PATH=str(mock) + os.pathsep + os.environ["PATH"], AUTV_MOCK_REPO=str(remote))
    def run(backup, succeeds):
        result = subprocess.run(["bash", str(script)], env=dict(environment, AUTV_SIGNING_BACKUP_DIR=str(backup)), capture_output=True)
        if (result.returncode == 0) != succeeds:
            raise AssertionError("Signing continuity check failed; fixture output deliberately suppressed")
    backup = root / "backup"
    run(backup, True)
    first = {p.name: p.read_bytes() for p in remote.iterdir()}
    run(backup, True)
    assert first == {p.name: p.read_bytes() for p in remote.iterdir()}, "Repeat setup rotated the identity"
    run(root / "missing-backup", False)
    other = root / "other"
    other.mkdir()
    password = other / "store-password"
    password.write_text("disposable-signing-fixture-only")
    subprocess.run(["keytool", "-genkeypair", "-keystore", str(other / "autv.jks"), "-storetype", "PKCS12", "-alias", "autv", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname", "CN=AUTV disposable fixture", "-storepass:file", str(password), "-keypass:file", str(password)], check=True, capture_output=True)
    run(other, False)
    assert first == {p.name: p.read_bytes() for p in remote.iterdir()}, "A wrong backup changed repository signing"
print("Signing setup: initial identity, repeat setup, missing backup and certificate mismatch passed (mock repository).")
