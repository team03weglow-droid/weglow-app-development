# Phase 6 — Profile + Profile Picture Implementation Report

Branch: `phase-6-profile` (not committed, not pushed, not merged)

## Status

**PHASE 6 IMPLEMENTATION STATUS: COMPLETE (code + automated verification).**

Not yet production-verified: the real-device round trip
(device image → Supabase Storage → `profiles.profile_image_url` → display →
app restart → logout/login → storage-security check) must still be performed
manually by the team, and the Supabase Storage bucket + policies must be
provisioned in the live project (see "Supabase steps you must perform").

---

## 1. Architecture

Unchanged and preserved:

```
UI (Compose)  →  ViewModel/state  →  domain repository interfaces  →  data impls  →  Supabase
```

- Supabase Storage access lives only in the data layer
  (`SupabaseProfileImageRepository`).
- `feature` and `ui` never import `io.github.jan.supabase.*` or
  `com.example.weglow.data.remote.*` (`architectureCheck` still passes).
- No global mutable state. The picture bytes flow through `ProfileUiState`.
- No user id is passed from the UI for authorization: `ProfileViewModel`
  resolves identity through `AuthRepository.currentUserId()`.
- Android `Uri` never crosses into domain/data. `ProfileImageReader`
  (an Android-facing `core` helper) reads the picked `Uri`, decodes, down-scales
  and re-encodes to JPEG, and hands a neutral `ProfileImageUpload(bytes, mimeType)`
  into the ViewModel.

---

## 2. Files created

| File | Purpose |
|---|---|
| `app/src/main/java/com/example/weglow/domain/model/ProfileImage.kt` | `ProfileImageUpload` neutral value type (bytes + mimeType). |
| `app/src/main/java/com/example/weglow/domain/model/ProfileImageValidation.kt` | `ProfileImageValidator` — pure size/MIME/magic-byte validation + `ProfileImageValidation` result. |
| `app/src/main/java/com/example/weglow/domain/repository/ProfileImageRepository.kt` | Domain contract for upload / download / delete of the binary asset. |
| `app/src/main/java/com/example/weglow/data/repository/SupabaseProfileImageRepository.kt` | Supabase Storage implementation (private bucket `profile-images`). |
| `app/src/main/java/com/example/weglow/core/image/ProfileImageReader.kt` | Android boundary: `Uri` → validated, down-scaled JPEG bytes. |
| `app/src/main/java/com/example/weglow/ui/components/ProfileAvatar.kt` | `ProfileAvatar` composable + `rememberDecodedBitmap` (bytes → `ImageBitmap`, placeholder when absent). |
| `supabase/migrations/20260911000000_profile_images_storage.sql` | New forward migration: private bucket + per-user `storage.objects` RLS. |
| `app/src/test/java/com/example/weglow/domain/model/ProfileImageValidatorTest.kt` | 7 validation tests. |
| `app/src/test/java/com/example/weglow/feature/profile/ProfileViewModelTest.kt` | 14 ViewModel tests (load, upload, failure, rollback, identity, name). |
| `docs/phase-6/PHASE_6_IMPLEMENTATION_REPORT.md` | This report. |

## 3. Files modified

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | Added `supabase-storage` (`storage-kt`, same BOM 3.0.0). |
| `app/build.gradle.kts` | `implementation(libs.supabase.storage)`. |
| `app/src/main/java/com/example/weglow/data/remote/supabase/SupabaseClientProvider.kt` | `install(Storage)`. |
| `app/src/main/java/com/example/weglow/domain/model/UserProfile.kt` | Added `profileImagePath: String?` (maps to `profile_image_url`; a storage path, not a signed URL). |
| `app/src/main/java/com/example/weglow/data/model/ProfileRow.kt` | Added `profile_image_url` + mappings. |
| `app/src/main/java/com/example/weglow/domain/repository/ProfileRepository.kt` | Added `updateProfileImagePath(userId, path)` — targeted single-column update. |
| `app/src/main/java/com/example/weglow/data/repository/SupabaseProfileRepository.kt` | Implemented `updateProfileImagePath` via postgrest `update { set(...) }` (does **not** use the `saveProfile` upsert, so a picture change never blanks name/onboarding columns). |
| `app/src/main/java/com/example/weglow/app/AppContainer.kt` | Added `profileImageRepository`. |
| `app/src/main/java/com/example/weglow/feature/profile/ProfileViewModel.kt` | Loads persisted picture, coordinates validate → upload → persist → display, with rollback and duplicate-tap guard. |
| `app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt` | Wires the new repo into `ProfileViewModel`; passes image state to Profile and Home. |
| `app/src/main/java/com/example/weglow/ui/screens/ProfileScreen.kt` | Tap avatar → Photo Picker; upload spinner overlay; error text; renders persisted picture. Layout otherwise unchanged. |
| `app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt` | The existing top-right avatar now shows the same persisted picture (no redesign). |
| `app/src/test/java/.../AuthViewModelTest.kt`, `.../OnboardingViewModelTest.kt`, `.../ArchitectureFoundationTest.kt`, `.../SupabaseProfileRepositoryTest.kt` | Updated fakes for the new interface method; added `profile_image_url` mapping coverage. |

