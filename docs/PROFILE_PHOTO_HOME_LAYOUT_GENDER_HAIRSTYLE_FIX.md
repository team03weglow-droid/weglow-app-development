# Profile Photo Rotation, Home Layout, and Gender/Hairstyle Fix

Fixes five issues in the existing WeGlow app: profile photo orientation, Home
quick-action sizing, "Curated For You" card clipping, editable Profile gender,
and hairstyle recommendations using the current profile gender instead of a
stale onboarding snapshot.

No redesign, no new project, no schema migration, nothing committed/pushed —
all changes are left in the working tree for review.

## 1. Root causes

### Profile photo rotation
`ProfileImageReader.read()` (`core/image/ProfileImageReader.kt`) decoded the
picked/captured bytes with plain `BitmapFactory` and re-encoded to JPEG
**without ever reading the EXIF orientation tag**. Camera and gallery photos
are frequently stored "sideways" with orientation metadata describing the
correction; since that tag was discarded, the persisted picture could come
out rotated. (The *scan* photo flow's `ScanPhotoDecoder` already handled this
correctly — it just wasn't reused for profile pictures.)

### Home quick-action sizing
`HomeShortcut` cards used `Modifier.weight(1f)` for equal *width* but had no
equal-*height* constraint. Each `Card` sized itself to its own two-line
label, so different text-wrap behavior (font scale, device width) made the
three cards visually inconsistent.

### Curated For You clipping
`HomeEditorial`'s title `Text` had no `maxLines`, so a long title (e.g. "The
Complete Guide to Layering Skincare Products") could wrap to 2–3 lines while
a short title stayed at 1 line, making cards in the same `LazyRow` different
heights.

### Gender storage and hairstyle staleness
Gender is already a first-class column: `profiles.gender`, mapped by
`UserProfile` / `ProfileRow`. Onboarding writes it via
`OnboardingViewModel.setGender` → `ProfileRepository.saveProfile`. There was
no way to edit it from Profile, and — more importantly — the hairstyle scan
flow (`WeGlowApp.kt`) sourced gender from `onboardingState.gender`, a
**one-shot in-memory snapshot that is never refreshed after onboarding**.
The actual Supabase recommendation query in `SupabaseHairstyleRepository`
already re-reads `profile.gender` fresh at query time, so filtering itself
was correct — but the wiring upstream was stale and misleading.

## 2. Implementation

| Task | Change |
|---|---|
| Photo rotation | `ProfileImageReader` reads the EXIF `TAG_ORIENTATION` from the decoded bytes and applies the correct `Matrix` transform (all 8 EXIF orientations, including mirrored/transposed) *after* the existing sample-size downscale, so no extra memory is used. |
| Home quick actions | The shortcuts `Row` now uses `Modifier.height(IntrinsicSize.Min)`; each `HomeShortcut` adds `fillMaxHeight()` plus `maxLines`/`overflow` on its text, so all three always share one geometry. |
| Curated For You | `HomeEditorial`'s title now uses `minLines = 2, maxLines = 2` (reserves a fixed two-line area, truncates longer titles gracefully); category text gets `maxLines = 1`. |
| Editable gender | Added `Gender.OPTIONS` (shared by onboarding and Profile — single source of truth for allowed values), `ProfileRepository.updateGender` / `SupabaseProfileRepository` targeted single-column update, `ProfileViewModel` gender state + `onGenderSelected` / `consumeGenderError` (loading state, no false-success on failure, no-op on unchanged value), and a new "Personal details" row + bottom sheet in `ProfileScreen`. |
| Hairstyle uses current gender | `WeGlowApp.kt` now passes `profileState.gender` (not `onboardingState.gender`) into `scanFlowViewModel.onPhotoReady` / `retryHairstyle`, and refreshes the profile on Scan entry so a gender change made in Profile is picked up immediately — no sign-out or re-onboarding required. |

## 3. How gender was previously stored, and how it flows now

- Stored the same way both before and after this change: `profiles.gender`
  column, via `UserProfile.gender` / `ProfileRow.gender`.
- Before: Profile had no editor; the hairstyle flow read a stale in-memory
  copy captured once during onboarding.
- After: Profile edits go through `ProfileViewModel.onGenderSelected` →
  `ProfileRepository.updateGender` → same `profiles.gender` column. The
  hairstyle flow reads `profileState.gender`, which is loaded by
  `ProfileViewModel.refresh()` on every entry into Home/Discover/Routines/Scan
  and updated locally only after a successful write — so it is always the
  current persisted value, with no second source of truth introduced.

## 4. Files modified

- `app/src/main/java/com/example/weglow/core/image/ProfileImageReader.kt`
- `app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/example/weglow/ui/screens/ProfileScreen.kt`
- `app/src/main/java/com/example/weglow/ui/screens/GenderSelectionScreen.kt`
- `app/src/main/java/com/example/weglow/feature/profile/ProfileViewModel.kt`
- `app/src/main/java/com/example/weglow/domain/repository/ProfileRepository.kt`
- `app/src/main/java/com/example/weglow/data/repository/SupabaseProfileRepository.kt`
- `app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt`
- `app/src/main/java/com/example/weglow/domain/model/Gender.kt` (new)
- Test fakes updated for the new `ProfileRepository.updateGender` method:
  `AuthViewModelTest.kt`, `OnboardingViewModelTest.kt`,
  `RecommendationViewModelTest.kt`, `RoutineViewModelTest.kt`,
  `ProfileViewModelTest.kt`

## 5. Tests added

7 new cases in `ProfileViewModelTest`:
- gender loaded on `refresh()`
- successful gender update persists and updates state
- failed gender update does not falsely show the new value
- update without an authenticated user is rejected
- re-selecting the already-saved value is a no-op (no redundant write)
- `consumeGenderError` clears the error

**Not unit-tested:** `ProfileImageReader`'s bitmap/EXIF logic uses real
`android.graphics` / `android.media` classes and this project has no
Robolectric configured, matching the existing untested convention for
`ScanPhotoDecoder`. Verify orientation manually per the checklist below.

## 6. Gradle commands run

```
./gradlew architectureCheck testDebugUnitTest   → BUILD SUCCESSFUL
./gradlew lintDebug                             → BUILD SUCCESSFUL
./gradlew assembleDebug                         → BUILD SUCCESSFUL
```

`lintDebug` initially failed on one **pre-existing, unrelated** issue in
`HomeScreen.kt` (`NonObservableLocale`: a composable read `Locale.getDefault()`
directly instead of `LocalConfiguration.current`). Since it was in a file
already touched for this work and was blocking the requested lint run, it was
fixed in place (switched to `LocalConfiguration.current.locales[0]`) rather
than left failing.

## 7. Remaining risks / manual checks

- Orientation fix is logic-verified against the same matrix table already
  proven in `ScanPhotoDecoder`, but should be confirmed on a physical device
  (no instrumented test was added for it).
- No database migration was needed — `profiles.gender` already existed.

## 8. Manual test checklist

**A. Profile photo**
- [ ] Camera portrait photo → correct orientation
- [ ] Camera landscape photo → correct orientation
- [ ] Gallery portrait photo → correct orientation
- [ ] Gallery landscape photo → correct orientation
- [ ] Close/reopen Profile → orientation still correct

**B. Home**
- [ ] All 3 quick actions have consistent width, height, padding, alignment
- [ ] Curated For You cards have identical geometry
- [ ] A long guide title truncates cleanly, doesn't break card layout
- [ ] Horizontal scrolling still works, partial next-card peek is fine

**C. Gender**
- [ ] Current gender appears correctly on Profile
- [ ] Change gender via the new picker
- [ ] Leave Profile, return → persisted value shown
- [ ] Restart app → persisted value remains

**D. Hairstyle**
- [ ] Set gender to Male, run a hairstyle/face-shape analysis → Male + detected face shape
- [ ] Change gender to Female (no sign-out/onboarding)
- [ ] Run another analysis → now uses Female + detected face shape

## 9. Diff summary

```
$ git diff --stat
 .../weglow/core/image/ProfileImageReader.kt        |  64 +++++++-
 .../data/repository/SupabaseProfileRepository.kt   |  14 ++
 .../weglow/domain/repository/ProfileRepository.kt  |   9 ++
 .../weglow/feature/profile/ProfileViewModel.kt     |  61 ++++++++
 .../com/example/weglow/navigation/WeGlowApp.kt     |  21 ++-
 .../weglow/ui/screens/GenderSelectionScreen.kt     |   6 +-
 .../com/example/weglow/ui/screens/HomeScreen.kt    |  75 +++++++--
 .../com/example/weglow/ui/screens/ProfileScreen.kt | 125 +++++++++++++++
 .../weglow/feature/auth/AuthViewModelTest.kt       |   3 +
 .../feature/onboarding/OnboardingViewModelTest.kt  |   3 +
 .../weglow/feature/profile/ProfileViewModelTest.kt | 109 +++++++++++++
 .../recommendation/RecommendationViewModelTest.kt  |   1 +
 .../weglow/feature/routine/RoutineViewModelTest.kt |   1 +
 docs/ARCHITECTURE_REVIEW.md                        | 170 ++++++++++-----------
 14 files changed, 552 insertions(+), 110 deletions(-)
```

> `docs/ARCHITECTURE_REVIEW.md`'s modification predates this session and was
> not touched as part of this work.

```
$ git status --short
 M app/src/main/java/com/example/weglow/core/image/ProfileImageReader.kt
 M app/src/main/java/com/example/weglow/data/repository/SupabaseProfileRepository.kt
 M app/src/main/java/com/example/weglow/domain/repository/ProfileRepository.kt
 M app/src/main/java/com/example/weglow/feature/profile/ProfileViewModel.kt
 M app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt
 M app/src/main/java/com/example/weglow/ui/screens/GenderSelectionScreen.kt
 M app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt
 M app/src/main/java/com/example/weglow/ui/screens/ProfileScreen.kt
 M app/src/test/java/com/example/weglow/feature/auth/AuthViewModelTest.kt
 M app/src/test/java/com/example/weglow/feature/onboarding/OnboardingViewModelTest.kt
 M app/src/test/java/com/example/weglow/feature/profile/ProfileViewModelTest.kt
 M app/src/test/java/com/example/weglow/feature/recommendation/RecommendationViewModelTest.kt
 M app/src/test/java/com/example/weglow/feature/routine/RoutineViewModelTest.kt
 M docs/ARCHITECTURE_REVIEW.md
?? app/src/main/java/com/example/weglow/domain/model/Gender.kt
?? docs/16KB_PAGE_SIZE_COMPATIBILITY_IMPLEMENTATION.md
?? docs/16KB_PAGE_SIZE_COMPATIBILITY_INVESTIGATION.md
?? docs/16KB_PAGE_SIZE_COMPATIBILITY_POST_MERGE_VERIFICATION.md
?? docs/ARCHITECTURAL_VIOLATIONS.md
?? docs/BOTTOM_NAV_SCROLL_RESET_FIX.md
?? docs/DISCOVER_PRICE_FILTER_IMPLEMENTATION.md
?? docs/DISCOVER_PRICE_FILTER_INVESTIGATION.md
?? docs/DISCOVER_PRICE_FILTER_POST_MERGE_VERIFICATION.md
?? docs/HOME_AVATAR_PROFILE_NAVIGATION_BUG_FIX.md
?? docs/HOME_HEADER_ALIGNMENT_FIX.md
?? docs/HOME_HEADER_DISCOVER_GEOMETRY_MATCH.md
?? docs/HOME_PREMIUM_REDESIGN_IMPLEMENTATION.md
?? docs/HOME_PREMIUM_REDESIGN_INVESTIGATION.md
?? docs/HOME_PREMIUM_REDESIGN_V2_VERIFICATION.md
?? docs/HOME_PREMIUM_REDESIGN_VERIFICATION.md
?? docs/HOME_SKIN_SYNC_TEXT_CLIPPING_FIX.md
?? docs/TASK_1_AUTH_SIGNOUT_OWNERSHIP_FIX.md
?? docs/TASK_2_HAIRSTYLE_SERVER_SIDE_FILTERING_FIX.md
?? docs/TASK_3_HAIRSTYLE_LOCAL_SUPABASE_SEPARATION_FIX.md
?? docs/TASK_4_HAIRSTYLE_SINGLE_SOURCE_OF_TRUTH_FIX.md
?? docs/TASK_5_HAIRSTYLE_DOMAIN_MODEL_RESOURCE_DECOUPLING_FIX.md
?? docs/TASK_6_SCAN_HAIRSTYLE_ERROR_HANDLING_STANDARDIZATION.md
?? docs/TASK_7_DEPENDENCY_NATIVE_RESOURCE_LIFECYCLE.md
?? docs/TASK_8_SCAN_FLOW_STATE_MACHINE_EXTRACTION.md
?? docs/phase-10/
?? docs/phase-5/PHASE_5_FINAL_AUDIT.pdf
?? docs/phase-5/PHASE_5_IMPLEMENTATION_REPORT.html
?? docs/phase-5/PHASE_5_IMPLEMENTATION_REPORT.pdf
?? docs/phase-8/
?? docs/phase-9/
?? docs/PROFILE_PHOTO_HOME_LAYOUT_GENDER_HAIRSTYLE_FIX.md
```

Nothing was staged, committed, or pushed — all changes remain in the working
tree for review.
