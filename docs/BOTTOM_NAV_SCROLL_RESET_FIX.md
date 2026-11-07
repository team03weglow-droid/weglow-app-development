# Bottom-Nav Scroll Position Reset: Investigation & Fix

## Summary

The main bottom-navigation tabs (Home, Discover, Routines, Profile) were
preserving their previous scroll position when the user left a tab and later
returned to it via bottom navigation. The desired behavior is for each tab
to open at the top when re-entered through the bottom nav, while leaving all
business/data state (auth, profile, routines, recommendations, Supabase
data) untouched. This has been implemented with a minimal, four-file change
that touches only local scroll-state construction.

## 1. Root Cause of Scroll-Position Preservation

The bottom nav's tab-switching convention is:

```kotlin
navController.navigate(tab.route) {
    popUpTo(Destination.Home.route) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```

This is the standard, intentional Jetpack Navigation pattern for making tab
switches feel seamless: when you leave a tab, `saveState = true` captures
that tab's `NavBackStackEntry` saved-state bundle (everything registered via
`rememberSaveable`); when you return, `restoreState = true` restores that
same bundle into a freshly created entry.

`rememberLazyListState()` and `rememberScrollState()` are themselves built
on `rememberSaveable` (each uses a `Saver` that (de)serializes the scroll
offset). That's why scroll position was surviving tab switches — it was
being swept up in the same save/restore mechanism used for genuinely
important saved UI state, even though the desired behavior treats viewport
position differently from other UI state.

## 2. Scroll-State Mechanism Used by Each Screen

| Screen | Mechanism (before) | Explicit or implicit |
|---|---|---|
| Home | `LazyColumn` + `rememberLazyListState()` | Explicit `listState` variable |
| Discover | `LazyColumn` with no `state=` param | Implicit — Compose defaults to `rememberLazyListState()` |
| Routines | `Column.verticalScroll(rememberScrollState())` | Inline |
| Profile | `Column.verticalScroll(rememberScrollState())` | Inline |

Scan was inspected separately and intentionally left untouched — it owns
its own camera/analyzing navigation flow and has no comparable
bottom-nav-tab scroll state.