---

## 4. Supabase Storage bucket required

- **Bucket id / name:** `profile-images`
- **Private:** yes (`public = false`)
- **File size limit:** 5 MiB (`5242880`) — mirrors `ProfileImageValidator.MAX_BYTES`
- **Allowed MIME types:** `image/jpeg`, `image/png`, `image/webp`
- **Object path convention:** `<auth.uid()>/<epochMillis>.jpg`

## 5. SQL / migration created

`supabase/migrations/20260911000000_profile_images_storage.sql` — new, additive,
non-destructive, idempotent (`on conflict do update`, `drop policy if exists`).
It does **not** touch `public.profiles` (the `profile_image_url` column already
exists from `20260910010000_reconcile_profiles_schema.sql`) and does not modify
any earlier migration.

It is **not** executed automatically. Apply it manually (see section below).

## 6. Storage policies created / required

Four policies on `storage.objects`, all `to authenticated`, all scoped to
`bucket_id = 'profile-images' AND (storage.foldername(name))[1] = (select auth.uid()::text)`:

| Policy | Operation | Effect |
|---|---|---|
| `profile-images read own` | `SELECT` | user reads only files in their own folder |
| `profile-images insert own` | `INSERT` (`with check`) | user creates only in their own folder |
| `profile-images update own` | `UPDATE` (`using` + `with check`) | user overwrites only their own files |
| `profile-images delete own` | `DELETE` | user deletes only their own files |

Consequences: one user cannot read, overwrite, or delete another user's picture,
and cannot write outside their `<uid>/` prefix. The Kotlin path derivation is
defence in depth, not the security boundary.

The existing `public.profiles` RLS (per-user `SELECT/INSERT/UPDATE` on
`auth.uid() = id`) is unchanged and not weakened.

---

## 7. Profile picture selection flow

1. User taps the 120 dp avatar on Profile (disabled while an upload is running).
2. Android **Photo Picker** (`ActivityResultContracts.PickVisualMedia`, `ImageOnly`)
   opens — no `READ_MEDIA_IMAGES` / storage permission is requested or declared.
3. `ProfileImageReader.read(contentResolver, uri)` (on `Dispatchers.IO`):
   reads bytes, decodes, down-scales longest edge to ≤ 1024 px, re-encodes JPEG
   q85. A file that cannot be decoded returns `null` → `onProfileImageUnreadable()`.
4. Neutral `ProfileImageUpload(jpegBytes, "image/jpeg")` is handed to the ViewModel.

## 8. Upload flow

`ProfileViewModel.onProfileImagePicked(upload)`:

1. If an upload is already in flight → **ignored** (duplicate-tap guard).
2. `ProfileImageValidator.validate(upload)` — empty / > 5 MiB / disallowed MIME /
   failed magic-byte sniff → set `imageError`, stop. Nothing is uploaded.
3. `userId = authRepository.currentUserId()`; `null` → error, stop.
4. `isUploadingImage = true` (spinner overlay on the avatar).
5. `profileImageRepository.uploadProfileImage(userId, upload)` puts the object at
   `<userId>/<epochMillis>.jpg` in the private bucket. Failure → `imageError`,
   `isUploadingImage = false`, **no** DB write, **no** success.
6. `profileRepository.updateProfileImagePath(userId, uploadedPath)` writes only
   the `profile_image_url` column.
7. On DB failure → delete the just-uploaded object (rollback), set `imageError`,
   report failure. No false success.
8. On DB success → if there was a previous object and it differs, delete it
   (orphan cleanup), then set `profileImagePath` + `profileImage` bytes and clear
   the spinner.

