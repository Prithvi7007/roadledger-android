# RoadLedger v0.4.0 — Permanently signed TEST APK

This is an isolated testing artifact (not a production merge).

APK: RoadLedger-v0.4.0-release-signed.apk
VersionCode: 5
Certificate SHA-256: 29ac600dd7c1355c3ff4205eebac9e94c5960bd64b93ba28a87fd81031c58562
APK SHA-256: fde607b65ddb34266d39ed7f1dab255a0408daedb4efadba7a317db3ea56c54b

Includes Insights, service schedules, saved fuel stations, custom expense categories, database v3 migration and backup compatibility. Ten Android emulator tests and the GitHub CI upgrade check passed on the source branch. The upgrade check uses a disposable test certificate; the APK here is signed with the permanent RoadLedger release key.

If v0.3.2 permanently signed is installed, this APK uses the same signing certificate and higher versionCode. Back up any important records first. An older debug-signed APK has a different certificate and cannot upgrade in place.

The signing keystore and password remain private on the VPS and are not included. Still NOT complete Fuelio feature parity.
