# RoadLedger v0.3 (data-safety development branch)

This branch adds **native, checksum-verified backup/restore** and editable vehicle,
fill-up, expense and trip records. It preserves the existing v0.2 Fuelio import.

## Safety guarantees

- ZIP backup has `manifest.json` and `records.json`; original record IDs,
  full timestamps, unit preferences and import deduplication metadata survive.
- Restore checks format, schema, SHA-256, required fields, row limits, and
  foreign keys. All changes occur in a single SQLite transaction.
- A restore **replaces all** RoadLedger data only after explicit user confirmation.
  Export your existing records to a separate native backup first.
- Backup archives are **not encrypted**. Choose a private storage destination.
- Editing does not remove source GUID deduplication history or alter record IDs.
- This is a **dev / test build**, not a safe upgrade path from debug-signed v0.2:
  GitHub CI debug signing may change between builds. Do not uninstall an app
  containing records or replace it without a verified backup/restore strategy.
- Existing CSV export is summary-only; it is **not** a native recovery backup.

## Scope and remaining work

- v0.3 covers the first P0 data-safety slice, **not** all Fuelio feature parity.
- Multi-tank, raw lossless Fuelio import, reminders, GPS, photo attachment,
  stations, report generation and stable release signing remain outstanding.
- Actual physical-device UI and upgrade migration tests are still required.
- No private Fuelio backups or user vehicle records belong in public GitHub.

For the full backlog, see `docs/REA_PARITY_PLAN.md`.
