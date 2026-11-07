# Home Avatar → Profile → Home Navigation Bug: Investigation & Fix

## Summary

Tapping the profile avatar in the Home header, then tapping **Home** in the
bottom navigation, left the app stuck on the Profile screen instead of
returning to Home. Normal bottom-navigation transitions (including
bottom-nav Profile → Home) were unaffected. The bug has been fixed with a
minimal, one-file change.

## 1. Exact Root Cause

Jetpack Navigation's

```kotlin
navigate(route) {
    popUpTo(anchorRoute) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```

pattern (used by `WeGlowBottomNavigation`'s `onSelect` for every tab) has a
side effect beyond popping the stack: when the `popUpTo` is **non-inclusive**,
`NavController.executePopOperations` also writes

```
backStackMap[anchorDestination.id] = <id of the first entry it just saved>
```

(see `androidx.navigation:navigation-runtime:2.8.0`,
`NavController.kt`, `executePopOperations`, lines ~705–724).

This aliases the **anchor destination's own ID** (`Destination.Home`, since
`popUpTo(Destination.Home.route)` is used for every tab, including Home
itself) to whatever was just popped off above it.

- If a screen (e.g. Profile) was entered **through the tab system** — i.e.
  its own navigation also carried `popUpTo(Home){ saveState = true }` — then
  at entry time there was nothing above Home to pop, so
  `backStackMap[Home.id]` got pre-set to `null` (a harmless placeholder).
- If Profile was entered via the Home avatar's **plain `navigate()`** call
  (no `popUpTo`/`saveState`/`restoreState`), `backStackMap[Home.id]` was
  never touched on the way in. Later, tapping the Home tab triggers
  `popUpTo(Home){ saveState = true }`, which pops Profile off — and this is
  the *first* thing to populate `backStackMap[Home.id]`, with **Profile's
  real saved-state ID**. Immediately afterward, `restoreStateInternal`
  consults that very same map entry and restores **Profile right back onto
  the stack** instead of leaving Home visible.

## 2. Why bottom-nav Profile → Home Worked

Entering Profile via the bottom-nav tab pre-poisons `backStackMap[Home.id]`
with `null` (there's nothing above Home to save at that point). When the
user later taps Home, `restoreStateInternal` finds a `null`-backed (empty)
saved state, gracefully returns `false`, and the prior `popUpTo` pop (which
already removed Profile) is left to correctly reveal Home.

## 3. Why Home-avatar → Profile → Home Failed

The avatar's `navigate(Destination.Profile.route) { launchSingleTop = true }`
never sets up the `backStackMap[Home.id]` entry. Tapping the Home tab then
writes a *real* mapping to Profile's saved state and immediately restores
it — so the screen snaps right back to Profile, looking like the Home tap
"didn't respond."

This was confirmed two ways:

- **Source-level trace** through `NavController.kt` (`navigate`,
  `popBackStackInternal`, `executePopOperations`, `restoreStateInternal`) in
  the exact library version this project uses (`navigation-compose 2.8.0`).
- **Empirical reproduction on an emulator**: built and installed the debug
  APK, drove the exact repro via `adb`/`uiautomator`, and captured
  before/after screenshots. The same class of failure was also reproduced
  for other screens reached via a plain `navigate()` call bypassing the tab
  convention (e.g. Profile → Routines via a Profile UI card), confirming
  this is a structural back-stack bookkeeping issue, not something specific
  to the avatar widget itself.

## 4. Exact Code Change

**File:** `app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt`

```diff
-                    onProfileClick = { navController.navigate(Destination.Profile.route) { launchSingleTop = true } },
+                    onProfileClick = {
+                        navController.navigate(Destination.Profile.route) {
+                            // Match the bottom-nav tab convention (see onSelect below) so that
+                            // entering Profile from the Home avatar leaves the same back-stack
+                            // bookkeeping in place as entering it via the tab. Without this,
+                            // returning to Home restores Profile's saved state instead of Home's.
+                            popUpTo(Destination.Home.route) {
+                                saveState = true
+                            }
+                            launchSingleTop = true
+                            restoreState = true
+                        }
+                    },
```

The avatar's navigation to Profile now uses the identical
`popUpTo(Destination.Home.route){ saveState = true }` +
`launchSingleTop = true` + `restoreState = true` options already used by the
bottom nav's `onSelect`, making avatar-entry indistinguishable — from the
back stack's perspective — from tab-entry.

## 5. Files Modified

Only `app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt`
(13 insertions, 1 deletion).

## 6. Navigation Architecture

Preserved. No new `NavController`, no nested navigation graphs, no new
destinations, ViewModels, or repositories, and no route strings outside the
existing `Destination` system. The fix reuses the exact convention already
established for bottom-nav tabs.

## 7. Gradle Validation Results

| Task | Result |
|---|---|
| `architectureCheck` | ✅ BUILD SUCCESSFUL |
| `compileDebugKotlin` | ✅ BUILD SUCCESSFUL |
| `testDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| `lintDebug` | ❌ FAILED — pre-existing, unrelated issue (see below) |
| `assembleDebug` | ✅ BUILD SUCCESSFUL |

**Lint failure detail:** `HomeScreen.kt:121`, `NonObservableLocale` for a
`Locale.getDefault()` call inside a composable. This file was not touched by
this fix. Verified via `git stash` that the identical lint error and failure
occur on unmodified `main`, confirming it predates this change.

## 8. Tests

All existing unit tests passed (`testDebugUnitTest` — BUILD SUCCESSFUL). No
test files were modified.

## 9. Manual Navigation Flows Verified

Verified live on an Android emulator (screenshots captured before and after
the fix):

- ✅ Home → avatar Profile → bottom-nav Home — **broken before, fixed after**
- ✅ Home → bottom-nav Profile → Home — still works (regression check)
- ✅ Home → bottom-nav Discover → Home — still works (regression check)

**Recommended additional manual spot-checks** (not touched by this change,
but share the same `NavHost`):

- Profile ↔ Discover
- Routines → Home
- Scan flow / navigation
- Logout / login
- Onboarding → Home
