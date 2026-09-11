# Phase 6 Follow-up — Camera Option for Profile Picture — Implementation Report

Branch: `phase-6-profile` (not committed, not pushed, not merged)

All five Gradle tasks were run. Only the pre-existing, unrelated
`YoloPostProcessorTest` fails (teammate scan/ONNX work — left untouched).

---

## 1. Exact files modified / created

### Created

| File | Purpose |
|---|---|
| `app/src/main/res/xml/file_paths.xml` | FileProvider config exposing exactly one path: `cache-path` → `profile-camera/` in internal cache. Nothing else (no files-dir, no external/SD). |
| `app/src/main/java/com/example/weglow/core/image/ProfileCameraImageStore.kt` | Owns the single throw-away camera capture file: create (clearing any leftover first), discard, ownership guard. Pure `java.io.File`. |
| `app/src/test/java/com/example/weglow/core/image/ProfileCameraImageStoreTest.kt` | 6 unit tests — temp-file location, per-capture freshness, leftover cleanup, safe/guarded discard. |
| `docs/phase-6/PHASE_6_FOLLOWUP_CAMERA.md` | Short follow-up notes. |
| `docs/phase-6/PHASE_6_FOLLOWUP_CAMERA_REPORT.md` | This report. |

### Modified

| File | Change |
|---|---|
| `app/src/main/AndroidManifest.xml` | Added a non-exported `androidx.core.content.FileProvider` (`${applicationId}.fileprovider`, `grantUriPermissions="true"`). **No new permissions.** |
| `app/src/main/java/com/example/weglow/ui/screens/ProfileScreen.kt` | Avatar tap now opens a `ModalBottomSheet` chooser (**Take Photo** / **Choose From Gallery**); camera launcher + permission handling + shared submit path; neutral inline notice for permission denial; new `ImageSourceRow` helper. |

`git diff --stat` (section 9) shows other files too — those are the
**uncommitted Phase 6 base work** already on this branch, untouched by this
follow-up except `ProfileScreen.kt` and `AndroidManifest.xml`.

---

## 2. Camera implementation approach

- System camera via `ActivityResultContracts.TakePicture()`
  (`MediaStore.ACTION_IMAGE_CAPTURE`) — a separate mechanism from the Scan
  feature's in-app CameraX preview; they share no code, no state, no lifecycle.
- Before launching, `ProfileCameraImageStore.createCaptureFile(context.cacheDir)`
  makes one empty file in `cacheDir/profile-camera/`; its `FileProvider` content
  URI is handed to the camera app (write grant added per-launch by the contract).
- `TakePicture` returns `Boolean saved`. On `true`, the file's bytes are read on
  `Dispatchers.IO` through the **existing** `ProfileImageReader.read(...)` and
  the resulting `ProfileImageUpload` goes to `onProfileImagePicked` — identical
  to the gallery path.
- `pendingCapturePath` is `rememberSaveable`, so a capture in progress survives
  configuration change / process death.

---

## 3. Permission handling

- `ContextCompat.checkSelfPermission(CAMERA)`; if not already granted,
  `ActivityResultContracts.RequestPermission()` for `Manifest.permission.CAMERA`
  — the permission already declared in the manifest for the Scan feature is
  reused, none added.
