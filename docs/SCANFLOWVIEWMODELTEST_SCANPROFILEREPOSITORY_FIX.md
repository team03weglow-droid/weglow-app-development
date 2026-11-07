# ScanFlowViewModelTest — Missing scanProfileRepository Fix

Fixes a post-merge compile failure in `ScanFlowViewModelTest.kt` that appeared
after pulling and auto-merging the latest `origin/main` into the WeGlow
Profile photo/Home/gender/hairstyle changes. Test-only fix — no production
code, architecture, or previously merged teammate changes were touched.

## 1. Root cause

A teammate's merged change ("scan result persistence") added a required
`scanProfileRepository: ScanProfileRepository` parameter to `ScanViewModel`'s
constructor (`feature/scan/ScanViewModel.kt`), used to persist a completed
acne scan via `ScanProfileRepository.saveScanResult(...)`.

`ScanViewModelTest.kt` was already updated for this — it has its own
`scanProfileRepository()` fake helper and passes it everywhere.
`ScanFlowViewModelTest.kt` was **not** updated: its 13
`ScanViewModel(fakeAcneRepository { ... })` call sites still passed only one
argument, so the compiler flagged every one of them with:

```
No value passed for parameter 'scanProfileRepository'
```

at exactly the reported lines (81, 97, 114, 130, 144, 158, 174, 196, 217,
231, 250, 263, 296). This was purely a stale test file left behind by the
merge — unrelated to the earlier Profile photo rotation / Home layout /
gender / hairstyle work.

## 2. Files modified

- `app/src/test/java/com/example/weglow/feature/scan/ScanFlowViewModelTest.kt`
  (test-only)

No other file was touched.

## 3. Was production code changed?

**No.** `ScanFlowViewModel`, `ScanViewModel`, and `ScanProfileRepository`
were left exactly as pulled from `origin/main` — no production constructor,
signature, or behavior was modified to accommodate the test.

## 4. Fix applied

Added a `fakeScanProfileRepository()` helper to `ScanFlowViewModelTest.kt`,
mirroring the exact convention already established in `ScanViewModelTest.kt`:

```kotlin
private fun fakeScanProfileRepository() = object : ScanProfileRepository {
    override suspend fun saveScanResult(result: AcneScanResult, scannedAt: Instant): Result<Unit> =
        Result.success(Unit)
}
```

Passed it as the second argument to all 13 `ScanViewModel(...)` construction
sites in the file (including the one multi-line lambda case), so every
instance now compiles against the current constructor without changing what
any test actually verifies.

## 5. Checks run

```
./gradlew architectureCheck testDebugUnitTest lintDebug assembleDebug
```

No other failure surfaced after this fix — the four tasks were the only ones
run and none needed further investigation.

## 6. Final result

```
BUILD SUCCESSFUL
```

All four tasks (`architectureCheck`, `testDebugUnitTest`, `lintDebug`,
`assembleDebug`) passed.

## 7. `git diff --stat`

```
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
 .../weglow/feature/scan/ScanFlowViewModelTest.kt   |  36 +++--
 docs/ARCHITECTURE_REVIEW.md                        | 170 ++++++++++-----------
 15 files changed, 575 insertions(+), 123 deletions(-)
```

`ScanFlowViewModelTest.kt` is the only file newly modified for this fix;
every other entry predates it (Profile photo/Home/gender/hairstyle work from
the prior session, plus `docs/ARCHITECTURE_REVIEW.md` which predates both
sessions and was never touched by either).

## 8. `git status --short`

```
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
 M app/src/test/java/com/example/weglow/feature/scan/ScanFlowViewModelTest.kt
 M docs/ARCHITECTURE_REVIEW.md
?? app/src/main/java/com/example/weglow/domain/model/Gender.kt
?? docs/PROFILE_PHOTO_HOME_LAYOUT_GENDER_HAIRSTYLE_FIX.md
?? docs/SCANFLOWVIEWMODELTEST_SCANPROFILEREPOSITORY_FIX.md
?? (other pre-existing untracked docs/* files, unrelated to this session)
```

Nothing was staged, committed, or pushed — all changes remain in the working
tree for review.
