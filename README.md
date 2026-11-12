# RoadLedger v0.7 — original Android implementation candidate

**Status: source kit built and locally checked; NOT yet applied to GitHub, compiled with Android SDK, emulator-tested, signed, merged, deployed or installed.** Do not call this a release or tell anyone the features are proven until CI and screenshots pass.

This is an original implementation against repository `Prithvi7007/roadledger-android`, based on exactly commit `7db61d639b629874536c59f76c647cba2936b0a9` (the v0.6 feature branch). It does not use private records or Fuelio APK code.

## What visibly changes

The default Android launcher switches from `MainActivity` (retained for advanced local import/backup and vehicle settings) to `V7Activity`: a new light-mode dashboard and bottom navigation with **Home, Fuel, Trips, Service, More**, and dedicated screens for **Expenses, Insights, Timeline, Stations, Receipts, Tools, Reports, Settings**. Direct actions are visible on the home screen. All core records share `GarageDb` and `roadledger.db`.

Added code includes:

- Complete usable add/edit views for fill-ups and expenses; independent tank awareness inherited from v0.6
- Completed service history, recurrence, reminder marking, daily WorkManager maintenance notifications and warning controls
- Opt-in foreground GPS recording with route-point persistence and GPX export, plus manual trips
- Station favorites, manual price/rating, maps app launch; no invented live prices
- Receipt image bytes stored in the database and included in native ZIP backup; offline ML Kit text OCR
- Monthly spending chart, cost summary, PDF/CSV reports, journey range/cost calculator
- Home-screen widget and Fuel/Trips app shortcuts
- Native backup schema 6 preserves receipts, routes, station details, preferences and service history; older backup schemas accepted
- New synthetic Android instrumentation tests and in-place v0.6→v0.7 upgrade script, plus screenshot-capturing CI job

See `docs/V7_PARITY_MATRIX.md` for **explicit remaining gaps**. Bluetooth auto-start, Android Auto, live provider prices, automatic cloud sync and comprehensive Fuelio parity are *not* implemented. This package is a substantial functional foundation, **not all 17 feature groups completed**.

## Local checks completed (without Android SDK)

```text
V7Logic: 16 standalone Java business-logic assertions PASSED
Java parse: all 7 new Java files PASSED
SQLite schema: 7 new tables and 11 create/index statements PASSED
Android XML: 4 resources PASSED
Shell CI scripts: syntax PASSED
Python patch installer: syntax PASSED
Simulated patch integration: 20 intended file changes, manifest XML and CI anchors PASSED
Real GitHub v0.6 source anchors: independently checked exactly once each
```

## Apply to an isolated **clean** v0.7 branch

The installer has a pinned base commit and refuses other branches, unexpected changes, missing files or mismatched anchors. It stages every code transformation in memory before writing.

```bash
# Only in an isolated checkout, branched from the exact v0.6 commit:
# git worktree add -b feature/roadledger-v07-full-experience PATH 7db61d639b629874536c59f76c647cba2936b0a9
python3 /path/to/v07-kit/apply_roadledger_v07.py /path/to/new-worktree
```

Then before approving the next action, inspect the generated diff and run:

```bash
# Android SDK35, Java17, Gradle8.9 required; GitHub Actions is prepared to provision them.
gradle --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
gradle --no-daemon :app:connectedDebugAndroidTest
```

The GitHub Actions workflow, once pushed, will build v0.7 APKs, run the native instrumentation suite, perform historical and v0.6→v0.7 synthetic data migration tests, and upload emulator screenshots of Home/Fuel/Service/Trips/More. Do not treat it as production-ready until all pass and the resulting UI screenshots have been reviewed.

## Safety

- Do not deploy, merge, upload private records or touch `crew_games` or `crew-dev-bridge/main`.
- On real devices, export a verified RoadLedger native ZIP backup before attempting a debug-build upgrade. GitHub Actions ephemeral debug keys may not match a previously installed APK.
- No photo/image data or personal GPS data is uploaded by this source kit. ML Kit OCR runs on-device; selected map app handles any map route query separately.
- Actual trip tracking runs only when manually started with foreground location permission; *automatic Bluetooth trip starts are disabled*.
- No fabricated fuel prices or forecasted savings are presented as real observations.
- Native backups are not encrypted and are limited by the existing backup size ceiling; store privately.
- Receipt pictures are explicitly limited to 2 MB each (current in-memory backup model). No undisclosed record-count cap was added.
- Development commit and CI artifact names use v0.7. Do not call an unsigned release installable.
