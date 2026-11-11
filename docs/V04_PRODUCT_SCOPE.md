# RoadLedger v0.4 core experience (original independent implementation)

Version 0.4 expands the actual Android app, using the reference Fuelio manifest only to identify feature families. No proprietary code, art, or assets are copied.

## Implemented

- Home navigation with Insights, Service, and More while retaining Expenses, Trips, Garage.
- Insights: six calendar months of fuel + expense totals, average cost per liter/gallon, recent price history, category spending breakdown, last-12-month spending, and full-to-full weighted fuel economy already present in v0.3.
- Service reminders with editable due date, odometer threshold, notes, completed state, due-soon and overdue states. **Foreground display only**, no notification permission or background worker yet.
- Fuel stations with local names/addresses, pick a station for a fill-up, safe station deletion that retains fuel data.
- Custom expense categories including category definitions from imported Fuelio CSV; existing expense labels never deleted.
- Database v2→v3 migration preserving existing records and ids; v3 backup includes all new tables/fields; older v2 RoadLedger backup remains restore-compatible.
- Synthetic-device instrumentation tests for upgrades, backup restore, stations, service states and analytics.

## Not yet parity with Fuelio

- True on-device GPS tracking with background service, GPS trip routes, stations map/locations, multi-tank vehicles, receipts/attachments and image capture, price search, Google Drive/Dropbox sync, reminder notifications, PDF reports, widget, vehicle metadata beyond basic name/make/model, and high fidelity Fuelio CSV import of images/station coordinates. These remain distinct requirements and should not be marketed as complete.
- The monthly chart currently uses a compact existing bar visualization; month labels and screen reader enhancements are future UI polish.

## Release gates

- `assembleDebug` and `connectedDebugAndroidTest` must succeed on Android 35 emulator.
- Upgrade v0.3.2→v0.4 with the same signing identity must preserve records; previous v0.3.2 synthetic test only verified v0.3.1→v0.3.2.
- User's permanent signing identity is private and not part of this code branch.
- No production merge, APK deployment, or personal records in this branch.
