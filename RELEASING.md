# Releasing

Releases are built and published by GitHub Actions (`.github/workflows/release.yml`): it builds a minified release APK, signs it, and attaches it to a GitHub release with its SHA-256 checksum.

## One-time setup: a signing key

Android only installs an update if it's signed with the same key as the installed app, so every release must use the same key. Create it once and keep a backup somewhere safe: if it's lost, users have to uninstall to get updates.

1. Create a keystore, either in Android Studio (**Build → Generate Signed App Bundle or APK → Create new…**) or with `keytool`:

   ```sh
   keytool -genkeypair -v -keystore stock-widget.jks -alias stock-widget \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Encode it as base64 for GitHub:

   ```sh
   base64 -w 0 stock-widget.jks > stock-widget.jks.b64     # macOS: base64 -i stock-widget.jks -o stock-widget.jks.b64
   ```

3. In the repository on GitHub, open **Settings → Secrets and variables → Actions** and add four repository secrets:

   | Secret | Value |
   | --- | --- |
   | `KEYSTORE_BASE64` | the contents of `stock-widget.jks.b64` |
   | `KEYSTORE_PASSWORD` | the keystore password |
   | `KEY_ALIAS` | the key alias, e.g. `stock-widget` |
   | `KEY_PASSWORD` | the key password (the same as the keystore password for PKCS12 keystores) |

Until these secrets exist, the workflow still publishes, but signs with a one-off key that is thrown away and marks the release as a **preview**. Preview builds can't be updated in place: uninstall one before installing a regular release.

Never commit the keystore or its passwords. `.gitignore` already excludes `*.jks`, `*.keystore` and `keystore.properties`.

## Publishing a version

1. Bump `versionCode` (by one) and `versionName` in `app/build.gradle.kts`.
2. Add the changes to `CHANGELOG.md` and to `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Commit, then either push a tag:

   ```sh
   git tag v1.0.1 && git push origin v1.0.1
   ```

   or open **Actions → Release → Run workflow** and enter the tag (e.g. `v1.0.1`).

The workflow runs the unit tests, builds, signs and publishes the release. The release notes include the signing certificate's SHA-256 fingerprint, which users and app stores can use to verify the APK.

## Signing locally

To build a signed release on your own machine, create `keystore.properties` in the project root:

```properties
storeFile=/absolute/path/to/stock-widget.jks
storePassword=…
keyAlias=stock-widget
keyPassword=…
```

then run `./gradlew assembleRelease`. The APK is in `app/build/outputs/apk/release/`.

## F-Droid and IzzyOnDroid

The app is ready for both: no proprietary dependencies, no Google services, dependency metadata disabled in the APK, and store texts in `fastlane/metadata/android/en-US/`.

- **IzzyOnDroid** picks up APKs from GitHub releases. Once there is a regular (non-preview) release, request inclusion by following the instructions at <https://apt.izzysoft.de/fdroid/index/info>.
- **F-Droid** builds apps from source. Open a request in the [requests-for-packaging](https://gitlab.com/fdroid/rfp/-/issues) tracker, or submit a merge request to [fdroiddata](https://gitlab.com/fdroid/fdroiddata) with build metadata for the `io.github.sebytzr16.stockwidget` application ID. Expect F-Droid to flag the app with the *NonFreeNet* anti-feature, because prices come from Yahoo Finance.

## Screenshots

The README and store screenshots in `fastlane/metadata/android/en-US/images/phoneScreenshots/` come from the **Screenshots** workflow (**Actions → Screenshots → Run workflow**). It boots an emulator, loads the sample list in `scripts/screenshot-data.json`, takes the shots with `scripts/screenshots.sh` and pushes them to the `screenshots` branch. To use them, copy that folder from the branch into `main`:

```sh
git fetch origin screenshots
git checkout origin/screenshots -- fastlane/metadata/android/en-US/images/phoneScreenshots
git commit -m "Update screenshots"
```

Prices in the shots are live, so take them while the US market is open.
