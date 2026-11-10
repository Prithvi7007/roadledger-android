# RoadLedger v0.2 — original offline-first Android vehicle log

## What's new in v0.2
- **Black / charcoal / gold** night-driving visual theme.
- Import a **Fuelio CSV backup** directly from Android's file picker or Google Drive's document provider, without uploading it to any server.
- Preview the record counts and confirm before importing. Imports are **atomic, additive, and deduplicated** by exported source GUIDs.
- Preserves original fill-up timestamps and totals; converts Fuelio's miles and US gallons to SI internal storage while showing **miles, gallons and US MPG** for imported US-unit vehicles.
- Flags missed fill-ups; the fuel-economy calculation excludes intervals broken by missing history.
- Imports vehicle profiles, fill-up history, expense history and trip logs. GPS trips with no odometer readings use their recorded GPS distance (meters) and are labeled as such.
- CSV export remains available for all local records.

**Not yet imported:** Favorite fuel stations, photo attachments / references, GPS track files, VIN/plate details, multiple-tank accounting, and custom categories. The source Fuelio CSV remains unchanged; preserve your backup for future migrations.

**Privacy:** The importer runs entirely on your phone. Do not commit real user exports to GitHub. The app does not request INTERNET, Drive, or GPS permissions; Android's system document picker handles Google Drive file access.

**IMPORTANT UPGRADE NOTE:** GitHub Actions debug builds may use different debug signing certificates each run. Android can reject installing v0.2 over v0.1 with 'App not installed' / signature mismatch. Before any uninstall, export existing RoadLedger data (Garage -> Export). The current release cannot import its own exported CSV yet, so if those manually entered records matter, keep v0.1 installed until a stable signing mechanism is configured. Never uninstall to force an update without checking whether you will lose existing data.

---

A clean-room native Android MVP designed from general architectural lessons in a vehicle/fuel-tracking application audit. **No Fuelio source code, assets, package names, API credentials, or branded designs are included.**

## Features in v0.1

- Manage multiple vehicle profiles (vehicle name, make, model)
- Enter fuel fill-ups with date, odometer (km), liters, total cost, and full/partial status
- Compute weighted L/100 km from complete **full-to-full** intervals, counting partial fills between anchors
- Track maintenance, insurance, parking, tolls, repairs, and other expenses
- Manually record trips with start/end odometer readings
- See an offline dashboard with economy, spending, trip totals, and recent fill-ups
- Export all records via Android's document picker to a CSV you own
- Long-press any record to delete (vehicle deletion confirms and cascades to records)

## Limitations

**Prototype, not production-ready.** It does not provide edit-in-place, live GPS/background tracking, Bluetooth automation, cloud sync, station API, receipt OCR, Android Auto, currency conversion, notifications, automatic reminders, or billing. Trips are manually entered. The app requests **zero Android permissions**. Monetary values are USD, distances km, fuel liters; these fixed units are shown in the UI.

Fuel economy is only calculated between two full fill-ups at distinct increasing odometer readings. Intermediate partial fills are counted; an incomplete interval has no economy value. This is a new, simplified implementation, not a reproduction of any proprietary algorithm.

The local SQLite database uses app-private storage and **is not additionally encrypted**. Android device backup is disabled. CSV export is manually initiated, and a user-created CSV is not encrypted by the app.

## Build an APK in Android Studio

1. Install Android Studio and Android SDK Platform 35.
2. Open this `roadledger/` folder as a Gradle project.
3. Use JDK 17 and Gradle 8.9 (Gradle sync may prompt to install it).
4. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**, or run:
   
   ```bash
   cd roadledger
   gradle :app:assembleDebug
   ```
5. Find **`app/build/outputs/apk/debug/app-debug.apk`** and install it on Android 8.0+.

The APK is **debug-signed**, for personal testing only, and is not suitable for Play Store distribution.

## Optional GitHub Actions build

The CREW bridge correctly requires separate human review before updating executable workflow files. A **non-active workflow template** lives at `ci/roadledger-apk.workflow.yml`. After reviewing it, a maintainer can manually place it at `.github/workflows/roadledger-apk.yml` and trigger the build on GitHub. **The template is not active and no APK is generated merely by opening this PR.**

## Privacy and provenance

No `INTERNET`, `ACCESS_FINE_LOCATION`, tracking, ad SDK, or analytics permissions. RoadLedger is independently implemented and should be tested with synthetic/non-sensitive sample data before personal use.

## Standalone Android repository
Extracted from CREW Development Bridge feature branch. The original repository's
main branch and production services are not modified by this migration.
No personal Fuelio backups or vehicle records are committed.
