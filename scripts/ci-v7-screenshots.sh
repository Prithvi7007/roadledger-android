#!/usr/bin/env bash
set -euo pipefail
# Only for a disposable GitHub Actions emulator using synthetic records.
package='fun.cadacrew.roadledger'
folder='app/build/reports/androidTests/connected/screenshots'
mkdir -p "$folder"
fixture="$(mktemp)"
trap 'status=$?; if [ "$status" -ne 0 ]; then echo "v0.7 screenshot capture failed with exit $status"; adb shell pm path "$package" || true; adb logcat -d -t 200 | tail -n 160 || true; fi; rm -f -- "$fixture" "$fixture-wal"; exit "$status"' EXIT
apk=app/build/outputs/apk/debug/app-debug.apk
test -s "$apk"
echo 'Installing debug APK before screenshots (instrumentation may uninstall it)'
adb install -r "$apk" >/dev/null
adb shell am force-stop "$package" || true
adb shell am start -W -n "$package/.V7Activity" >/dev/null
adb shell am force-stop "$package"
adb exec-out run-as "$package" cat databases/roadledger.db > "$fixture"
if adb shell run-as "$package" test -f databases/roadledger.db-wal; then
 adb exec-out run-as "$package" cat databases/roadledger.db-wal > "${fixture}-wal"
fi
python3 - "$fixture" <<'PY'
import sqlite3,sys,time
conn=sqlite3.connect(sys.argv[1]);conn.execute('PRAGMA foreign_keys=ON')
assert conn.execute('PRAGMA user_version').fetchone()==(6,), 'UI screenshots require v0.7 schema'
if not conn.execute('SELECT COUNT(*) FROM vehicles').fetchone()[0]:
 conn.execute("INSERT INTO vehicles (id,name,make,model,unit_system,year,vin,registration,insurance,tank1_fuel,tank2_fuel,tank1_unit,tank2_unit,tank1_capacity,tank2_capacity,consumption_unit) VALUES (700,'RoadLedger Demo','Toyota','Camry',1,2023,'DEMO0000000000001','DEMO-001','Demo','Gasoline','Disabled','US gal','US gal',50,0,'US MPG')")
 now=int(time.time()*1000)
 for n in range(6):
  conn.execute('INSERT INTO fuel (vehicle_id,date,odometer,liters,cost,full_tank,missed_fillup,station_id,tank_number,source_unit,fuel_subtype,discount,weather) VALUES (?,?,?,?,?,1,0,0,1,\'US gal\',\'Regular\',0,\'Clear\')',(700,now-n*20*86400000,58000-n*440,42-n,63+n*2))
 conn.execute("INSERT INTO expenses (vehicle_id,date,category,amount,odometer,note) VALUES (700,?,?,?,58000,?)",(now-5*86400000,'Insurance',142.00,'Demo premium'))
 conn.execute("INSERT INTO service_items (vehicle_id,title,due_date,due_km,note,completed) VALUES (700,'Tire rotation',?,59000,'Synthetic demo',0)",(now+8*86400000,))
 conn.execute("INSERT INTO trips (vehicle_id,date,start_km,end_km,note) VALUES (700,?,57700,58000,'Personal trip')",(now-86400000,))
 conn.commit();assert conn.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
 assert conn.execute('PRAGMA wal_checkpoint(TRUNCATE)').fetchone()[0]==0
conn.close()
PY
adb push "$fixture" /data/local/tmp/roadledger-v07-demo.db >/dev/null
adb shell run-as "$package" rm -f databases/roadledger.db-wal databases/roadledger.db-shm databases/roadledger.db-journal
adb shell run-as "$package" cp /data/local/tmp/roadledger-v07-demo.db databases/roadledger.db
for tab in Home Fuel Service Trips More; do
 adb shell am force-stop "$package" || true
 adb shell am start -W -n "$package/.V7Activity" --es screen "$tab" >/dev/null
 sleep 2
 adb exec-out screencap -p > "$folder/v07-${tab,,}.png"
 python3 - "$folder/v07-${tab,,}.png" <<'PY'
from pathlib import Path
import sys,struct
p=Path(sys.argv[1]);raw=p.read_bytes();assert raw[:8]==b'\x89PNG\r\n\x1a\n';w,h=struct.unpack('>II',raw[16:24]);assert w>=320 and h>=480;print('Captured',p,w,h)
PY
done
