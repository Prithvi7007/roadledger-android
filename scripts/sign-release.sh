#!/usr/bin/env bash
# Sign an already-built unsigned RELEASE APK. Secrets only in root-owned files.
set -euo pipefail
if (( $# != 2 )); then echo 'Usage: sign-release.sh unsigned.apk signed.apk' >&2; exit 2; fi
unsigned="$1"; signed="$2"
: "${ROADLEDGER_KEYSTORE:?Set ROADLEDGER_KEYSTORE to a private PKCS12 keystore path}"
: "${ROADLEDGER_PASSWORD_FILE:?Set ROADLEDGER_PASSWORD_FILE to a 0400 password file path}"
: "${ROADLEDGER_KEY_ALIAS:=roadledger}"
test -s "$unsigned" && test -r "$ROADLEDGER_KEYSTORE" && test -r "$ROADLEDGER_PASSWORD_FILE"
command -v apksigner >/dev/null && command -v zipalign >/dev/null
outdir="$(dirname "$signed")"; mkdir -p "$outdir"
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
zipalign -f -p 4 "$unsigned" "$work/aligned.apk" >/dev/null
# No passwords passed on the command line or echoed; apksigner reads a private file.
apksigner sign --ks "$ROADLEDGER_KEYSTORE" --ks-type PKCS12 --ks-key-alias "$ROADLEDGER_KEY_ALIAS" \
  --ks-pass "file:$ROADLEDGER_PASSWORD_FILE" --key-pass "file:$ROADLEDGER_PASSWORD_FILE" \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
  --out "$work/signed.apk" "$work/aligned.apk"
apksigner verify --verbose --print-certs "$work/signed.apk"
install -m 0644 "$work/signed.apk" "$signed"
echo 'Signed release APK created; preserve your private key and verify upgrade before distributing.'