## 9. Database persistence behavior

- The picture reference persists in `public.profiles.profile_image_url` as a
  **storage object path** (e.g. `d290.../1725900000000.jpg`), never a signed URL.
- The write is a targeted `UPDATE ... SET profile_image_url = ...` filtered by
  `id = userId`; it never goes through the onboarding upsert, so `full_name`,
  onboarding answers and `onboarding_completed` are untouched.
- Success is reported to the UI only after this write succeeds.

## 10. Private image retrieval / display behavior

- On Profile open, Home open, app restart, and logout/login, `ProfileViewModel.refresh()`:
  1. loads the profile row for the authenticated user,
  2. reads `profileImagePath`,
  3. if present, calls `profileImageRepository.downloadProfileImage(path)` which
     uses `storage.from("profile-images").downloadAuthenticated(path)` — an
     authenticated GET with the user's JWT; **no signed URL is created, stored,
     or exposed**,
  4. puts the bytes in `ProfileUiState.profileImage`.
- `ProfileAvatar` decodes the bytes to an `ImageBitmap`; if there is no path or
  the download/decode fails, it shows a neutral person-icon placeholder.
- The Home top-right avatar is bound to the same `profileState.profileImage`.
- The Android Photo Picker `Uri` is **not** retained after upload; display always
  goes through the persisted storage path.

## 11. Security behavior

- Private bucket; images are never publicly reachable.
- Per-user `storage.objects` RLS: read/insert/update/delete confined to
  `<auth.uid()>/…`. Server-side, not Android-side.
- Upload path is derived from the **session** user id, never from a UI-supplied id.
- No service-role key anywhere; only the existing publishable key is used.
- On-device validation (size, MIME allow-list, magic bytes) plus the bucket's own
  `file_size_limit` + `allowed_mime_types` as a second gate.
- Existing `profiles` RLS unchanged.

## 12. Tests added / updated

`./gradlew testDebugUnitTest` → **57 tests, 1 failed**. The only failure is the
pre-existing `YoloPostProcessorTest.ignoresBelowThresholdInvalidAndPaddingOnlyDetections`
(teammate scan/ONNX work — untouched by Phase 6, and failing on the baseline
before any Phase 6 change).

New / updated:

- `ProfileViewModelTest` (14): loads persisted name; resolves persisted image
  reference; no-image state (no download attempted); upload requires an
  authenticated user; upload uses the **session** user id for the storage path;
  successful upload persists the reference and updates state; failed Storage
  upload does not persist and does not report success; failed DB update rolls the
  object back and reports the real error; replacing an image deletes the previous
  object; sign-out refresh resets state; `consumeImageError` clears; invalid
  image rejected before any upload; duplicate taps ignored while uploading;
  fallback to identity display name preserved.
- `ProfileImageValidatorTest` (7): valid JPEG/PNG/WebP; MIME with charset suffix;
  empty; unsupported MIME; oversize; declared-image-but-non-image bytes.
- `SupabaseProfileRepositoryTest` (+2): `profile_image_url` round-trips; missing
  column maps to null.
- `ArchitectureFoundationTest`: mapping now asserts `profile_image_url`.
- All tests use fakes at the repository boundary; none touch the network.

Architecture: `./gradlew architectureCheck` → **PASS** (ui/feature free of
Supabase + `data.remote`; domain free of Android/Compose/Supabase).

## 13. Gradle verification results

Command: `./gradlew clean architectureCheck compileDebugKotlin lintDebug assembleDebug`
plus `./gradlew testDebugUnitTest`.

| Step | Result |
|---|---|
| `architectureCheck` | **PASS** |
| `compileDebugKotlin` | **PASS** (only pre-existing deprecation warnings in unrelated Scan screens) |
| Phase 6 unit tests | **PASS** — `ProfileViewModelTest` 14/14, `ProfileImageValidatorTest` 7/7, `SupabaseProfileRepositoryTest` 8/8, `ArchitectureFoundationTest` 1/1 |
| Pre-existing YOLO test | **FAIL** (`YoloPostProcessorTest`, 1) — unrelated, teammate scan/ONNX, failing on baseline; not caused by Phase 6 |
| `lintDebug` | **PASS** — 0 errors, 88 warnings (87 pre-existing; 1 new: a `UseKtx` style hint suggesting `Bitmap.scale` in `ProfileImageReader.kt`) |
| `assembleDebug` | **PASS** — `app/build/outputs/apk/debug/app-debug.apk` produced |

