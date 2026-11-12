#!/usr/bin/env bash
set -euo pipefail
# CI emulator ONLY: performs an in-place v0.6 -> v0.7 upgrade with synthetic records.
# Never run against a physical device holding real records.
repo="$(git rev-parse --show-toplevel)"
base_commit='7db61d639b629874536c59f76c647cba2936b0a9'
package='fun.cadacrew.roadledger'
umask 077
scratch="$(mktemp -d)"
trap 'rm -rf -- "$scratch"' EXIT
cd "$repo"
mkdir "$scratch/base"
git archive "$base_commit" | tar -x -C "$scratch/base"
gradle --no-daemon -p "$scratch/base" :app:assembleDebug
gradle --no-daemon :app:assembleDebug
old="$scratch/v06.apk"
new="$scratch/v07.apk"
cp "$scratch/base/app/build/outputs/apk/debug/app-debug.apk" "$old"
cp app/build/outputs/apk/debug/app-debug.apk "$new"
export PATH="$ANDROID_HOME/build-tools/35.0.0:$PATH"
old_cert="$(apksigner verify --print-certs "$old" | grep 'Signer #1 certificate SHA-256 digest:')"
new_cert="$(apksigner verify --print-certs "$new" | grep 'Signer #1 certificate SHA-256 digest:')"
test -n "$old_cert" && test "$old_cert" = "$new_cert"
# Run only on a pristine, disposable emulator created by the CI job.
adb install "$old"
adb shell am start -W -n "$package/.MainActivity" >/dev/null
adb shell am force-stop "$package"
fixture="$scratch/fixture.db"
adb exec-out run-as "$package" cat databases/roadledger.db > "$fixture"
if adb shell run-as "$package" test -f databases/roadledger.db-wal; then
 adb exec-out run-as "$package" cat databases/roadledger.db-wal > "${fixture}-wal"
fi
python3 - "$fixture" <<'PY'
import sqlite3,sys
c=sqlite3.connect(sys.argv[1]);c.execute('PRAGMA foreign_keys=ON')
assert c.execute('PRAGMA user_version').fetchone()==(5,),'Baseline must be v0.6 schema 5'
assert c.execute('SELECT COUNT(*) FROM vehicles').fetchone()==(0,),'CI emulator must be pristine'
c.execute("INSERT INTO vehicles (id,name,make,model,unit_system,year,vin,registration,insurance,tank1_fuel,tank2_fuel,tank1_unit,tank2_unit,tank1_capacity,tank2_capacity,consumption_unit) VALUES (1337,'SYNTHETIC V6','Test','Vehicle',0,2022,'TESTVIN','TEST-PLATE','None','Gasoline','Disabled','L','L',50,0,'Auto')")
c.execute("INSERT INTO fuel (id,vehicle_id,date,odometer,liters,cost,full_tank,missed_fillup,station_id,tank_number,source_unit,fuel_subtype,discount,weather) VALUES (1234,1337,1700000000000,25000,36,100,1,0,0,1,'L','Regular',5,'Clear')")
c.execute("INSERT INTO expenses (id,vehicle_id,date,category,amount,odometer,note) VALUES (1235,1337,1700000000000,'Insurance',99,25000,'Synthetic note')")
c.execute("INSERT INTO service_items (id,vehicle_id,title,due_date,due_km,note,completed) VALUES (1236,1337,'Synthetic Service',1700000000000,27000,'Synthetic',0)")
c.commit()
assert c.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
assert c.execute('PRAGMA wal_checkpoint(TRUNCATE)').fetchone()[0]==0
c.close()
PY
adb push "$fixture" /data/local/tmp/roadledger-v6-v7-fixture.db >/dev/null
adb shell run-as "$package" rm -f databases/roadledger.db-wal databases/roadledger.db-shm databases/roadledger.db-journal
adb shell run-as "$package" cp /data/local/tmp/roadledger-v6-v7-fixture.db databases/roadledger.db
adb install -r "$new"
adb shell am start -W -n "$package/.V7Activity" >/dev/null
adb shell am force-stop "$package"
exported="$scratch/upgraded.db"
adb exec-out run-as "$package" cat databases/roadledger.db > "$exported"
mkdir -p app/build/reports/androidTests/connected
python3 - "$exported" <<'PY' | tee app/build/reports/androidTests/connected/v06-to-v07-upgrade.txt
import sqlite3,sys
c=sqlite3.connect(sys.argv[1])
assert c.execute('PRAGMA user_version').fetchone()==(6,), 'Must migrate from schema 5 to schema 6'
assert c.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
assert c.execute('PRAGMA foreign_key_check').fetchall()==[]
assert c.execute("SELECT name,year,vin,registration FROM vehicles WHERE id=1337").fetchone()==('SYNTHETIC V6',2022,'TESTVIN','TEST-PLATE')
assert c.execute("SELECT id,discount,weather FROM fuel WHERE id=1234").fetchone()==(1234,5,'Clear')
assert c.execute("SELECT category,amount,note FROM expenses WHERE id=1235").fetchone()==('Insurance',99,'Synthetic note')
assert c.execute("SELECT title,due_km,completed FROM service_items WHERE id=1236").fetchone()==('Synthetic Service',27000,0)
for table in ('service_history','v7_vehicle_settings','v7_routes','v7_route_points','v7_attachments','v7_station_meta','v7_reminder_recurrence'):
 assert c.execute('SELECT COUNT(*) FROM '+table).fetchone()==(0,),table+' should start empty'
print('PASS: original v0.6 app upgraded to v0.7 without losing synthetic vehicle, fuel, expense or service records')
print('PASS: all seven v0.7 tables initialized and foreign keys checked')
c.close()
PY
