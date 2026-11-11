# RoadLedger v0.3.1 — backup and upgrade safety gates

**Status: unverified until instrumentation CI and device testing pass.**

Branch: `feature/roadledger-v03-restore-tests`, based on v0.3 commit
`1df07c8948a0f928c37e95631975a8e03344d775`. Do not merge into main
or install over a device containing important records yet.

## Automated Android tests

The new `RoadLedgerRecoveryInstrumentedTest` suite runs on a clean emulator
(Android 35) with a dedicated database name; it does not access `roadledger.db`.

- `fullRoundTripPreservesAllTablesAndProvenance`: writes/read/restores a native
  ZIP and checks all six tables, record IDs, original timestamps, US-unit
  settings, missed-fillup flag, vehicle links and import provenance.
- `checksumMismatchIsRejectedWithoutTouchingRecords`: re-ZIPs modified data
  without updating the SHA-256 and confirms it cannot be read or replace data.
- `foreignKeyFailureRollsBackEntireRestore`: re-ZIPs structurally valid data
  with an orphaned vehicle link and a correct checksum, then proves failed
  restore rolls back to exact pre-restore database state.
- `editsPersistAndFuelEconomyIsRecomputed`: edits records, historical dates,
  and odometer values, then asserts full-to-full economy updates.

CI builds the debug APK separately; emulator recovery tests must also pass.

## Additional release gates still REQUIRED

1. Add malformed/oversized archive and truncated ZIP tests; verify import
   deduplication and historical v0.1-to-v0.3 schema migrations on an emulator.
2. Run UI edit/import/backup workflows on a physical Android device and inspect
   crash logs and accessibility behavior.
3. Establish a *private, stable* release signing key, store it only in an
   authorized secret manager, and verify fingerprint continuity across upgrades.
   Never create or commit signing keys, passwords, or signing config secrets in
   this public branch. Debug APKs from GitHub Actions are not a safe upgrade path.
4. Prove APK upgrade/install over v0.2 **without uninstall**, with the existing
   database preserved. If v0.2 signing cannot be reproduced, devise an explicit
   export/restore migration and validate it before installation instructions.
5. Independently verify restores using representative synthetic fixtures; only
   after additional review should any private Fuelio backup be used locally.

**No production deploy, automatic merge, or on-device migration is authorized
by this test kit.** These tests cannot guarantee data safety by themselves.
