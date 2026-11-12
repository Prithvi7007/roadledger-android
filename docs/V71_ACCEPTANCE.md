# RoadLedger v0.7.1 — Home and Navigation Acceptance

**Base:** `46665a9bb1e2b7c48bc1dfe103159b690ec5687e` on `feature/roadledger-v07-full-experience`.

This increment deliberately **does not** claim automatic trip detection or advanced Fuel charts. Those are subsequent increments.

## Product behaviors

- Exactly three bottom navigation destinations: Home, Fuel and Trips, including selected states.
- Existing Service, Expenses, Stations, Receipts, Backup/Restore, Insights, Reports, and Tools remain reachable via Home, top-right More, or the classic-advanced screen.
- Home displays vehicle / latest odometer, monthly total spend, average tank-1 full-to-full consumption (or a pending state), monthly logged trip distance, total fuel spending over the displayed six-month window, and six monthly spending bars using the active vehicle's real records.
- Monthly spend includes fuel + non-fuel expenses + separately logged service history (avoid double logging the same service as an expense).
- Prominent Home **Add Fill-up** action opens the existing persisted entry form. Saving from Home returns to Home and refreshes analytics; saving from Fuel returns to Fuel.
- Android 8–15+ system status/navigation bar insets are applied to the app root so header and bottom tabs are not obscured. Tap targets for tabs, vehicle picker and More are at least 48 dp.
- Empty fuel history shows a pending economy estimate rather than an invented rate. Empty spending history shows an empty state, not a misleading chart.
- Database schema and backup format remain unchanged in this increment.

## Verification gates

- [x] Pure Java monthly calendar boundary, aggregation and invalid-number tests completed locally.
- [x] Static integrity checks prepared for the isolated source files.
- [ ] Android APK compile in GitHub Actions.
- [ ] Android navigation test on API 35 emulator.
- [ ] Previous v0.7 database / recovery regression tests remain green.
- [ ] Screenshot inspection at standard Android size and with gesture/3-button navigation; no overlap, clipping or inaccessible actions.
- [ ] Hands-on Android test: add a fill-up via Home, return to Home, verify spending and economy updates appropriately.
- [ ] User visual review and approval before merge.

**No personal vehicle data, location recordings or credentials are required for CI.**