Notably, every *other* piece of screen-local UI state in these four files
(Discover's search/filter/view-mode, Routines' `isMorning`/calendar
selection/note draft, Home's `showNotifications`) already used plain
`remember` and was **already** resetting on tab-away-and-back. Scroll
position was the one outlier, purely because it happened to be built on the
`Saver`-backed helper functions.

## 3. Exact Solution Chosen

Swapped each screen's saveable scroll-state constructor for the equivalent
plain, non-saveable one:

- `rememberLazyListState()` → `remember { LazyListState() }` (Home, and a
  newly-added explicit `state=` parameter on Discover's `LazyColumn`, which
  previously relied on the same default implicitly)
- `rememberScrollState()` → `remember { ScrollState(0) }` (Routines, Profile)

A tab's `NavBackStackEntry` is destroyed and a brand-new one is recreated
when the bottom nav leaves and later returns to it (confirmed via
`NavController` source: `popEntryFromBackStack` drives the entry's lifecycle
to `DESTROYED`, and `restoreStateInternal` builds an entirely new entry from
the saved bundle). Any state that isn't specifically registered with
`rememberSaveable` simply starts over at its initial value on that
recreation — so this change gives "start at top" for free, with no extra
reset logic, `LaunchedEffect`, or flags needed anywhere.

This was **not** implemented by removing `saveState`/`restoreState` from
the bottom navigation, since that mechanism also preserves other important
state (e.g. Profile's in-progress camera capture path, which is explicitly
`rememberSaveable` to survive configuration changes) and is unrelated to
scroll position specifically.

## 4. Why This Resets Only Viewport State, Not Business State

- Scroll position lives entirely in the `LazyListState` / `ScrollState`
  object, a pure UI construct scoped to the composable's `remember` slot —
  nothing else was piggybacking on that same instance.
- All business/domain state (`profileState`, `routineState`,
  `discoverState`, journal/completion data, auth state) is hoisted as
  ViewModels in `WeGlowApp()`, entirely outside the NavHost entry lifecycle.
  It is never refetched, cleared, or otherwise touched by this change.
- Other `rememberSaveable` fields in the same screens — `isMorning` in Home,
  `showSourceChooser` / `pendingCapturePath` in Profile (explicitly
  preserved so an in-progress camera capture survives configuration change)
  — were left exactly as they were.

## 5. Files Modified

- `app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/example/weglow/ui/screens/DiscoverScreen.kt`
- `app/src/main/java/com/example/weglow/ui/screens/RoutinesScreen.kt`
- `app/src/main/java/com/example/weglow/ui/screens/ProfileScreen.kt`

No changes to `WeGlowApp.kt`, navigation architecture, repositories,
ViewModels, `AppContainer`, Supabase, authentication, the recommendation
engine, routine business logic, Scan/CameraX/ONNX/ML Kit pipelines, Gradle,
the manifest, or any visual design property (colors, typography, padding,
alignment, icons, section order).

### Diff

```diff
diff --git a/app/src/main/java/com/example/weglow/ui/screens/DiscoverScreen.kt b/app/src/main/java/com/example/weglow/ui/screens/DiscoverScreen.kt
@@ -6,6 +6,7 @@ import androidx.compose.foundation.clickable
 import androidx.compose.foundation.layout.*
 import androidx.compose.foundation.text.KeyboardOptions
 import androidx.compose.foundation.lazy.LazyColumn
+import androidx.compose.foundation.lazy.LazyListState
 import androidx.compose.foundation.lazy.LazyRow
 import androidx.compose.foundation.lazy.items
 import androidx.compose.foundation.shape.CircleShape
@@ -103,6 +104,11 @@ fun DiscoverScreen(
     }

     LazyColumn(
+        // Intentionally not the default rememberSaveable-backed state: this tab's
+        // NavBackStackEntry is destroyed and recreated when the bottom nav leaves
+        // and returns to it, so a plain remember resets the viewport to the top
+        // on return without touching any business/data state.
+        state = remember { LazyListState() },
         modifier = Modifier
             .fillMaxSize()
             .background(PageBackground),

diff --git a/app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt b/app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt
@@ -8,7 +8,7 @@ import androidx.compose.foundation.layout.*
 import androidx.compose.foundation.lazy.LazyColumn
 import androidx.compose.foundation.lazy.LazyRow
 import androidx.compose.foundation.lazy.items
-import androidx.compose.foundation.lazy.rememberLazyListState
+import androidx.compose.foundation.lazy.LazyListState
 import androidx.compose.foundation.shape.RoundedCornerShape
 import androidx.compose.foundation.shape.CircleShape
 import androidx.compose.material.icons.Icons
@@ -73,7 +73,12 @@ fun HomeScreen(
     }
     var isMorning by rememberSaveable { mutableStateOf(true) }
     var showNotifications by remember { mutableStateOf(false) }
-    val listState = rememberLazyListState()
+    // Intentionally not rememberSaveable: this tab's NavBackStackEntry is
+    // destroyed and recreated when the bottom nav leaves and returns to it
+    // (saveState/restoreState still preserves the entry's other saved state),
+    // so a plain remember here resets the viewport to the top on return
+    // without touching any business/data state.
+    val listState = remember { LazyListState() }
     val scope = rememberCoroutineScope()
     val activeSteps = if (isMorning) morningRoutine else eveningRoutine
     val openRoutine = { onRoutinesPeriodClick?.invoke(isMorning) ?: onRoutinesClick() }

diff --git a/app/src/main/java/com/example/weglow/ui/screens/ProfileScreen.kt b/app/src/main/java/com/example/weglow/ui/screens/ProfileScreen.kt
@@ -10,7 +10,7 @@ import androidx.compose.foundation.Image
 import androidx.compose.foundation.background
 import androidx.compose.foundation.clickable
 import androidx.compose.foundation.layout.*
-import androidx.compose.foundation.rememberScrollState
+import androidx.compose.foundation.ScrollState
 import androidx.compose.foundation.shape.CircleShape
 import androidx.compose.foundation.shape.RoundedCornerShape
 import androidx.compose.foundation.verticalScroll
@@ -176,7 +176,11 @@ fun ProfileScreen(
         modifier = Modifier
             .fillMaxSize()
             .background(PageBackground)
-            .verticalScroll(rememberScrollState())
+            // Intentionally not rememberSaveable: this tab's NavBackStackEntry is
+            // destroyed and recreated when the bottom nav leaves and returns to it,
+            // so a plain remember resets the viewport to the top on return without
+            // touching any business/data state (profile, auth, uploads).
+            .verticalScroll(remember { ScrollState(0) })
     ) {
         Row(
             modifier = Modifier

diff --git a/app/src/main/java/com/example/weglow/ui/screens/RoutinesScreen.kt b/app/src/main/java/com/example/weglow/ui/screens/RoutinesScreen.kt
@@ -10,7 +10,7 @@ import androidx.compose.foundation.background
 import androidx.compose.foundation.border
 import androidx.compose.foundation.clickable
 import androidx.compose.foundation.layout.*
-import androidx.compose.foundation.rememberScrollState
+import androidx.compose.foundation.ScrollState
 import androidx.compose.foundation.shape.CircleShape
 import androidx.compose.foundation.shape.RoundedCornerShape
 import androidx.compose.foundation.verticalScroll
@@ -128,7 +128,11 @@ fun RoutinesScreen(
         modifier = Modifier
             .fillMaxSize()
             .background(PageBackground)
-            .verticalScroll(rememberScrollState())
+            // Intentionally not rememberSaveable: this tab's NavBackStackEntry is
+            // destroyed and recreated when the bottom nav leaves and returns to it,
+            // so a plain remember resets the viewport to the top on return without
+            // touching any business/data state (routine plan, journal, calendar).
+            .verticalScroll(remember { ScrollState(0) })
             .padding(
                 horizontal = 24.dp,
                 vertical = 20.dp
```

## 6. Home Avatar Navigation Fix — Confirmed Intact

`WeGlowApp.kt` was not touched by this change. Verified live on the
emulator: **Home → avatar → Profile → bottom-nav Home** still correctly
lands on Home (and, as a side effect of this fix, now also opens at the
top).

## 7. Gradle Validation Results

| Task | Result |
|---|---|
| `architectureCheck` | ✅ BUILD SUCCESSFUL |
| `compileDebugKotlin` | ✅ BUILD SUCCESSFUL |
| `testDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| `lintDebug` | ❌ FAILED — pre-existing, unrelated issue (see below) |
| `assembleDebug` | ✅ BUILD SUCCESSFUL |

**Lint failure detail:** `HomeScreen.kt:126`, `NonObservableLocale` for a
`Locale.getDefault()` call inside a composable. This is the same
known pre-existing issue called out in the task — the line number shifted
from 121 to 126 only because of the comment lines this fix added above it;
the offending code itself was not touched. Confirmed identical to the error
present on unmodified `main`.

## 8. Test Results

All existing unit tests passed (`testDebugUnitTest` — BUILD SUCCESSFUL). No
test files were modified or weakened.

## 9. Manual Emulator Flows Tested

Built, installed, and driven via `adb`/`uiautomator` on an emulator, with
before/after screenshots for each flow:

- ✅ Home scroll down → Discover → Home — **opens at top**
- ✅ Discover scroll down → Profile → Discover — **opens at top**
- ✅ Routines scroll down → Home → Routines — **opens at top**
- ✅ Profile scroll down → Discover → Profile — **opens at top**
- ✅ Home → avatar → Profile → Home — works, **opens at top**
- ✅ Home → bottom-nav Profile → Home — unaffected, still works
- Scan navigation and routine calendar/data: not exercised in this pass,
  but no file on either code path was modified, so there is no mechanism by
  which this change could affect them. Recommended as a quick manual
  spot-check.
- Logout/login: unaffected — no auth-related file was touched.

## 10. `git diff --name-only` Output

```
app/src/main/java/com/example/weglow/ui/screens/DiscoverScreen.kt
app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt
app/src/main/java/com/example/weglow/ui/screens/ProfileScreen.kt
app/src/main/java/com/example/weglow/ui/screens/RoutinesScreen.kt
```

No git operations (`add`, `commit`, `push`, `merge`, `pull`, `checkout`,
`switch`, `reset`, `restore`) were run, per instructions.
