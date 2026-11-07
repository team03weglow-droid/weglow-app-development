# WeGlow Mobile Technical Lead Review

Reviewed application commit: `919b2a0`.

Scope: the current Android application and its supporting Supabase integration. This is a source-level review, not a device penetration test or a verification of live Dashboard settings. It identifies material issues found during inspection; it does not guarantee that every defect has been discovered.

**Assessment:** Authentication, lifecycle, and data-consistency issues should be addressed before treating the application as production-ready. This includes gaps in the password-recovery implementation added earlier.

No application code was changed during the review. Paths below are relative to the project root; line numbers refer to the reviewed version.

## High-Priority Findings — P1

### 1. Recovery credentials travel through an interceptable custom-scheme link

Another Android app can register `weglow://password-reset`. If that app receives the link, its token hash is sufficient to request a recovery session. The Google flow’s PKCE configuration does not protect this direct token-hash exchange.

**Recommended correction:** Use verified HTTPS App Links or a recovery flow bound to the requesting application. Adding `autoVerify` to the existing custom scheme alone would not establish domain ownership.

**Evidence:**
- `app/src/main/AndroidManifest.xml:70`
- `app/src/main/java/com/example/weglow/data/repository/SupabaseAccountSecurityRepository.kt:38`
- [Android deep-link interception guidance](https://developer.android.com/privacy-and-security/risks/unsafe-use-of-deeplinks)

### 2. Recovery sessions can become ordinary login sessions after process death

The installed Supabase SDK persists the session created by `verifyEmailOtp()`. However, `recoveryActive`, `currentOperation`, and recovery authorization exist only in memory. If the process dies after verification but before saving a password, startup can restore that session and navigate to Home or onboarding instead of continuing recovery.

**Recommended correction:** Give recovery explicit session-lifecycle ownership and a fail-closed restart policy. Do not persist recovery credentials in ordinary saved UI state as a workaround.

**Evidence:**
- `app/src/main/java/com/example/weglow/data/repository/SupabaseAccountSecurityRepository.kt:34`
- `app/src/main/java/com/example/weglow/feature/auth/AuthViewModel.kt:123`
- Installed Supabase Kotlin 3.0.0 source: `verify()` imports a session; automatic session saving/loading is enabled by default.

### 3. An authentication-state race can lose successful-login navigation

The observer sets `hadAuthenticatedSession = true` before awaiting the profile lookup. Because it uses `collectLatest`, another authenticated emission can cancel that lookup. The replacement execution sees an already-authenticated session and may emit no `SignedIn` event, leaving the login screen loading.

**Recommended correction:** Commit navigation state only after resolution succeeds, and distinguish session refreshes from new sign-ins.

**Evidence:** `app/src/main/java/com/example/weglow/feature/auth/AuthViewModel.kt:128`.

### 4. Signup advances to onboarding without confirming that a session exists

When Supabase requires email confirmation, successful signup can return without authenticating the user. The ViewModel nevertheless emits `SignedUp`, and navigation opens onboarding. Saving then fails because there is no authenticated identity.

**Recommended correction:** Introduce an explicit “check your email” outcome and advance only when an authenticated session is available.

**Configuration dependency:** The failure path depends on the project’s email-confirmation setting, which was not verified live.

**Evidence:**
- `app/src/main/java/com/example/weglow/feature/auth/AuthViewModel.kt:305`
- `app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt:584`

### 5. A failed profile request is interpreted as incomplete onboarding

`hasCompletedOnboarding(...).getOrDefault(false)` treats network failures, authorization failures, and missing profiles identically. Returning users can be sent through onboarding during an outage, potentially replacing existing profile answers if they continue.

**Recommended correction:** Preserve an explicit lookup-error state with retry; distinguish an absent/incomplete profile from a failed request.

**Evidence:** `app/src/main/java/com/example/weglow/feature/auth/AuthViewModel.kt:487`.

### 6. Profile writes report success without confirming that a row changed

Profile updates return `Unit` without inspecting affected rows. A missing row or an RLS policy filtering out the update can therefore look successful. The photo workflow may subsequently delete the previous image even though its replacement path was never saved.

**Recommended correction:** Require confirmation of the updated row before destructive cleanup.

**Evidence:**
- `app/src/main/java/com/example/weglow/data/repository/SupabaseProfileRepository.kt:40`
- `app/src/main/java/com/example/weglow/feature/profile/ProfileViewModel.kt:220`

## Other Correctness and Reliability Findings — P2

### 7. Temporary session-refresh failures are treated as logout

Every SDK `RefreshFailure` maps to `NOT_AUTHENTICATED`, including recoverable network/server failures. This clears account state and redirects to login even when the SDK may later recover the session.

**Recommended correction:** Introduce a separate reconnecting/error state.

**Evidence:** `app/src/main/java/com/example/weglow/data/repository/SupabaseAuthRepository.kt:32`.

### 8. Coroutine cancellation is swallowed by repository contracts

Numerous suspending methods use `runCatching`, which also catches `CancellationException`. Authentication and recovery callers can consequently publish error states after cancellation; recovery cleanup can also be interrupted. `AccountWork.await()` already compensates for swallowed cancellation elsewhere, demonstrating the inconsistency.

**Recommended correction:** Rethrow cancellation consistently and give security-sensitive cleanup an appropriate cancellation policy.

**Evidence:**
- `app/src/main/java/com/example/weglow/data/repository/SupabaseAccountSecurityRepository.kt:34`
- `app/src/main/java/com/example/weglow/core/auth/AccountWork.kt:60`

### 9. Sign-in handling depends on which screen is composed

Logout handling is global, but `SignedIn` is consumed inside Login and `SignedUp` inside Signup. An OAuth callback arriving while a legal or recovery screen is open can authenticate the account without completing navigation immediately.

**Recommended correction:** Handle authentication transitions centrally.

**Evidence:** `app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt:513`.

### 10. Dismissing an invalid recovery link logs out an existing user

An unverified link can activate the recovery screen. Its Back action unconditionally invokes normal sign-out, even if recovery verification never succeeded.

**Recommended correction:** Dismissing an invalid link should not terminate an unrelated valid session.

**Evidence:**
- `app/src/main/java/com/example/weglow/feature/auth/AuthViewModel.kt:391`
- `app/src/main/java/com/example/weglow/feature/auth/AuthViewModel.kt:437`

### 11. Normal token refresh invalidates recovery authorization

Recovery stores the current refresh token and later requires exact equality. The SDK automatically refreshes sessions, so leaving the reset screen open until refresh can make a legitimate reset fail.

**Recommended correction:** Follow the verified recovery session lifecycle rather than treating a rotating credential as a stable identifier.

**Evidence:** `app/src/main/java/com/example/weglow/data/repository/SupabaseAccountSecurityRepository.kt:40`.

### 12. Profile refresh can overwrite a newer successful edit

`refresh()` can run concurrently with photo uploads and gender changes. An older response can arrive last and replace the newly saved image path, bytes, or gender in UI state.

**Recommended correction:** Serialize these operations or reject outdated refresh results.

**Evidence:** `app/src/main/java/com/example/weglow/feature/profile/ProfileViewModel.kt:97`.

### 13. Cart and saved-product reads can overwrite newer mutations

Their `load()` operations have no latest-request protection. A read started before an add/remove can complete afterward and restore stale items. Account isolation does not prevent races within the same account.

**Recommended correction:** Coordinate reads and mutations using serialization or request/version checks.

**Evidence:**
- `app/src/main/java/com/example/weglow/feature/cart/CartViewModel.kt:41`
- `app/src/main/java/com/example/weglow/feature/savedproducts/SavedProductsViewModel.kt:38`

### 14. Retrying scan persistence is not idempotent

If an insert commits but its response is lost, Retry performs another insert. The payload has no stable scan identifier, and the checked-in schema does not enforce uniqueness for that logical scan. Duplicate history entries are possible.

**Recommended correction:** Assign a stable identity to each scan and make persistence retries idempotent.

**Evidence:**
- `app/src/main/java/com/example/weglow/data/repository/SupabaseScanProfileRepository.kt:25`
- `app/src/main/java/com/example/weglow/feature/scan/ScanViewModel.kt:140`
- `supabase/migrations/20261031000000_user_data_security.sql`

### 15. Recreating the scan screen resets an ongoing workflow

Scan entry always calls `resetFlow()`, including after Activity recreation, while its retained ViewModels may still be analyzing. The stage returns to mode selection, and completion handling may no longer recognize the result.

**Recommended correction:** Reset only on an intentional new scan.

**Evidence:** `app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt:949`.

### 16. Scan navigation events can be lost during recreation

The completion `SharedFlow` has no replay. Its extra buffer does not retain events when there are no subscribers. A completion emitted while the root collector is absent can disappear.

**Recommended correction:** Represent pending completion as acknowledged state, or otherwise provide reliable delivery.

**Evidence:** `app/src/main/java/com/example/weglow/feature/scan/ScanFlowViewModel.kt:58`.

### 17. Completed local scans wait for backend persistence before appearing

Acne analysis awaits the profile-summary write, then the ViewModel awaits history persistence before publishing `result`. Slow backend requests delay access to an already-computed local result.

**Recommended correction:** Expose the result independently and track saving separately.

**Evidence:**
- `app/src/main/java/com/example/weglow/data/repository/SupabaseAcneScanRepository.kt:37`
- `app/src/main/java/com/example/weglow/feature/scan/ScanViewModel.kt:89`

### 18. Catalog loading has no pagination or freshness contract

A single unrestricted select can produce incomplete results once the server’s response limit is reached. Discover then retains a successful list indefinitely, while other features fetch separate copies.

**Recommended correction:** Define pagination, refresh, and shared cache ownership.

**Evidence:**
- `app/src/main/java/com/example/weglow/data/repository/SupabaseCatalogRepository.kt:16`
- `app/src/main/java/com/example/weglow/feature/discover/DiscoverViewModel.kt:34`

### 19. Avatar decoding runs synchronously during composition

`rememberDecodedBitmap()` calls `BitmapFactory.decodeByteArray()` on the composition thread. `remember` avoids repeated decoding for the same bytes but does not move the initial work off the main thread.

**Recommended correction:** Use background decoding or the existing image-loading infrastructure.

**Evidence:** `app/src/main/java/com/example/weglow/ui/components/ProfileAvatar.kt:26`.

### 20. Camera failure and disposal handling is incomplete

Binding and capture exceptions are silently ignored. The asynchronous camera-provider listener can also finish after disposal and bind the camera after cleanup ran.

**Recommended correction:** Add visible failure states, capture-in-flight protection, and a disposed-state check.

**Evidence:**
- `app/src/main/java/com/example/weglow/ui/screens/ScanScreen.kt:636`
- `app/src/main/java/com/example/weglow/ui/screens/ScanScreen.kt:775`

## Architectural Weaknesses

### 21. The journal ViewModel also acts as its persistence layer

It directly owns `SharedPreferences`, JSON serialization, storage keys, and disk writes. This combines presentation and storage responsibilities and makes storage migration and JVM testing unnecessarily difficult.

**Recommended correction:** Move persistence behind a repository while preserving the current device-local behavior.

**Evidence:** `app/src/main/java/com/example/weglow/feature/routine/RoutineJournalViewModel.kt:26`.

### 22. Dependency lifetimes do not match their documented ownership

`AppContainer` is created with composition-scoped `remember`, but native models are described as process-scoped and intentionally never closed. Repository-owned HTTP clients also have no explicit shutdown owner.

**Recommended correction:** Choose actual application ownership or a clear retained owner with cleanup. This does not require adopting a new DI framework.

**Evidence:**
- `app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt:73`
- `app/src/main/java/com/example/weglow/app/AppContainer.kt:113`

### 23. Authentication and platform boundaries are inconsistent

OAuth session exchange happens directly in `MainActivity` through the Supabase singleton, while recovery goes through a repository and ViewModel. Separately, `EnvironmentViewModel` imports its location contract from the data package. These split ownership and reverse the intended presentation-to-domain dependency direction.

**Recommended correction:** Route authentication operations through the existing authentication boundary and place the location contract in an appropriate shared/domain boundary.

**Evidence:**
- `app/src/main/java/com/example/weglow/MainActivity.kt:57`
- `app/src/main/java/com/example/weglow/feature/environment/EnvironmentViewModel.kt:6`

### 24. Error handling has no consistent application contract

Scanning uses typed failures, while authentication, profiles, cart, and environment frequently display `Throwable.message` directly. Backend wording can leak implementation details, vary unpredictably, and resist localization.

**Recommended correction:** Extend the existing typed-failure approach rather than displaying arbitrary exceptions.

**Evidence:**
- `app/src/main/java/com/example/weglow/feature/auth/AuthViewModel.kt:224`
- `app/src/main/java/com/example/weglow/feature/environment/EnvironmentViewModel.kt:78`

### 25. Architecture enforcement does not test the intended boundaries

`checkArchitecture` only searches for two forbidden strings. It cannot detect presentation importing data implementations or other dependency-direction violations. It also rejects `ContentResolver` inside explicitly Android-facing image adapters; those imports alone do not prove a bad architectural boundary.

**Recommended correction:** Define allowed dependencies per package and test those rules.

**Evidence:** `app/build.gradle.kts:231`.

### 26. Tests do not exercise the highest-risk authentication paths

Recovery tests use fake repositories rather than the installed SDK’s persistence and refresh behavior. Coverage is missing for recovery process death, signup without a session, overlapping auth emissions, callback navigation, and zero-row writes. No repository-defined GitHub Actions workflow was found enforcing these checks; external CI configuration was not inspected.

**Recommended correction:** Add focused SDK/repository integration tests and lifecycle/navigation regression tests, then enforce the agreed checks in CI.

**Evidence:** `app/src/test/java/com/example/weglow/feature/auth/AuthViewModelTest.kt:570`.

## Verification Results

| Check | Result |
| --- | --- |
| `:app:assembleDebug` | Passed; up-to-date outputs reused |
| `:app:testDebugUnitTest` | Passed; reports contain 260 passing tests, zero failures/errors/skipped |
| `:app:lintDebug` | Passed; zero errors, 115 warnings |
| `:app:checkArchitecture` | Failed on the two existing `ContentResolver` imports |
| `git diff --check` | Passed |
| Device/live Supabase testing | Not performed |

The combined Gradle invocation used `--continue` and returned failure solely because `checkArchitecture` failed. The flagged files were:

- `app/src/main/java/com/example/weglow/core/image/ProfileImageReader.kt`
- `app/src/main/java/com/example/weglow/core/image/ScanPhotoDecoder.kt`

Passing unit tests does not establish that the untested lifecycle and SDK integration paths above are correct.

## Older Findings That Are Already Fixed

The current code already contains:

- Server-side hairstyle filtering.
- Injected hairstyle classification.
- A ViewModel owning the scan workflow.
- Global logout handling.

These should not be reported as current violations. A single Gradle module or the absence of Hilt is not inherently an architectural violation.

## Recommended Order of Work

1. Address findings **1–6**: recovery security/session ownership, authentication transitions, signup outcomes, profile lookup failures, and write confirmation.
2. Address cancellation, request ordering, and lifecycle restoration.
3. Improve persistence boundaries, resource ownership, error contracts, and architecture enforcement.
4. Add focused regression coverage for the corrected failure paths.

These changes can be made incrementally without rewriting or restructuring the application.
