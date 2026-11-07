# Unit Test Compilation Fix (Post-Merge)

Report for fixing unit-test compilation failures left over after the
`origin/main` merge (the merge conflict in `WeGlowApp.kt` had already been
manually resolved and staged before this task started). Scope was strictly
limited to unit-test compilation problems — `WeGlowApp.kt` was not touched,
the merge was neither aborted nor completed, and nothing was staged,
committed, or pushed.

## Root Cause(s)

The merge from `origin/main` brought in a teammate's chatbot feature, which
extended `ProfileRepository` with `updateScanSummary`/`updateEnvironment`,
and this repo's `AuthRepository` already has `currentUserEmail()` from
earlier work. Five test files went out of sync with these interfaces
during the merge, via **two distinct mechanisms**:

1. **Simple staleness** (`AuthViewModelTest.kt`): `FakeAuthRepository` was
   never updated to add `currentUserEmail()`.
2. **Misplaced merge hunks** (`RecommendationViewModelTest.kt`,
   `RoutineViewModelTest.kt`): the merge tool spliced an `updateGender`
   override onto the *wrong* class — appended after
   `FakeCatalogRepository.products()` (a class that doesn't even implement
   `ProfileRepository`) instead of into `FakeProfileRepository`, with a
   stray glued-together closing brace (`Result.success(Unit)}`). Both
   files were *also* missing `currentUserEmail()` and the
   correctly-placed `updateGender`/`updateScanSummary`/`updateEnvironment`
   overrides.
3. **Duplicated line** (`OnboardingViewModelTest.kt`): a leftover
   duplicate `Result.success(Unit)` statement was left dangling after
   `updateGender`'s single-expression body, which is not a valid
   class-member declaration — the literal cause of the reported syntax
   error.
4. **Truncated file** (`ProfileViewModelTest.kt`) — the most serious: the
   merge cut the file off mid-body of test #15
   (`onGenderSelected_sameValueAsCurrent_isNoOp`). Everything after that
   point was lost: the rest of that test, the
   `consumeGenderError_clearsError` test, the test class's closing brace,
   and the **entire Fakes section** (`FakeAuthRepository`,
   `FakeProfileRepository`, `FakeProfileImageRepository` — none of them
   existed anywhere in the file), which is why every reference to them
   earlier in the file was "unresolved."

## Exact Files Modified

- `app/src/test/java/com/example/weglow/feature/auth/AuthViewModelTest.kt`
- `app/src/test/java/com/example/weglow/feature/recommendation/RecommendationViewModelTest.kt`
- `app/src/test/java/com/example/weglow/feature/routine/RoutineViewModelTest.kt`
- `app/src/test/java/com/example/weglow/feature/onboarding/OnboardingViewModelTest.kt`
- `app/src/test/java/com/example/weglow/feature/profile/ProfileViewModelTest.kt`

`WeGlowApp.kt` was **not** touched. No production/domain files were
modified.

## Exact Changes Made

- **`AuthViewModelTest.kt`**: added
  `override fun currentUserEmail(): String? = null` to
  `FakeAuthRepository`.
- **`RecommendationViewModelTest.kt`** / **`RoutineViewModelTest.kt`**
  (same fix in both): added `currentUserEmail()` to `FakeAuthRepository`;
  moved the misplaced `updateGender` override out of
  `FakeCatalogRepository` and into `FakeProfileRepository` (where
  `updateScanSummary`/`updateEnvironment` already correctly lived), fixing
  the glued-brace formatting.
- **`OnboardingViewModelTest.kt`**: removed the duplicate dangling
  `Result.success(Unit)` line after `updateGender`'s body.
- **`ProfileViewModelTest.kt`**: completed test #15's body
  (`vm.onGenderSelected("Male")`, `advanceUntilIdle()`,
  `assertEquals(0, profiles.updateGenderCallCount)`), restored the
  `consumeGenderError_clearsError` test, closed the test class, and
  reconstructed the entire Fakes section — `FakeAuthRepository` (now
  including `currentUserEmail()`), `FakeProfileRepository` (now including
  `updateScanSummary`/`updateEnvironment` alongside the existing
  `updateGender`/`updateProfileImagePath` tracking), and
  `FakeProfileImageRepository` — matching exactly what the 21 test bodies
  already in the file required, and matching the current
  `ProfileRepository`/`AuthRepository`/`ProfileImageRepository`
  interfaces verbatim (no signature guessed).

