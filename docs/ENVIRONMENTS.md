# WeGlow environments and release handoff

2026-11-06 update: see [the dated launch-fix report](LAUNCH_FIXES_2026-11-06.md).
Product-independent routines and production feature gates are implemented;
weather, account deletion and retired-chat handlers are deployed. Disposable-user
live checks passed for completion persistence, isolation, Storage, recovery-token
password changes and deletion. Inbox delivery, public policy/deletion pages,
staging configuration and upload signing remain owner/release setup items.

Latest hosted checkpoint: the owner reports Google sign-in working in WeGlow
Dev against the new backend. Email signup/confirmation and recovery delivery,
authenticated profile/Storage behavior and release-build tests are still pending.

Prepared on `refining-the-app-for-playstore`. Local configuration/build checks
do not establish hosted deployment readiness or Play Console approval.

## Verification completed

- Full JVM suite: 274 tests, zero failures/errors with installed Temurin 25.0.4.
  The earlier JBR native-DLL failure was reproduced, diagnosed and resolved by
  selecting the existing newer JDK; no model test was skipped or weakened.
- Debug APK assembly, instrumentation compilation and architecture check passed.
- Debug lint: zero errors, 87 warnings retained in the report.
- Four Android 17 emulator tests passed: development package identity, chart
  selection/baseline and the actual bundled-model faceless-photo rejection.
- All 16 Android 64-bit native libraries passed 16 KB ELF alignment; SDK
  `zipalign -c -P 16 4` passed for the debug APK. The emulator reports 4096-byte
  pages, so this is not a 16 KB runtime certification.
- Fresh-setup/hairstyle import, both progress schema variants and full database
  migration/ownership/deletion checks passed in isolated PGlite databases.
- Missing production key, missing signing and missing staging URL were each
  rejected by the corresponding validation task, as intended.
- Git diff whitespace check passed. GitHub CI and hosted end-to-end tests remain
  unrun. No new production AAB or Play upload was produced.

Connection checkpoint: the owner supplied the new project's publishable key.
It is configured in ignored `local.properties` for development and production,
with the original file backed up under ignored `build/environment-backups`.
Production environment validation now passes, and the rebuilt development APK
was installed successfully on the connected emulator without clearing app data.
Live read-only checks returned Auth settings HTTP 200 (Google/email enabled,
email auto-confirm false), including matching apikey/Bearer headers. Anonymous
hairstyle/profile reads were rejected with HTTP 401 / SQLSTATE 42501. An unsigned
Storage probe returned NoSuchBucket; that does not distinguish private from
missing, so authenticated Storage verification remains pending. No accounts
were created and no email was sent by these probes. Add the development callback
to the candidate project's allowlist before testing Google in WeGlow Dev.

## Build variants

| Variant | App ID | Label | App callback | Configuration prefix |
| --- | --- | --- | --- | --- |
| debug | `com.team03weglow.app.debug` | WeGlow Dev | `weglow-dev://login-callback` | `WEGLOW_DEV_SUPABASE_` |
| staging | `com.team03weglow.app.staging` | WeGlow Staging | `weglow-staging://login-callback` | `WEGLOW_STAGING_SUPABASE_` |
| release | `com.team03weglow.app` | WeGlow | `weglow://login-callback` | `WEGLOW_PROD_SUPABASE_` |

