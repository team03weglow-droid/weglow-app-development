# Task 1 — Global Auth/Sign-Out Navigation Ownership Fix

**Scope:** Fix ONLY the global authentication/sign-out navigation ownership violation identified in the architecture review (`docs/ARCHITECTURAL_VIOLATIONS.md`, item 7). No other architectural finding was touched.

---

## 1. Was the original architectural violation confirmed on current main?

Yes. `AuthViewModel.kt`, `AuthRepository`/`SupabaseAuthRepository`, `AuthUiState`/`AuthEvent`, and `WeGlowApp.kt` were re-read fresh from current main (not from the old report) before any change was made. The `LaunchedEffect(authState.event) { if (authState.event is AuthEvent.SignedOut) { ... navController.navigate(Login) ... } }` block was found living inside `composable(Destination.Profile.route) { ... }` in `WeGlowApp.kt` (previously at lines 699–720). `git status`/`git diff` confirmed no prior session had already fixed this.

One correction to the original report's framing, found during verification: `SupabaseAuthRepository.authenticationState` maps `SessionStatus.RefreshFailure` to `NOT_AUTHENTICATED`, but `AuthViewModel.observeAuthenticationState()` does **not** emit `AuthEvent.SignedOut` for that transition today — only the explicit `signOut()` call does. So the concrete bug isn't yet "session expiry silently does nothing on other screens" (that's a separate, larger gap), it's specifically: *the app's reaction to a `SignedOut` event only exists while the Profile screen happens to be composed.* That's the violation that was fixed — nothing broader.

## 2. Exact root cause

The reaction to a cross-cutting application event (`AuthEvent.SignedOut`) was implemented as a side effect scoped to one leaf destination's composable lifecycle, rather than at the composable that owns the `NavController` for the app's entire lifetime (`WeGlowApp()` itself). Because `LaunchedEffect` is torn down when its host composable leaves composition, the event handler only existed while `Destination.Profile.route` was on screen.

## 3. Previous ownership/flow

`ProfileScreen`'s `onLogout` called `authViewModel.signOut()` → `AuthViewModel` set `AuthUiState(event = SignedOut)` → a `LaunchedEffect` **nested inside the Profile composable** observed `authState.event`, cleared `scanViewModel`/`hairstyleViewModel`, navigated to Login with full back-stack pop, and called `consumeEvent()`. If any other code path ever triggered `signOut()`, or if Profile weren't currently composed, this handler simply wouldn't exist to react.

## 4. New ownership/flow

Same trigger, same reaction — different owner. `ProfileScreen`'s `onLogout` still calls `authViewModel.signOut()` (Profile only *initiates*). The `LaunchedEffect(authState.event) { if (... is SignedOut) { ... } }` block now lives at the top level of `WeGlowApp()`, alongside the other `collectAsState()` declarations, so it is mounted for the entire app session regardless of which destination is currently visible.

## 5. Exact files modified

- `app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt` (only file with a functional change)

## 6. Exact code-level change

- Added a new top-level `LaunchedEffect(authState.event)` block in `WeGlowApp()`, right after the last `collectAsState()` declaration (`routineState`) and before `Scaffold(...)`, containing exactly the same logic that was previously inline in Profile: clear `scanViewModel`/`hairstyleViewModel`, `navController.navigate(Destination.Login.route) { popUpTo(navController.graph.id) { inclusive = true }; launchSingleTop = true }`, then `authViewModel.consumeEvent()`.
- Removed that same block from inside `composable(Destination.Profile.route) { ... }`, leaving only `LaunchedEffect(Unit) { profileViewModel.refresh() }` and the `ProfileScreen(...)` call with `onLogout = authViewModel::signOut` unchanged.
- No changes to `AuthViewModel`, `AuthRepository`, `AuthEvent`, `AuthUiState`, `Destination`, or any screen composable.

```kotlin
// New: top-level, in WeGlowApp(), mounted for the app's whole lifetime
val routineState by routineViewModel.uiState.collectAsState()

// Global session-termination reaction, owned by the navigation root rather than by
// whichever screen happens to trigger sign-out. This must stay mounted for the whole
// app lifetime (not scoped to a single destination's composition) so a SignedOut event
// is always handled regardless of which screen is on screen when it fires. Screens that
// can initiate sign-out (currently only Profile) call authViewModel.signOut() and do not
// react to the result themselves.
LaunchedEffect(authState.event) {
    if (authState.event is AuthEvent.SignedOut) {
        scanViewModel.clear()
        hairstyleViewModel.clear()

        navController.navigate(Destination.Login.route) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }

        authViewModel.consumeEvent()
    }
}

Scaffold( ... )
```

```kotlin
// Profile destination: now only initiates logout
composable(Destination.Profile.route) {
    LaunchedEffect(Unit) { profileViewModel.refresh() }

    ProfileScreen(
        // ...
        onLogout = authViewModel::signOut
    )
}
```

## 7. Why this is architecturally better

`WeGlowApp()` is the sole owner of the `NavController` and is composed for the entire app session — it's the correct place to own an application-wide reaction to session termination. Profile is now purely a trigger (`onLogout = authViewModel::signOut`) with no awareness of what happens afterward, matching the goal exactly: Profile initiates logout, it doesn't own the global reaction. This also removes the latent fragility where the reaction's existence depended on incidental UI state (which screen the user happened to be on).

## 8. How existing behavior was preserved

- Email login, signup, Google OAuth: untouched — their `LaunchedEffect(authState.event)` handlers for `SignedIn`/`SignedUp` in the `Login`/`Signup` composables are unchanged.
- Startup session restoration, onboarding routing, onboarding-completed checks: untouched — `AuthViewModel.determineDestination()`, `resolveStartupDestination()`, and the `Loading` destination's `LaunchedEffect` are unchanged.
- Profile logout button: unchanged wiring (`onLogout = authViewModel::signOut`).
- Logout → Login navigation and back-stack clearing: identical code (`popUpTo(navController.graph.id) { inclusive = true }`), just relocated, so pressing Back after logout still cannot return to authenticated screens — the entire graph is popped inclusively as before.
- `scanViewModel.clear()` / `hairstyleViewModel.clear()` on sign-out: preserved verbatim.

## 9. Tests added or changed

None. `AuthViewModel`'s public behavior (emitting `SignedOut`) is unchanged and already covered by `AuthViewModelTest.kt`, which still passes. The change itself is a relocation of a `LaunchedEffect` inside the Compose navigation graph; the project has no existing Compose UI test harness that instantiates `WeGlowApp()` with a real `NavController` to assert on navigation destinations, and building one from scratch would be new test infrastructure, not a "focused" test protecting this change — out of scope per the task's own constraints (no unrelated cleanup/refactoring). This is flagged as a gap worth a future, separately-scoped task rather than silently adding scaffolding here.

## 10. Gradle results

Had to point `JAVA_HOME` at Android Studio's bundled JBR — no system Java was on `PATH`; that's an environment note, not a code change.

| Command | Result |
|---|---|
| `./gradlew architectureCheck` | **BUILD SUCCESSFUL** |
| `./gradlew compileDebugKotlin` | **BUILD SUCCESSFUL** |
| `./gradlew testDebugUnitTest` | **BUILD SUCCESSFUL** (all unit tests pass, including `AuthViewModelTest`) |
| `./gradlew assembleDebug` | **BUILD SUCCESSFUL** |

## 11. `git diff --name-only`

```
app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt
docs/ARCHITECTURE_REVIEW.md
```

Note: `docs/ARCHITECTURE_REVIEW.md` shows as modified because it was already a tracked file in this repo and its content was overwritten earlier in this session (before Task 1 began) when the architecture review was requested as a markdown file. It is **not** part of this task's change — only `WeGlowApp.kt` is. `docs/ARCHITECTURAL_VIOLATIONS.md` is untracked (new) and likewise unrelated to Task 1. Nothing was staged or committed — no `git add`/`commit`/etc. was run.

## 12. Manual tests to perform on emulator/device

1. Fresh login (email) → confirm still lands on Home (or onboarding if incomplete) as before.
2. Fresh signup (email) → confirm still routes into the onboarding wizard.
3. Google OAuth sign-in (existing user and new user) → confirm same destinations as before.
4. Kill and relaunch the app with an existing session → confirm startup session restoration still lands correctly (Home vs onboarding).
5. **Primary regression target:** Go to Profile → tap Logout → confirm you land on Login, and press system Back afterward — confirm you cannot return to Home/Profile/any authenticated screen (back stack fully cleared).
6. Log out while mid-scan or with a hairstyle result loaded → confirm scan/hairstyle state is cleared (no stale result shown after logging back in as a different or same user).
7. Log back in immediately after logout → confirm no double-navigation, no flicker, no stuck loading state (this exercises the effect being re-armed correctly with the new `authState.event` key at the top level).

---

**Status:** Task 1 complete. No other architecture-review item was addressed.