No test was removed, disabled, or weakened — every fix either restored
what a test already required or added the one missing interface member
with an inert (`Result.success(Unit)`/`null`) default, consistent with
every sibling fake in the codebase.

## Results

1. `./gradlew compileDebugUnitTestKotlin --console=plain` → **BUILD SUCCESSFUL**
2. `./gradlew testDebugUnitTest` → **BUILD SUCCESSFUL** — all 55 tests
   across the 5 affected classes pass (`ProfileViewModelTest` 21/21,
   `AuthViewModelTest` 9/9, `RecommendationViewModelTest` 8/8,
   `RoutineViewModelTest` 5/5, `OnboardingViewModelTest` 12/12), 0
   skipped, 0 failed
3. `./gradlew lintDebug` → **BUILD SUCCESSFUL**
4. `./gradlew assembleDebug` → **BUILD SUCCESSFUL**

## `git status --short`

```
 M .gitignore
 M app/build.gradle.kts
 M app/src/main/java/com/example/weglow/app/AppContainer.kt
A  app/src/main/java/com/example/weglow/chatbot/VoiceAgentManager.kt
 M app/src/main/java/com/example/weglow/core/config/AppConfig.kt
 M app/src/main/java/com/example/weglow/data/model/ProfileRow.kt
A  app/src/main/java/com/example/weglow/data/remote/chat/ChatApi.kt
A  app/src/main/java/com/example/weglow/data/remote/chat/ChatApiProvider.kt
A  app/src/main/java/com/example/weglow/data/repository/RetrofitChatRepository.kt
A  app/src/main/java/com/example/weglow/data/repository/SupabaseAcneScanRepository.kt
 M app/src/main/java/com/example/weglow/data/repository/SupabaseProfileImageRepository.kt
 M app/src/main/java/com/example/weglow/data/repository/SupabaseProfileRepository.kt
 M app/src/main/java/com/example/weglow/data/repository/WeatherApiEnvironmentRepository.kt
A  app/src/main/java/com/example/weglow/domain/model/ChatTurn.kt
A  app/src/main/java/com/example/weglow/domain/repository/ChatRepository.kt
 M app/src/main/java/com/example/weglow/domain/repository/EnvironmentRepository.kt
 M app/src/main/java/com/example/weglow/domain/repository/ProfileRepository.kt
A  app/src/main/java/com/example/weglow/feature/chat/ChatViewModel.kt
 M app/src/main/java/com/example/weglow/feature/environment/EnvironmentViewModel.kt
 M app/src/main/java/com/example/weglow/navigation/Destination.kt
 M app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt
A  app/src/main/java/com/example/weglow/ui/screens/WeGlowChatScreen.kt
A  app/src/main/res/xml/network_security_config.xml
 M app/src/test/java/com/example/weglow/data/repository/SupabaseProfileRepositoryTest.kt
MM app/src/test/java/com/example/weglow/feature/auth/AuthViewModelTest.kt
MM app/src/test/java/com/example/weglow/feature/onboarding/OnboardingViewModelTest.kt
MM app/src/test/java/com/example/weglow/feature/profile/ProfileViewModelTest.kt
MM app/src/test/java/com/example/weglow/feature/recommendation/RecommendationViewModelTest.kt
MM app/src/test/java/com/example/weglow/feature/routine/RoutineViewModelTest.kt
A  backend/chat.py
 M docs/ARCHITECTURE_REVIEW.md
 M gradle/libs.versions.toml
A  supabase/CONSOLIDATED_SETUP.sql
A  supabase/config.toml
A  supabase/functions/_shared/cors.ts
A  supabase/functions/chat/index.ts
A  supabase/migrations/20260911000100_profile_images_storage.sql
A  supabase/migrations/20260914000000_add_scan_summary_to_profiles.sql
A  supabase/migrations/20260915000000_add_env_to_profiles.sql
?? (untracked docs/*.md files, all pre-existing/from earlier sessions)
```

The `MM` status on the five test files reflects that they were already
staged from the merge and were then modified further, unstaged, on top —
exactly as expected. `WeGlowApp.kt` is `M ` only (staged, untouched by
this task). Nothing was staged, committed, pushed, and the merge was
neither aborted nor completed.