Each prefix has `URL` and `PUBLISHABLE_KEY` settings. Precedence: Gradle property,
environment variable, then ignored `local.properties`. Copy individual values
from `local.properties.example`. Legacy `WEGLOW_SUPABASE_*` values are accepted
only as a debug fallback. Release never falls back to development/the old backend.
BuildConfig exposes the environment; manifest/Supabase callback schemes agree.
Separate app IDs/FileProvider authorities allow builds to coexist.
[Android build variants](https://developer.android.com/build/build-variants).

Release is pinned to `https://nkrxygqtptvarntzxawk.supabase.co`; its publishable
key is configured locally. Staging requires its own URL/key and rejects production.
A separate staging project still needs configuration and verification: build
variants do not create hosted projects. Development should use a test backend.
The prior local backend configuration has an ignored rollback copy; the active
development and production configurations now use the new project.

To test the new backend in a debug build, explicitly set its DEV URL/key and add
`weglow-dev://login-callback` to that candidate project's allowed redirect URLs.
Production keeps `weglow://login-callback`. Staging's project needs its staging
redirect. Remove temporary test redirects when no longer needed. The Google HTTPS
callback belongs to the corresponding Supabase project, not the Android suffix.

## Release safeguards and signing

- `validateReleaseEnvironment`: HTTPS URL, approved project, publishable/anon
  key type. Legacy JWT keys must have role `anon` and match their project ref.
  Key values are not printed. This is format validation, not a live key test.
- `validateStagingEnvironment`: its backend is configured and separate.
- `validateReleaseSigning`: local/CI credentials or Android Studio injected
  signing must exist. Unsigned release attempts fail before release build work.
- Release remains non-debuggable with package `com.team03weglow.app` and the
  existing upload key. R8/shrinking remains at its previous setting.
- `WEGLOW_VERSION_CODE` is a positive integer, default 1; set it above all
  previously uploaded Play versions. `WEGLOW_VERSION_NAME` defaults to 1.0.

Use the existing keystore with Android Studio's signed-bundle wizard or ignored
`keystore.properties`. For CI signing, supported environment variables are
`WEGLOW_UPLOAD_STORE_FILE`, `WEGLOW_UPLOAD_STORE_PASSWORD`,
`WEGLOW_UPLOAD_KEY_ALIAS`, and `WEGLOW_UPLOAD_KEY_PASSWORD`. Store the file in
protected temporary storage and passwords in CI secrets. Never commit credentials
or put service-role/weather secrets in Android. Keep an independent upload-key
backup. Use `--no-configuration-cache` for signing builds.

## Commands and checks

```powershell
$env:JAVA_HOME=Join-Path $env:USERPROFILE '.jdks/temurin-25.0.4'
.\gradlew.bat :app:checkArchitecture :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
.\gradlew.bat :app:validateStagingEnvironment
.\gradlew.bat :app:validateReleaseEnvironment :app:validateReleaseSigning
.\gradlew.bat :app:bundleRelease :app:lintRelease --no-configuration-cache --no-daemon
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-WeGlowNativeAlignment.ps1 -ArtifactPath app/build/outputs/bundle/release/app-release.aab
```

Release commands require real configuration/signing. Verify a newly built AAB
with JDK `jarsigner -verify -verbose -certs`, bundletool and a Play testing track.
The alignment script checks 64-bit ELF LOAD segments only. Also verify APK ZIP
alignment and actual runtime behavior on a 16 KB device/emulator. The older signed
`app/release/app-release.aab` predates this work and is not the current candidate.

On this Windows machine Android Studio's bundled JBR has MSVC runtime 14.29,
whereas the installed Temurin 25.0.4 has 14.40. The desktop ONNX JVM test fails
initializing its DLL under that older JBR. Use the installed Temurin for the full
JVM check suite (and select it as the Gradle JDK in Android Studio). No vendor JDK
DLLs were replaced or removed. The artifact-checker's execution-policy override
is process-only and makes no persistent Windows policy change.
[ONNX maintainer explanation](https://github.com/microsoft/onnxruntime/issues/24287).

`.github/workflows/android-checks.yml` prepares the SDK and runs architecture,
JVM, debug lint and assembly checks on push/PR/manual dispatch. Optional DEV
values use repository variables. Tests use fixtures and need no signing secrets.
The workflow has not run on GitHub yet; production signing/deployment/upload
remain owner-controlled handoff steps.

## Hosted checkpoints and remaining launch gates

Owner reported: 342 hairstyles, zero products/incomplete rows; private bucket,
routine/deletion RPC and table-RLS checks true. Google provider, production
redirect, Gmail SMTP and recovery-code template are saved. These do not prove
successful delivery/login, image loading, isolation or deployed endpoint behavior.

Before calling this upload-ready:

1. Provision/configure staging. Test signup,
   confirmation, Google/email login, delivered recovery code, logout, session
   expiry and private profile uploads with destination builds.
2. Deploy `smart-endpoint`, `delete-account`, shared helpers and any required
   chat retirement stub. Configure destination runtime/weather secrets, verify
   gateway signing compatibility, weather, deletion and two-account isolation.
3. Resolve product-dependent launch UI: without product records the current
   Discover/recommendations/routine builder cannot populate. Combined progress
   cannot score without scheduled routine tasks. This is an application/content
   blocker, not an environment setting. Do not invent product/task records.
4. Verify hairstyle image delivery/rights; move or repoint old-project assets
   before retiring that backend.
5. Publish privacy and account-deletion request pages; confirm legal operator,
   contact, retention and SDK/Data safety disclosures. In-app privacy now
   describes cloud routine history and device-local notes.
6. Complete upload-key backup, Play App Signing, package/version checks, reviewer
   access, listing, audience/rating, Data safety and health declarations, and
   required testing/production-access steps for this developer account.
7. Test the signed candidate's permissions, offline/camera/ML behavior, API/native
   compatibility and Play pre-launch reports. API level alone is not approval.

Official references: [target API](https://support.google.com/googleplay/android-developer/answer/11926878),
[16 KB support](https://developer.android.com/guide/practices/page-sizes),
[account deletion](https://support.google.com/googleplay/android-developer/answer/13327111),
[health declaration](https://support.google.com/googleplay/android-developer/answer/14738291).
