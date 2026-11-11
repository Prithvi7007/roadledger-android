#!/usr/bin/env bash
set -euo pipefail
# CI-only, same ephemeral DEBUG key for both APKs. Never uses the private release key.
repo="$(pwd)"
base_commit='dac81cd48a3832fc476d2043aead63e2cf57fc98'
package='fun.cadacrew.roadledger'
build_gradle_backup="$(mktemp)"
cp app/build.gradle "$build_gradle_backup"
trap 'cp "$build_gradle_backup" app/build.gradle; rm -f "$build_gradle_backup"' EXIT

# Build the exact previous v0.3.1 build config using the same source (no runtime code changes in v0.3.2).
git show "$base_commit":app/build.gradle > app/build.gradle
gradle --no-daemon :app:assembleDebug
before="$(mktemp --suffix=-v031.apk)"
cp app/build/outputs/apk/debug/app-debug.apk "$before"
cp "$build_gradle_backup" app/build.gradle
gradle --no-daemon :app:assembleDebug
new="$(mktemp --suffix=-v032.apk)"
cp app/build/outputs/apk/debug/app-debug.apk "$new"

# Verify signing fingerprint identical. Android debug keys here are fresh and ephemeral; NOT release identity.
if ! command -v apksigner >/dev/null 2>&1; then
  export PATH="$ANDROID_HOME/build-tools/35.0.0:$PATH"
fi
command -v apksigner >/dev/null || { echo "Android apksigner not available" >&2; exit 1; }
old_cert="$(apksigner verify --print-certs "$before" | grep 'Signer #1 certificate SHA-256 digest:')"
new_cert="$(apksigner verify --print-certs "$new" | grep 'Signer #1 certificate SHA-256 digest:')"
test -n "$old_cert" && test "$old_cert" = "$new_cert"
echo "CI test-only signing fingerprints match"

adb install "$before"
adb shell am start -n "$package/.MainActivity" >/dev/null
adb shell am force-stop "$package"

# Create a private, synthetic SQLite fixture that matches the source's schema.
fixture="$(mktemp --suffix=-synthetic.db)"
python3 - "$repo/app/src/main/java/fun/cadacrew/roadledger/GarageDb.java" "$fixture" <<'PY'
import pathlib,re,sqlite3,sys
source=pathlib.Path(sys.argv[1]).read_text()
db=sqlite3.connect(sys.argv[2])
for sql in re.findall(r'db\.execSQL\("(CREATE (?:TABLE|INDEX)[^"\n]*)"\)',source):
    db.execute(sql)
db.execute('PRAGMA user_version=2')
db.execute("INSERT INTO vehicles(id,name,make,model,unit_system) VALUES(9731,'SYNTHETIC UPGRADE VEHICLE','Test','Fixture',1)")
db.execute('INSERT INTO fuel(id,vehicle_id,date,odometer,liters,cost,full_tank,missed_fillup) VALUES(98,9731,1700600000456,1400.0,42.25,151.10,1,0)')
db.execute("INSERT INTO imported_rows(row_key,record_kind,vehicle_key,imported_at) VALUES('synthetic-upgrade-fuel','fuel','synthetic-upgrade-vehicle',1700900000123)")
db.commit()
assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
db.close()
PY
adb push "$fixture" /data/local/tmp/roadledger-upgrade-fixture.db >/dev/null
adb shell run-as "$package" mkdir -p databases
adb shell run-as "$package" cp /data/local/tmp/roadledger-upgrade-fixture.db databases/roadledger.db
adb install -r "$new"
adb shell am start -n "$package/.MainActivity" >/dev/null
adb shell am force-stop "$package"
exported="$(mktemp --suffix=-upgraded.db)"
adb exec-out run-as "$package" cat databases/roadledger.db > "$exported"
python3 - "$exported" <<'PY'
import sqlite3,sys
c=sqlite3.connect(sys.argv[1]);
assert c.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
assert c.execute('SELECT id,name,unit_system FROM vehicles WHERE id=9731').fetchone()==(9731,'SYNTHETIC UPGRADE VEHICLE',1)
assert c.execute('SELECT id,vehicle_id,date,odometer,liters FROM fuel WHERE id=98').fetchone()==(98,9731,1700600000456,1400.0,42.25)
assert c.execute("SELECT record_kind FROM imported_rows WHERE row_key='synthetic-upgrade-fuel'").fetchone()==('fuel',)
print('PASS: installation upgraded in place, synthetic SQLite records and import provenance survived')
PY
rm -f "$before" "$new" "$fixture" "$exported"
