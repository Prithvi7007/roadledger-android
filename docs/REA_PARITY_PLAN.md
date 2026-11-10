# RoadLedger parity rebuild — implementation plan

Source: static REA investigation of Fuelio 10.9.2; **not** a running-app proof.
All code must be independently authored and kept free of third-party assets.

## P0: data safety and core edits (v0.3 development)

- [x] Editable vehicle, fill-up, expense and manual trip records.
- [x] Update preserves record IDs, dates unless explicitly changed and Fuelio import keys.
- [x] Own-data ZIP backup and all-or-nothing restore with checksum and FKs.
- [ ] Android emulator and on-device round-trip smoke test.
- [ ] Stable app signing: do not uninstall any installed build containing data.
- [ ] Lossless original Fuelio archive and explicit field/row diagnostics.
- [ ] Independent unit/conversion/economy test suite with multi-tank cases.

## P1: complete experience and history

- [ ] Multiple tanks, category editing, saved stations, reminders.
- [ ] Per-vehicle statistics, charts, date filtering and report export.
- [ ] Receipt/photo attachments with secure backup and recovery.
- [ ] Modular Android architecture and accessible black/gold UI redesign.
- [ ] Foreground GPS recording with explicit opt-in and privacy review.

## P2: optional connectivity

- [ ] Account-consented cloud synchronization and conflict resolution.
- [ ] Independent authorized fuel station APIs, OCR, Android Auto and widgets.

## Release gates

1. CI compile and synthetic fixture tests.
2. Backup/restore must round-trip with exact IDs and timestamps.
3. Restore corruption and FK violation must leave existing DB untouched.
4. Edit a historical entry; verify derived MPG and cost recomputation.
5. Import then edit a row; reimport should not duplicate or overwrite changes.
6. Upgrade signing verified before any migration of real phone data.
