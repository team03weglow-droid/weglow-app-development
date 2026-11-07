# Minimum Age 14 Fix

WeGlow now requires a minimum age of 14 for account creation. This report
covers a single, narrowly scoped fix: "Under 14" was removed as a
selectable onboarding option, and — more importantly — it can no longer be
submitted through the application flow at all, since the enforcement lives
at the one point every age value passes through on its way into a saved
profile. No Privacy Policy, Terms & Conditions, or account-deletion work
was done as part of this change. Nothing was staged, committed, or pushed.

## 1. Files changed

- `app/src/main/java/com/example/weglow/domain/model/AgeRange.kt` (new)
- `app/src/main/java/com/example/weglow/ui/screens/AgeSelectionScreen.kt`
- `app/src/main/java/com/example/weglow/feature/onboarding/OnboardingViewModel.kt`
- `app/src/test/java/com/example/weglow/feature/onboarding/OnboardingViewModelTest.kt`

## 2. Previous age behavior

`AgeSelectionScreen` offered 6 selectable options, including `"Under 14"`,
in a private local list. `OnboardingViewModel.setAge(value: String)`
accepted any string unconditionally and stored it directly in onboarding
state, later persisted verbatim to `profiles.age_range`. Nothing in the
flow validated the value.

## 3. New age behavior

`"Under 14"` is no longer offered as a selectable option — it was removed
at the source. The screen now renders `AgeRange.OPTIONS` (`14–24`,
`25–35`, `36–45`, `46–59`, `60+`), and a new line of wording appears under
the existing subtitle:

> "You must be at least 14 years old to use WeGlow."

## 4. Where the 14+ rule is enforced

At two layers, per the "smallest architecture-safe change" requirement:

- **UI**: `AgeSelectionScreen` can no longer render or select `"Under 14"`
  — it's not in the list it iterates.
- **ViewModel (the real enforcement point)**:
  `OnboardingViewModel.setAge(value)` now checks
  `value !in AgeRange.OPTIONS` and silently no-ops otherwise. This is the
  single point every age value passes through on its way into the profile
  that gets saved, so no caller — UI or otherwise — can get an under-14
  (or any unrecognized) value into a saved profile through the normal
  application flow. `AgeRange.OPTIONS` is the one shared source of truth
  both layers read from (mirroring the existing `Gender.OPTIONS` pattern
  already used elsewhere in this codebase).

## 5. How existing "Under 14" profiles are handled

Not touched. No migration, no read-path rewrite, no backfill logic was
added or changed. `SupabaseProfileRepository`/`ProfileRow` mapping is
untouched, so an existing profile row with `age_range = 'Under 14'`
continues to read back exactly as stored; only *new* selections of that
value are now impossible.

## 6. Tests changed/added

`OnboardingViewModelTest.kt`:

- Updated 7 existing test call sites that used placeholder age strings
  (`"25-34"`, `"45 - 50"`) not present in the real option list — replaced
  with real `AgeRange.OPTIONS` values (`"25–35"`, `"36–45"`) so they
  continue to exercise valid input under the new guard.
- Added 5 new tests:
  - `setAge_underFourteen_isRejected`
  - `setAge_unrecognizedValue_isRejected`
  - `setAge_everySupportedAgeRange_isAccepted`
  - `finalAnswer_afterRejectedAge_neverPersistsIt` (end-to-end: even after
    `setAge("Under 14")`, the completed/saved profile's `ageRange` is
    `null`, never `"Under 14"`)
  - `setAge_rejectedValueAfterValidOne_leavesValidAnswerInPlace`

## 7–10. Gradle verification results

```
./gradlew architectureCheck testDebugUnitTest lintDebug assembleDebug
```

| Task | Result |
|---|---|
| `architectureCheck` | ✅ BUILD SUCCESSFUL |
| `testDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| `lintDebug` | ✅ BUILD SUCCESSFUL |
| `assembleDebug` | ✅ BUILD SUCCESSFUL |

`OnboardingViewModelTest`: 12/12 passed, 0 skipped, 0 failed.

## 11. `git diff --stat`

```
 .../feature/onboarding/OnboardingViewModel.kt      | 13 +++-
 .../weglow/ui/screens/AgeSelectionScreen.kt        | 13 +++-
 .../feature/onboarding/OnboardingViewModelTest.kt  | 83 +++++++++++++++++++---
 3 files changed, 97 insertions(+), 12 deletions(-)
```

## 12. `git status --short`

```
 M app/src/main/java/com/example/weglow/feature/onboarding/OnboardingViewModel.kt
 M app/src/main/java/com/example/weglow/ui/screens/AgeSelectionScreen.kt
 M app/src/test/java/com/example/weglow/feature/onboarding/OnboardingViewModelTest.kt
?? app/src/main/java/com/example/weglow/domain/model/AgeRange.kt
```

Nothing staged, committed, or pushed.
