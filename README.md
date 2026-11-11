# RoadLedger v0.3.2 — signed TEST APK

This branch is an isolated Android APK distribution artifact. It is NOT a production merge or final app release.

- APK: RoadLedger-v0.3.2-release-signed.apk
- VersionCode: 4
- Certificate SHA-256: 29ac600dd7c1355c3ff4205eebac9e94c5960bd64b93ba28a87fd81031c58562
- SHA-256 of APK: 399c0f9d394044d4bf65fe38b5b3b0779a0df15647ff4df3412bf57366c103e0
- Signing identity: privately generated RSA-4096 key, not uploaded here
- Test status: GitHub Android emulator upgrade tests succeeded using a separate, disposable CI test key.
- Manual installed-device tests and off-VPS signing-key backup remain necessary.

If an older debug APK is installed, its certificate will differ. Only if NO important RoadLedger data is present, uninstall the old app before installing this signed test version. Do not uninstall if there is data to preserve.

This branch contains only this signed installable APK, README, and checksum.
