#!/usr/bin/env bash
# Run once on a trusted machine with JDK keytool, Python 3 and authenticated GitHub CLI.
set -euo pipefail
set +x
umask 077
repo="safetyblade/autv"
signing_dir="${AUTV_SIGNING_BACKUP_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/autv-signing}"
mkdir -p "$signing_dir"
chmod 700 "$signing_dir"
command -v keytool >/dev/null
command -v gh >/dev/null
command -v python3 >/dev/null
gh auth status >/dev/null
# A new machine or missing backup must never rotate an existing repository identity.
remote_key=$(gh secret list --repo "$repo" --json name --jq '.[] | select(.name == "AUTV_SIGNING_KEYSTORE_BASE64") | .name')
if [[ -n "$remote_key" && ! -f "$signing_dir/autv.jks" ]]; then
  echo 'Repository signing is already configured. Restore the original private signing backup; do not generate a replacement.' >&2
  exit 1
fi
# Never replace an existing distribution identity.
if [[ ! -f "$signing_dir/autv.jks" ]]; then
  if [[ -e "$signing_dir/store-password" ]]; then
    echo 'Incomplete previous setup; recover the existing signing backup before continuing.' >&2
    exit 1
  fi
  python3 -c 'import secrets; print(secrets.token_urlsafe(48))' > "$signing_dir/store-password"
  keytool -genkeypair -keystore "$signing_dir/autv.jks" -storetype PKCS12 \
    -alias autv -keyalg RSA -keysize 3072 -validity 10000 \
    -dname 'CN=AUTV Android Distribution' \
    -storepass:file "$signing_dir/store-password" -keypass:file "$signing_dir/store-password"
fi
[[ -s "$signing_dir/store-password" ]] || { echo 'Missing password backup; stop and recover it.' >&2; exit 1; }
keytool -list -keystore "$signing_dir/autv.jks" -alias autv -storepass:file "$signing_dir/store-password" >/dev/null
certificate=$(keytool -exportcert -keystore "$signing_dir/autv.jks" -alias autv -storepass:file "$signing_dir/store-password" |
  python3 -c 'import hashlib,sys; print(hashlib.sha256(sys.stdin.buffer.read()).hexdigest())')
# Public certificate identity is independently pinned so a wrong local backup cannot rotate signing.
variables=$(gh variable list --repo "$repo" --json name,value)
registered_certificate=$(printf '%s' "$variables" | python3 -c 'import json,sys; print(next((v["value"] for v in json.load(sys.stdin) if v["name"] == "AUTV_SIGNING_CERT_SHA256"), ""))')
if [[ -n "$registered_certificate" && "$registered_certificate" != "$certificate" ]]; then
  echo 'Signing certificate differs from the registered AUTV identity. Restore the original backup; no secrets were changed.' >&2
  exit 1
fi
python3 -c 'import base64,sys; print(base64.b64encode(open(sys.argv[1],"rb").read()).decode())' "$signing_dir/autv.jks" |
  gh secret set AUTV_SIGNING_KEYSTORE_BASE64 --repo "$repo"
gh secret set AUTV_SIGNING_STORE_PASSWORD --repo "$repo" < "$signing_dir/store-password"
gh secret set AUTV_SIGNING_KEY_PASSWORD --repo "$repo" < "$signing_dir/store-password"
gh variable set AUTV_SIGNING_CERT_SHA256 --repo "$repo" --body "$certificate"
echo "Signing configured. Keep an encrypted, private backup of: $signing_dir"
echo 'Never delete or regenerate this key. Run the Native app workflow for installable updates.'
