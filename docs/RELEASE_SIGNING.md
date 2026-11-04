# WeGlow upload signing

Current build updates: development/staging now have separate app IDs. Production
keeps the existing package and upload certificate. Release requires an explicit
production backend key and upload signing; unsigned release attempts are rejected.
See [ENVIRONMENTS.md](ENVIRONMENTS.md). Historical unsigned-build notes below
describe earlier behavior. Rebuild the candidate after configuration and checks;
the old verified wizard artifact predates the current changes.

Application ID: `com.team03weglow.app`. The Kotlin namespace stays
`com.example.weglow`; this does not change the installed application's identity.
Play Console must still accept/register the new ID. No store registration has
been performed by this repository change.

## Create your upload key in Android Studio

1. Open **Build > Generate Signed App Bundle or APK** (the label may vary by
   Android Studio version). Choose **Android App Bundle**, then **Next**.
2. Select module **weglow.app** / **app**, then **Create new** beside the keystore.
3. Use this keystore path:
   `C:/Users/KLC/AndroidStudioProjects/weglow-app-development/.signing/weglow-upload.jks`.
4. Choose and confirm your own keystore password. Use alias `weglow-upload`,
   choose the key password, and use a validity of at least 25 years.
   Fill certificate details with your own accurate information. These details
   are part of the signing certificate; they are not another online account.
5. Create the key, then select **release** and finish generating the bundle.
   Do not send passwords or the keystore through chat or commit them to Git.

Keep a protected backup of the keystore and its passwords separately from this
computer. The project ignores `.signing/`, `*.jks`, `*.keystore`, and the local
credential files, but a Git exclusion is not a backup.

## Optional repeatable Gradle signing

The wizard can sign an individual build without storing passwords in this
repository's configuration. For command-line signing later:

1. Copy `keystore.properties.example` to `keystore.properties` in the project
   root. Enter both passwords locally and check the key alias/path.
2. Use forward slashes in file paths. This is a Java properties file; escape
   backslashes as `\\` and leading spaces as `\ ` if they occur in a password.
3. Run `./gradlew bundleRelease --no-configuration-cache` (Windows:
   `.\gradlew.bat bundleRelease --no-configuration-cache`).

The build loads this ignored file only when it exists. Incomplete files fail
with a message naming missing fields, without printing their values. Without
this file or credentials supplied by Android Studio, release outputs remain
unsigned and cannot be uploaded to Play. Debug builds continue to use the
development debug key.

## Verify before upload

- Verify the AAB signature with the JDK's `jarsigner -verify -verbose -certs`.
  Confirm it is signed by the intended upload certificate. A self-signed
  certificate is normal for an Android upload key.
- Output: `app/build/outputs/bundle/release/app-release.aab` for Gradle builds;
  the wizard may use a different destination selected in its final step.
- Enroll in Play App Signing for the first Play upload. Let Google manage the
  app-signing key, and retain this separate upload key for future uploads.
- A package change installs alongside the old development app; it does not
  migrate the old installation's local journal/session. Do not uninstall or
  erase the old app's data just to test the new build.
- Verify Google Sign-In with the new application ID. If an Android OAuth client
  is configured, register the new ID and appropriate certificate fingerprints.
  Play-installed builds use the **Play app-signing certificate**, not the upload
  certificate. The current Supabase browser callback remains
  `weglow://login-callback`; inspect actual provider settings before editing them.
- The FileProvider authority is derived from the application ID and should now
  be `com.team03weglow.app.fileprovider`; verify the merged manifest and camera.

## Implementation verification

- `bundleRelease compileDebugAndroidTestKotlin lintRelease --offline
  --no-configuration-cache --no-daemon`: passed after changing the application ID.
- Release lint: 0 errors, 115 warnings. Device tests compiled; they were not run
  on a device in this verification.
- Debug/release merged manifests and release BuildConfig use
  `com.team03weglow.app`; FileProvider uses
  `com.team03weglow.app.fileprovider`. Activity class names keep the source
  namespace.
- Updated the device-test package assertion and resource URI. Fixed an existing
  routine-journal test AuthRepository stub missing `currentUserEmail()`.
- The owner created `.signing/weglow-upload.jks` and generated the signed bundle
  through Android Studio on 2 November 2026. No passwords were entered by the
  agent. This does not configure command-line signing automatically; use the
  wizard again or the optional ignored properties file for future signed builds.
- Verified signed output: `app/release/app-release.aab` (134,916,058 bytes).
  This is the artifact checked below; do not confuse it with an earlier unsigned
  bundle in `app/build/outputs`. The entire wizard output folder is Git-ignored.
- `jarsigner -verify`: `jar verified`. The JDK also reports self-signed-chain,
  no-timestamp, ZIP attribute and JarInputStream manifest-order warnings. An
  independent JarFile verification read every payload entry and confirmed all
  650 payload entries are signed by one consistent certificate. These local
  checks do not substitute for Play's upload checks.
- Installed bundletool 1.18.3: bundle structure validation passed; manifest
  application ID confirmed as `com.team03weglow.app`.
- Upload certificate SHA-256 (public fingerprint):
  `F8:51:2A:CD:5A:0D:91:7E:A5:17:20:75:36:40:0A:0B:46:05:2F:11:C7:17:E0:38:2A:74:9C:6E:CD:B8:9A:20`.
  Certificate expires 27 October 2051. This is the upload certificate, not a
  Play app-signing certificate.
- Verified AAB SHA-256:
  `2DEA6985AF9F35EC4A87500A2E5AD59632582762E7DDC75153007118C78B5A3E`.
- Key/password backup by the owner, Play App Signing enrollment, store
  registration and release-device/backend checks remain pending. No upload to
  Play Console was performed.

Official instructions:
https://developer.android.com/studio/publish/app-signing
