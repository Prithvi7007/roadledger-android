# RoadLedger v0.7.2 — Native Data & Backup navigation

Base: feature/roadledger-v071-home-navigation at `825d4cf770ebbf0e2b47d4dac468df7ee04ca1e1`.

## The reported bug

In v0.7.1, “Import / restore existing data” and “Native ZIP backup / restore & Fuelio import” launch `MainActivity` (legacy UI) from the new RoadLedger interface. This is not acceptable as a normal navigation path.

## Acceptance

- Empty-garage Import / Restore opens native **Import & backup** in V7Activity, without launching MainActivity.
- With vehicles, Settings and More and Reports navigate to the same **Import & backup** screen.
- Fuelio CSV import uses the existing `FuelioImport.inspect` preview and `FuelioImport.apply` transaction; displays expected counts and diagnostics, does not replace existing records, avoids duplicate rows, and returns to Home after a confirmed import.
- RoadLedger ZIP export uses the existing `RoadLedgerBackup.write`, via Android document picker, and displays the nonencrypted-backup warning.
- ZIP restore uses `RoadLedgerBackup.read` to validate before showing a **Replace all** confirmation, then invokes transactional `RoadLedgerBackup.restore` only on approval. Cancel/invalid ZIP never modifies existing records.
- Empty garage can restore a backup to create vehicle records.
- On successful import/restore, Home vehicle selection and analytics reload.
- Do not modify DB schema, backup format, importer, manifest, or personal records.
- Legacy MainActivity is still accessible **only** for unmigrated advanced vehicle/tank management (labeled as such).

## Verification

- [x] Local Java 17 parser checks for changed sources.
- [x] Exact path and source-API contract checks.
- [ ] Android Gradle compilation and connected instrumentation on disposable emulator.
- [ ] Test `importRestoreUsesNativeScreenWithoutLaunchingLegacyActivity` on emulator.
- [ ] Capture 6 actual screens including Data; confirm no blocking system dialogs.
- [ ] Verify real document-picker flows end to end with generated synthetic CSV/ZIP.
- [ ] User approval before merge; no auto-deployment.
