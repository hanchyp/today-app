# Setup guide

How to build, test, sign and install Today without Android Studio.

## Requirements

| Component | Notes |
|---|---|
| JDK 17 | Any distribution, for example Microsoft Build of OpenJDK 17 |
| Android SDK, API 35 | Set `sdk.dir` in `local.properties` (not committed) |
| Gradle | Comes with the wrapper (`./gradlew`, Gradle 8.10) |

Optional: set `GRADLE_USER_HOME` to move the Gradle cache to a bigger drive. Robolectric downloads its Android runtime (about 150 MB) into the folder set in `app/build.gradle.kts` (`user.home` for the test task); change it if that path does not exist on your machine.

## Build and test

```bash
./gradlew testDebugUnitTest   # all JVM tests (Robolectric for Room)
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk (R8)
```

## Release signing

- The release keystore lives outside the repo.
- Its path and passwords go in `keystore.properties` at the project root (ignored by git) with the keys `storeFile`, `storePassword`, `keyAlias` and `keyPassword`.
- Without `keystore.properties`, `assembleRelease` still works but the APK is unsigned.
- **Keep a backup of the keystore.** A release signed with a different key cannot be installed over an older one, so the user would have to uninstall and lose data that was not backed up.

## Installing on a phone

Some phones (for example Xiaomi with MIUI) block `adb install` without a SIM card. Copy the APK to the phone and open it from the file manager instead.

Debug and release APKs use different keys, so **a release build cannot be installed over a debug build**. To switch:

1. In the debug app: *Atur → Backup ke file*.
2. Uninstall the debug app.
3. Install `app-release.apk` and finish onboarding with *Kosong*.
4. *Atur → Pulihkan dari backup* and pick the backup file.

## Backup file

- *Backup ke file* writes `today-backup-YYYYMMDD.json` through the Android file picker (Downloads, Google Drive, and so on).
- It contains every table plus the user name and a `schemaVersion`. Files from a newer schema version are rejected.
- *Pulihkan dari backup* shows a summary first, then replaces all data in one transaction: a failed restore changes nothing. Future reminders are scheduled again.
- *Hapus semua data* asks you to type `HAPUS`; the exercise catalog and default finance categories are created again.

## Data conventions

UUID ids, `createdAt` / `updatedAt` / `deletedAt` (soft delete), money as whole rupiah in a `Long`, dates as `epochDay`. Schemas are exported to `app/schemas/`; any schema change needs a `Migration` and a test in `MigrationTest`. See `docs/SPRINTS.md` §0.4.

## README images

The scripts in `scripts/readme-assets/` rebuild every image in `docs/images/`. They need a running emulator (or device) on adb, Python 3 with Pillow, Node.js, Microsoft Edge (used by Playwright) and ffmpeg.

```bash
./gradlew assembleDebug
cd scripts/readme-assets
npm install
python capture_screens.py --keep   # fresh install, sample data, raw screenshots in raw/
node compose.mjs                   # banner, feature composites and mobile.png
python record_demo.py              # demo.gif (add --encode-only to re-encode raw/demo.mp4)
```

## Third-party licenses

- Inter font (SIL Open Font License 1.1) in `res/font/inter_variable.ttf`.
- Exercise catalog from [hasaneyldrm/exercises-dataset](https://github.com/hasaneyldrm/exercises-dataset): `assets/exercise-catalog.json` has 1,324 exercises (MIT data, see `assets/exercise-catalog-license.txt`).
- `assets/exercise-media/videos/*.webp`: 180×180 exercise animations converted from the original GIFs to animated WebP (lossy q70, method 6), about 3× smaller. Media © Gym visual, https://gymvisual.com/ (see `assets/exercise-media-notice.md`); credit is shown in the exercise dialog.
