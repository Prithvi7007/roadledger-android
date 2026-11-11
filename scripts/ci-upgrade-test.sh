#!/usr/bin/env bash
set -euo pipefail
# CI-only, same ephemeral DEBUG key for both APKs. Never uses the private release key.
repo="$(git rev-parse --show-toplevel)"
base_commit='dac81cd48a3832fc476d2043aead63e2cf57fc98'
package='fun.cadacrew.roadledger'
umask 077
test_dir="$(mktemp -d)"
trap 'rm -rf -- "$test_dir"' EXIT
cd "$repo"

copy_database() {
  local destination="$1"
  # Caller has force-stopped the app. Keep a WAL, if present, with its database
  # so SQLite can read all committed records regardless of device journal mode.
  adb exec-out run-as "$package" cat databases/roadledger.db > "$destination"
  if adb shell run-as "$package" test -f databases/roadledger.db-wal; then
    adb exec-out run-as "$package" cat databases/roadledger.db-wal > "${destination}-wal"
  fi
}

# Build the actual pinned v0.3.1 source, not current code with an old version label.
# Both builds use the runner's same ephemeral debug key; no release key is accessed.
mkdir "$test_dir/baseline"
git archive "$base_commit" | tar -x -C "$test_dir/baseline"
gradle --no-daemon -p "$test_dir/baseline" :app:assembleDebug
before="$test_dir/before.apk"
cp "$test_dir/baseline/app/build/outputs/apk/debug/app-debug.apk" "$before"
gradle --no-daemon :app:assembleDebug
new="$test_dir/current.apk"
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
adb shell am start -W -n "$package/.MainActivity" >/dev/null
adb shell am force-stop "$package"

# Seed the database created by the old Android app. Parsing CREATE statements from
# current Java mixed schema versions and omitted ALTERs such as tank_number.
fixture="$test_dir/synthetic.db"
copy_database "$fixture"
python3 - "$fixture" <<'PY'
import sqlite3,sys
db=sqlite3.connect(sys.argv[1])
assert db.execute('PRAGMA user_version').fetchone()==(2,), 'Baseline must create schema v2'
assert db.execute('SELECT COUNT(*) FROM vehicles').fetchone()==(0,), 'Emulator must be fresh'
db.execute('PRAGMA foreign_keys=ON')
db.execute("INSERT INTO vehicles(id,name,make,model,unit_system) VALUES(9731,'SYNTHETIC UPGRADE VEHICLE','Test','Fixture',1)")
db.execute('INSERT INTO fuel(id,vehicle_id,date,odometer,liters,cost,full_tank,missed_fillup) VALUES(98,9731,1700600000456,1400.0,42.25,151.10,1,0)')
db.execute("INSERT INTO expenses(id,vehicle_id,date,category,amount,odometer,note) VALUES(99,9731,1700700000789,'Maintenance',58.45,1400.0,'Synthetic expense')")
db.execute("INSERT INTO trips(id,vehicle_id,date,start_km,end_km,note) VALUES(100,9731,1700800000321,1350.5,1400.0,'Synthetic trip')")
db.execute("INSERT INTO import_vehicle_links(source_key,vehicle_id) VALUES('synthetic-upgrade-vehicle',9731)")
db.execute("INSERT INTO imported_rows(row_key,record_kind,vehicle_key,imported_at) VALUES('synthetic-upgrade-fuel','fuel','synthetic-upgrade-vehicle',1700900000123)")
db.commit()
assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
assert db.execute('PRAGMA wal_checkpoint(TRUNCATE)').fetchone()[0]==0
db.close()
PY
adb push "$fixture" /data/local/tmp/roadledger-upgrade-fixture.db >/dev/null
adb shell run-as "$package" mkdir -p databases
adb shell run-as "$package" rm -f databases/roadledger.db-wal databases/roadledger.db-shm databases/roadledger.db-journal
adb shell run-as "$package" cp /data/local/tmp/roadledger-upgrade-fixture.db databases/roadledger.db
adb install -r "$new"
adb shell am start -W -n "$package/.MainActivity" >/dev/null
adb shell am force-stop "$package"
exported="$test_dir/upgraded.db"
copy_database "$exported"
mkdir -p app/build/reports/androidTests/connected
python3 - "$exported" "$repo/app/src/main/java/fun/cadacrew/roadledger/GarageDb.java" <<'PY' | tee app/build/reports/androidTests/connected/upgrade-preservation.txt
import pathlib,re,sqlite3,sys
c=sqlite3.connect(sys.argv[1]);
expected_version=int(re.search(r'private static final int VERSION = (\d+);',pathlib.Path(sys.argv[2]).read_text()).group(1))
assert c.execute('PRAGMA user_version').fetchone()==(expected_version,), 'App did not migrate to current schema'
assert c.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
assert c.execute('PRAGMA foreign_key_check').fetchall()==[]
assert c.execute('SELECT id,name,make,model,unit_system FROM vehicles WHERE id=9731').fetchone()==(9731,'SYNTHETIC UPGRADE VEHICLE','Test','Fixture',1)
assert c.execute('SELECT id,vehicle_id,date,odometer,liters,cost,full_tank,missed_fillup,station_id,tank_number FROM fuel WHERE id=98').fetchone()==(98,9731,1700600000456,1400.0,42.25,151.10,1,0,0,1)
assert c.execute('SELECT id,vehicle_id,date,category,amount,odometer,note FROM expenses WHERE id=99').fetchone()==(99,9731,1700700000789,'Maintenance',58.45,1400.0,'Synthetic expense')
assert c.execute('SELECT id,vehicle_id,date,start_km,end_km,note FROM trips WHERE id=100').fetchone()==(100,9731,1700800000321,1350.5,1400.0,'Synthetic trip')
assert c.execute('SELECT source_key,vehicle_id FROM import_vehicle_links').fetchall()==[('synthetic-upgrade-vehicle',9731)]
assert c.execute('SELECT row_key,record_kind,vehicle_key,imported_at FROM imported_rows').fetchall()==[('synthetic-upgrade-fuel','fuel','synthetic-upgrade-vehicle',1700900000123)]
for table in ('fuel_stations','service_items','expense_categories','fuelio_archives','import_diagnostics'):
    assert c.execute('SELECT COUNT(*) FROM '+table).fetchone()==(0,), table+' must be initialized empty'
print('PASS: actual v0.3.1 APK upgraded in place to schema v'+str(expected_version))
print('PASS: vehicle, fuel, expense, trip, timestamps, units and import provenance preserved')
print('PASS: new station, tank, maintenance and archive schema initialized')
c.close()
PY