- Grant → camera launches immediately from the result callback.
- Denial → a neutral, non-red inline notice ("Camera access is off. You can
  still choose a photo from your gallery.") that auto-dismisses after 4s. No
  `ProfileViewModel` error, no crash, gallery still fully usable.

---

## 4. Temporary image handling

- One file per capture, only in `cacheDir/profile-camera/` (internal cache).
- Deleted in a `finally` block immediately after its bytes are read into
  `ProfileImageUpload` — on both the success and the unreadable branch.
- On cancellation / no capture, the file is discarded silently.
- `createCaptureFile` clears any leftover `capture_*.jpg` before creating a new
  one, so a killed-mid-capture orphan cannot accumulate.
- `discard()` refuses to touch anything that is not a `capture_*.jpg` file, and
  tolerates `null` / missing.
- The captured file is never shown as the avatar; the UI still only renders
  `profileState.profileImage`, which after persistence is the bytes downloaded
  from Supabase on `refresh()`.

---

## 5. How camera and gallery converge into the same upload pipeline

```
Take Photo  ─┐
             ├─►  ProfileImageReader.read(contentResolver, uri)   [IO: decode, <=1024px, JPEG q85]
Gallery ─────┘             │
                           v
                  ProfileImageUpload(bytes, "image/jpeg")
                           │  onProfileImagePicked(upload)   <- single entry point
                           v
     ProfileViewModel -> validate -> dup-tap guard -> ProfileImageRepository.uploadProfileImage(authUserId, upload)
                      -> private Supabase Storage bucket `profile-images`
                      -> profiles.profile_image_url -> downloadProfileImage -> ProfileAvatar
```

No new upload code, no second Supabase implementation, no global mutable state,
no user id supplied by the UI. The `profile-images` bucket, its RLS policies,
and the `<uid>/…` path model are unchanged. Duplicate uploads are still
prevented by the existing `isUploadingImage` guard in
`ProfileViewModel.onProfileImagePicked`; loading spinner and error text behavior
are unchanged.

---

## 6. Tests / results

| Task | Result |
|---|---|
| `./gradlew architectureCheck` | **PASS** — `ui`/`feature`/`domain` boundaries intact; camera glue stays in `ui`, temp-file helper in `core`. |
| `./gradlew compileDebugKotlin` | **PASS** |
| `./gradlew testDebugUnitTest` | **63 tests, 1 failed** — only `YoloPostProcessorTest.ignoresBelowThresholdInvalidAndPaddingOnlyDetections` (known unrelated teammate scan/ONNX failure, not modified). New `ProfileCameraImageStoreTest` = 6/6 pass; `ProfileViewModelTest` 22/22; `ProfileImageValidatorTest` 7/7. |
| `./gradlew lintDebug` | **PASS** (BUILD SUCCESSFUL). No new lint findings from the FileProvider, manifest, `ProfileScreen`, or `ProfileCameraImageStore` changes; all reported issues pre-date this work. |
| `./gradlew assembleDebug` | **PASS** — `app-debug.apk` built. Merged manifest confirmed: provider `com.example.weglow.fileprovider`, `exported=false`, `grantUriPermissions=true`; permissions still only `INTERNET` + `CAMERA`. |

---

## 7. Manual Android tests required

1. Avatar tap → bottom sheet shows **Take Photo** and **Choose From Gallery**.
2. Take Photo (first run) → CAMERA prompt → capture → image uploads → avatar
   updates → kill & relaunch → same picture loads from Supabase.
3. Deny CAMERA → neutral notice appears, gallery path still works.
4. Open camera, press Back → returns with no error and no avatar change.
5. Replace an existing picture via camera → previous storage object deleted,
   `profiles.profile_image_url` points to the new one.
6. After a completed capture, `cacheDir/profile-camera/` contains no file.
7. Gallery (Photo Picker) still works exactly as before.
8. Second account cannot read the first account's object (RLS unchanged).

---

## 8. git status

```
On branch phase-6-profile
Your branch is up to date with 'origin/phase-6-profile'.

Changes not staged for commit:
	modified:   app/build.gradle.kts
	modified:   app/src/main/AndroidManifest.xml
	modified:   app/src/main/java/com/example/weglow/app/AppContainer.kt
	modified:   app/src/main/java/com/example/weglow/data/model/ProfileRow.kt
	modified:   app/src/main/java/com/example/weglow/data/remote/supabase/SupabaseClientProvider.kt
	modified:   app/src/main/java/com/example/weglow/data/repository/SupabaseProfileRepository.kt
	modified:   app/src/main/java/com/example/weglow/domain/model/UserProfile.kt
	modified:   app/src/main/java/com/example/weglow/domain/repository/ProfileRepository.kt
	modified:   app/src/main/java/com/example/weglow/feature/profile/ProfileViewModel.kt
	modified:   app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt
	modified:   app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt
	modified:   app/src/main/java/com/example/weglow/ui/screens/ProfileScreen.kt
	modified:   app/src/test/java/com/example/weglow/ArchitectureFoundationTest.kt
	modified:   app/src/test/java/com/example/weglow/data/repository/SupabaseProfileRepositoryTest.kt
	modified:   app/src/test/java/com/example/weglow/feature/auth/AuthViewModelTest.kt
	modified:   app/src/test/java/com/example/weglow/feature/onboarding/OnboardingViewModelTest.kt
	modified:   gradle/libs.versions.toml

Untracked files:
	app/src/main/java/com/example/weglow/core/image/ProfileCameraImageStore.kt
	app/src/main/java/com/example/weglow/core/image/ProfileImageReader.kt
	app/src/main/java/com/example/weglow/data/repository/SupabaseProfileImageRepository.kt
	app/src/main/java/com/example/weglow/domain/model/ProfileImage.kt
	app/src/main/java/com/example/weglow/domain/model/ProfileImageValidation.kt
	app/src/main/java/com/example/weglow/domain/repository/ProfileImageRepository.kt
	app/src/main/java/com/example/weglow/ui/components/ProfileAvatar.kt
	app/src/main/res/xml/file_paths.xml
	app/src/test/java/com/example/weglow/core/
	app/src/test/java/com/example/weglow/domain/
	app/src/test/java/com/example/weglow/feature/profile/
	docs/phase-5/PHASE_5_FINAL_AUDIT.pdf
	docs/phase-5/PHASE_5_IMPLEMENTATION_REPORT.html
	docs/phase-5/PHASE_5_IMPLEMENTATION_REPORT.pdf
	docs/phase-6/
	supabase/migrations/20260911000000_profile_images_storage.sql

no changes added to commit (use "git add" and/or "git commit -a")
```

*This follow-up added only: `AndroidManifest.xml` + `ProfileScreen.kt` edits,
and the new `ProfileCameraImageStore.kt`, `file_paths.xml`, `core/` test dir,
and the `docs/phase-6/` follow-up docs. Everything else was the already-
uncommitted Phase 6 base.*

---

## 9. git diff --stat

```
 app/build.gradle.kts                               |   1 +
 app/src/main/AndroidManifest.xml                   |  16 ++
 .../java/com/example/weglow/app/AppContainer.kt    |   3 +
 .../com/example/weglow/data/model/ProfileRow.kt    |   3 +
 .../data/remote/supabase/SupabaseClientProvider.kt |   4 +
 .../data/repository/SupabaseProfileRepository.kt   |  15 ++
 .../com/example/weglow/domain/model/UserProfile.kt |   7 +
 .../weglow/domain/repository/ProfileRepository.kt  |   8 +
 .../weglow/feature/profile/ProfileViewModel.kt     | 156 +++++++++++-
 .../com/example/weglow/navigation/WeGlowApp.kt     |  10 +-
 .../com/example/weglow/ui/screens/HomeScreen.kt    |  19 +-
 .../com/example/weglow/ui/screens/ProfileScreen.kt | 267 +++++++++++++++++++--
 .../example/weglow/ArchitectureFoundationTest.kt   |   2 +
 .../repository/SupabaseProfileRepositoryTest.kt    |  21 ++
 .../weglow/feature/auth/AuthViewModelTest.kt       |   6 +
 .../feature/onboarding/OnboardingViewModelTest.kt  |   3 +
 gradle/libs.versions.toml                          |   1 +
 17 files changed, 498 insertions(+), 44 deletions(-)
```

*(Tracked-file stat only; new untracked files are not shown by `--stat`.)*

---

Nothing committed, pushed, or merged. Stopped after implementation and verification.
