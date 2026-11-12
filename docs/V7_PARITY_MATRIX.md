# RoadLedger v0.7: audited feature matrix, not a claim of full Fuelio parity

Product scope: all 17 groups discussed for RoadLedger. **A source file or green build is not product signoff.** For every feature, confirm reachable UI, correct storage/units, migration, native backup/restore, emulator test, and physical-device behavior. Do not label the release complete until those checks are shown.

| # | Family | v0.7 implementation in this source kit | Still unverified or missing |
|---|---|---|---|
| 1 | Vehicles, multi-fuel, units | v0.6 dual tanks, VIN/year/registration, unit conversion; v0.7 new home vehicle switcher and advanced-settings route | More intuitive native V7 unit editor; multi-currency formatting |
| 2 | Fill-up logging | New dedicated v0.7 fuel screen and create/edit forms with tanks, station, price, discount, fuel grade, weather, full/partial/missed | Auto GPS/weather, receipt attached to a specific fill-up |
| 3 | Consumption | Existing mathematically guarded per-tank full-to-full calculation with new on-screen presentation | Bi-fuel energy source attribution when both tanks alternate between intervals |
| 4 | Home/timeline | New default launcher with hero odometer, costs, direct actions, all-record monthly timeline | Rich home customization |
| 5 | Expenses and service | New expense history/forms, separate completed service history and recurring service planner | Vendor inventory, split taxes, deep attachment-to-service linking |
| 6 | Reminders | Recurrence via dates/km, daily WorkManager notification checks, per-vehicle warning/mute settings | Exact-time scheduling, per-reminder snooze, tested Android 13 permission UX |
| 7 | GPS trips/manual mileage | Opt-in foreground GPS recorder, route points, saved trips, manual trips, GPX export | Advanced GPS outlier filtering, route playback, detailed speeds and altitude charts, tax-calibrated mileage rules |
| 8 | Bluetooth auto-trip | Per-vehicle pairing preference reserved; **automatic starts disabled intentionally** | Android-compliant pairing and reliably consented background tracking |
| 9 | Fuel stations/pricing | Saved stations, favorites, ratings, manual last-known prices, opening map destinations | Live third-party price API, crowdsourced prices, real-time verification |
| 10 | Route planning/refueling | Journey cost/range calculator and open-in-Maps deep link | Internal multi-stop route and refuel optimization |
| 11 | Receipts/photos/OCR | Local copy of image bytes included in native ZIP backup, gallery/document picker and on-device ML Kit text extraction | Camera capture, scanning crop/rotation, line-item parsing; currently max 2 MB per image |
| 12 | Statistics/charts | Spend, cost/km, monthly spending bars, per-tank economy and vehicle overview | Interactive slicing, rich visual analytics, period comparison dashboard |
| 13 | Reports | Download via system picker: original detailed CSV, new consolidated CSV, PDF, GPX trips | Tax-report rules by jurisdiction, sales/ownership dossier templates |
| 14 | Fuel/journey calculators | Cost and range tool with unit-aware trip distance | Provider-backed tolls, terrain/temperature/elevation predictive model |
| 15 | Backup/import/export | Extends native single-DB ZIP schema from v5 to v6; includes route GPS points, receipt content, service history, stations and preferences; remains compatible with old native backups | True conflict-resolved multi-device cloud sync, encrypted automatic cloud backup |
| 16 | Android Auto/shortcuts/widgets | Android home widget and two static shortcuts (Fuel, Trips) | Android Auto host app integration (requires policy-specific surface/review) |
| 17 | Audit-derived metadata | Year/VIN/registration/insurance, second tank, source provenance, manual station price, route and attachment data | Full Fuelio audit equivalence and third-party service integrations |

## Verification gate

- Source/syntax checks: Python installer, Java parser, XML parser, standalone pure-Java arithmetic tests.
- **Pending**: apply against pinned real v0.6 branch, `gradle :app:assembleDebug :app:assembleDebugAndroidTest`, run all connected Android instrumentation tests, verify both `v5 → v6` database migrations and old native backups, capture screenshots via emulator, then review actual UI.
- **Pending**: manual Android 8–15 permission and GPS foreground-service runs; ensure attachments, reminders, widget updates, and PDF exports operate correctly.
- **Pending**: review real device and stable signing. Never uninstall an app containing personal records to test v0.7.

## Data-safety guarantees and caveats

- The existing `roadledger.db` remains authoritative; no second shadow database is introduced.
- New data uses foreign keys to the same vehicle/station/trip tables; v0.7 native backup includes every additional table.
- Original v0.2–v0.6 backup compatibility is intended and tested only after Android instrumentation and upgrade tests pass.
- No personal Fuelio data, location route records, photographs, or private credentials should ever be committed to GitHub.
- Original backup/restore and Fuelio import are still available from the classic/advanced management activity; this is transitional UX, not full native redesign.
- Location recording requires an explicit user action and an ongoing foreground notification. There is **no silent location collection**.
- ML Kit text-recognition is bundled for on-device OCR; it has not been confirmed on a physical device.
- USD is currently the visible accounting currency. Changing currencies requires a separately verified price conversion and display workflow.