## 14. Blocking issues

None in code. **External prerequisite before the feature works end-to-end:** the
`profile-images` bucket and its four `storage.objects` policies must be created in
the live Supabase project (migration provided; not auto-run).

## 15. Non-blocking issues

- `ProfileImageReader.kt` lint hint: prefer the `Bitmap.scale` KTX extension over
  `Bitmap.createScaledBitmap`. Cosmetic; left as-is to match `ScanPhotoDecoder`'s
  existing style.
- On hosted Supabase, `storage.buckets` / `storage.objects` are owned by
  `supabase_storage_admin`; if your migration runner lacks that privilege, use
  the Dashboard steps below instead of the SQL file.
- `SupabaseProfileImageRepository.deleteProfileImage` failures during
  cleanup/rollback are swallowed (best-effort); a rare orphan object is possible
  if a delete call itself fails.
- No dark-mode / large-image memory profiling was done for the decoded bitmap
  (bounded to ≤ 1024 px, so low risk).

---

## Supabase steps you must perform (pick ONE)

### Option A — run the migration SQL

In the Supabase Dashboard → SQL Editor, paste and run the contents of
`supabase/migrations/20260911000000_profile_images_storage.sql`.
(Equivalently, apply it through your normal migration tooling. It is additive and
idempotent.)

### Option B — Dashboard, manual

1. **Storage → Buckets → New bucket**
   - Name: `profile-images`
   - Public bucket: **off**
   - File size limit: `5 MB`
   - Allowed MIME types: `image/jpeg, image/png, image/webp`
2. **Storage → Policies → `profile-images` → New policy** (create four,
   `Target roles: authenticated`):
   - **SELECT** — `USING`:
     `bucket_id = 'profile-images' and (storage.foldername(name))[1] = (select auth.uid()::text)`
   - **INSERT** — `WITH CHECK`: same expression
   - **UPDATE** — `USING` and `WITH CHECK`: same expression
   - **DELETE** — `USING`: same expression

Do **not** make the bucket public.

---

## Manual Android test plan (team)

1. Launch the app, sign in to an existing account that finished onboarding.
2. Go to the **Profile** tab. Confirm the **persisted real name** shows (never
   "Team 03"). If no picture was ever set, confirm the placeholder avatar.
3. Tap the large circular avatar.
4. In the Photo Picker, choose a real photo from the device.
5. Confirm the spinner overlay appears, then the new picture replaces the avatar.
6. In the Supabase Dashboard → **Storage → `profile-images`**, confirm an object
   exists at `<yourUserId>/<number>.jpg`.
7. In **Table editor → `profiles`**, confirm your row's `profile_image_url` now
   holds that same `<yourUserId>/<number>.jpg` path.
8. Confirm the picture is visible on Profile (and in the Home top-right avatar).
9. Navigate to another tab and back to Profile — picture still shows.
10. Kill and relaunch the app — after loading, Profile shows the same picture.
11. Tap **Log Out**.
12. Log in again to the **same** account.
13. Confirm the same picture returns on Profile and Home.
14. **Security check:** sign in as a *different* user (User B) and, using a REST
    client with User B's access token, attempt:
    - `GET .../storage/v1/object/authenticated/profile-images/<userA>/<file>.jpg` → expect **403/404**
    - upload to `profile-images/<userA>/x.jpg` → expect **403**
    - delete `profile-images/<userA>/<file>.jpg` → expect **403**
    Also confirm User B uploading to `profile-images/<userB>/...` succeeds and
    does not affect User A's object.

### Replace / oversize / bad-input checks

15. Set a second picture; confirm the avatar updates and the **previous** Storage
    object for your folder is gone (orphan cleanup).
16. Try picking a very large image (> 5 MB source) — it is down-scaled locally to
    JPEG ≤ ~1024 px and should still upload; a pathological non-image chosen via a
    file provider should surface an error, not upload.

---

## Not done (by instruction / scope)

- No commit, push, or merge.
- No changes to `YoloPostProcessor*`, the acne ONNX model, scan repository,
  scan UI internals, or backend Python.
- No production SQL executed.
- No name-editing UI added (the current Profile design has no name field; Phase 5
  name behavior is preserved as-is).
- No new project, folder, or ZIP.
