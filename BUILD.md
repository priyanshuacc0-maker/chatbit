# Building the APK on GitHub

The APK is built by GitHub Actions, not on your laptop — the workflow runs the
Android SDK and signs the result. It attaches the APK to the workflow run as a
downloadable artifact.

The app is under development, so **nothing is published as a release**. The
APK is only ever an artifact of a run.

Workflow file: `.github/workflows/android.yml`

## How to get a new build

1. Commit your changes and push to `main`. That starts a build.
2. Open **Actions → Build APK → the newest run**, and download the
   `chatbit-apk-vc…` artifact from the **Artifacts** section at the bottom of
   the run summary.

You can also run it by hand: **Actions → Build APK → Run workflow**, optionally
typing a version name.

## Why a new APK installs over the old one

Android only accepts an in-place update when both of these hold:

- **Same signing key.** The workflow signs with the release keystore held in
  repository secrets. The key never changes between builds, so upgrades work.
- **Higher `versionCode`.** The workflow sets it to `1000 + run number`, which
  strictly increases with every build. You never have to bump it by hand.

The `versionName` (what the phone shows as "0.3.0") comes from the manual-run
input, or from `app/build.gradle.kts` if it is left blank.

If you ever change the signing key, phones will refuse the update and the app
must be uninstalled once before the new key's APK installs.

## Signing secrets

Four repository secrets drive signing (Settings → Secrets and variables →
Actions):

| Secret | What it holds |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the keystore file, base64-encoded |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias inside the keystore |
| `ANDROID_KEY_PASSWORD` | key password |

**Back up the keystore file.** If it is lost you cannot publish updates to an
installed app any more — you would have to ship a new app under a new key.

If those secrets are missing the workflow still runs, but falls back to a
debug-signed APK and says so in the release notes. Debug-signed builds cannot
install over a previous install.

### Using your own keystore instead

```bash
keytool -genkeypair -v -keystore chatbit-release.jks -alias chatbit \
        -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 chatbit-release.jks     # macOS: base64 -i chatbit-release.jks
```

Put the base64 output in `ANDROID_KEYSTORE_BASE64`, and the passwords and alias
in the other three secrets. The Gradle config picks the keystore type from the
file extension (`.p12` → PKCS12, anything else → JKS); set
`MESHLINE_KEYSTORE_TYPE` if you need to override it.

## Building locally

There is no Gradle wrapper checked in (no `gradle-wrapper.jar`), so `./gradlew`
will not work until you add one with `gradle wrapper --gradle-version 8.9`.
With Gradle 8.9 and JDK 17 installed:

```bash
gradle assembleDebug
```

A local `assembleRelease` without the `MESHLINE_*` environment variables
produces an unsigned APK, which cannot be installed.
