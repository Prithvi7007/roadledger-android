# RoadLedger release signing and safe upgrades (v0.3.2)

## Two separate identities

- **GitHub CI debug**: disposable debug key, not a permanent update identity.
- **Private release**: permanent private RSA-4096 key, stored outside GitHub. Only releases signed by this key can update a previously release-signed RoadLedger app without uninstalling.

The CI workflow builds **an unsigned release** and runs emulator tests using a disposable debug identity. It does not receive the permanent private key. An unsigned APK must never be installed or described as ready for users.

## Persistent identity on CREW VPS

Maintain a root-owned directory outside the public repo (`0700`), containing one private `roadledger-release.p12` keystore (`0600`), one randomly generated password in a root-only file (`0400`), and a public certificate fingerprint. Do not copy any of these secret files into the repository, GitHub artifacts, tool output, or chat. Back up the private key **and** password securely off-VPS; VPS loss otherwise prevents app upgrades. Reusing the key is necessary for future signed releases.

Use a vetted Android `apksigner` and `zipalign` (Android SDK build tools) plus `scripts/sign-release.sh`. The wrapper reads credentials from files and verifies the output. A signed release APK may be distributed; the key/password must never be distributed.

## First migration from v0.2 debug builds

Older Github debug APKs were signed with arbitrary CI debug keys. They cannot update to this new release identity in place. On your phone, only after confirming there is **no important RoadLedger data**, uninstall the old debug APK and install the first permanently signed release. The same permanent certificate is then reused for all later upgrades.

If important data is discovered before uninstalling, **do not uninstall**: the v0.2 summary CSV cannot restore full fidelity. Stop and design a data-preserving migration first.

## Release gates

1. `:app:assembleRelease` produces unsigned APK, and only the offline signer creates distributable artifacts.
2. `apksigner verify --verbose --print-certs` verifies the signed release. The SHA-256 signer certificate fingerprint is recorded publicly (not private key).
3. Two successive release APKs are signed with the same persistent keystore and their signer certificate fingerprints compared.
4. Android emulator upgrade from v0.3.1 to v0.3.2 with same test signature preserves synthetic vehicle, fuel, timestamps, units, and import metadata. This proves upgrade mechanics; it does **not** use or expose the permanent private key.
5. Native backup/restore Android instrumentation tests remain passing.
6. Manual on-device upgrade and backup test before any `main` merge or user-data migration.

No production promotion, public APK shipping, or destructive uninstall is automated in the feature branch.
