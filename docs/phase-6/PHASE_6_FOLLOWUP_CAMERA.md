# Phase 6 follow-up — camera option for the profile picture

Branch: `phase-6-profile` (not committed, not pushed, not merged)

Adds a **Take Photo** source alongside the existing **Choose From Gallery**
(modern Photo Picker) for the profile avatar. Both sources feed the unchanged
Phase 6 pipeline.

## What changed

| File | Change |
|---|---|
| `app/src/main/AndroidManifest.xml` | Added a non-exported `androidx.core.content.FileProvider` (`${applicationId}.fileprovider`). **No new permissions** — the existing `CAMERA` permission is reused. |
| `app/src/main/res/xml/file_paths.xml` | **New.** Exposes exactly one path: `cache-path` → `profile-camera/` inside internal cache. No files-dir, no external/SD storage. |
| `app/src/main/java/com/example/weglow/core/image/ProfileCameraImageStore.kt` | **New.** Creates / discards the single throw-away capture file; clears leftovers before each new capture. Pure `java.io.File`, unit-tested. |
| `app/src/main/java/com/example/weglow/ui/screens/ProfileScreen.kt` | Tapping the avatar now opens a `ModalBottomSheet` with **Take Photo** / **Choose From Gallery**. Camera path: runtime `CAMERA` permission check → `ActivityResultContracts.TakePicture` → `ProfileImageReader` → `onProfileImagePicked`. Gallery path unchanged. |
| `app/src/test/java/com/example/weglow/core/image/ProfileCameraImageStoreTest.kt` | **New.** 6 tests for temp-file location, freshness, leftover cleanup, and safe discard. |

## Convergence

```
Take Photo ─┐
            ├─► ProfileImageReader.read(contentResolver, uri)   (Dispatchers.IO: decode, ≤1024px, JPEG q85)
Gallery ────┘        │
                     ▼
             ProfileImageUpload(bytes, "image/jpeg")
                     │  onProfileImagePicked(...)
                     ▼
   ProfileViewModel  → validate → ProfileImageRepository.uploadProfileImage(authUserId, upload)
                     → private Supabase Storage bucket `profile-images`
                     → profiles.profile_image_url  → download → ProfileAvatar
```

No new upload code, no new repository, no global mutable state, no user id from
the UI. The `profile-images` bucket, its RLS policies and the `<uid>/…` path
model are untouched.

## Camera specifics

- **Permission:** `ContextCompat.checkSelfPermission(CAMERA)`; if not granted,
  `ActivityResultContracts.RequestPermission`. Denial shows a neutral inline
  notice ("Camera access is off. You can still choose a photo from your
  gallery.") — never a ViewModel error.
- **Cancellation:** `TakePicture` returns `false` → temp file discarded
  silently, no error.
- **Temp files:** one file per capture in `cacheDir/profile-camera/`, deleted
  immediately after its bytes are read; any leftover is cleared before the next
  capture. `pendingCapturePath` is `rememberSaveable` so a capture survives
  configuration change / process death.
- **FileProvider:** `exported="false"`, `grantUriPermissions="true"`; the URI is
  granted per-launch to the camera app only. The `file_paths.xml` root is a
  single cache sub-directory.
- **Scan camera untouched:** the scan feature keeps its own CameraX in-app
  preview; this uses the system camera activity and shares nothing with it.

## Manual device tests still required

1. Avatar tap → sheet shows both options.
2. Take Photo, first run → CAMERA permission prompt → capture → image uploads →
   avatar updates → kill/relaunch app → same picture loads from Supabase.
3. Deny the permission → neutral notice, gallery still works.
4. Open camera, press back → returns with no error, no avatar change.
5. Replace an existing picture via camera → old storage object deleted, new one
   referenced in `profiles.profile_image_url`.
6. Confirm `cacheDir/profile-camera/` holds no file after a completed capture.
7. Second account cannot read the first account's object (RLS unchanged).
