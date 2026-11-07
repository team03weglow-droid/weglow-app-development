# Profile Bottom-Nav Bug & Discover Add-to-Bag Fix

Two narrowly scoped fixes to the existing WeGlow Android app:

1. Profile bottom-navigation tab silently failing to reopen after
   Profile → Recommendations → Discover.
2. "Add to Bag" missing from every regular Discover product card (only the
   featured card and, separately, "Save" on grid cards existed before).

No architecture redesign, no second `NavHost`, no duplicate
repositories/ViewModels/Product model, no Supabase schema changes. Nothing
staged, committed, or pushed — all changes remain in the working tree for
review.

## Fix 1 — Profile bottom-navigation bug

### Root cause

A confirmed, previously-documented `androidx.navigation` behavior in
`NavController.executePopOperations` (see
`docs/HOME_AVATAR_PROFILE_NAVIGATION_BUG_FIX.md`, which empirically verified
this on an emulator for a different trigger path): `popUpTo(Home){saveState
= true}` aliases the anchor destination's ID (Home) to whatever it just
popped and saved. A tab reached via a **plain**
`navigate(route){launchSingleTop = true}` — no `popUpTo` — never writes that
aliasing entry on the way in. The next time *any* tab is popped back to Home
via the standard pattern, that pop is the first thing to write a real value
into it, and `restoreState` then restores *that* screen instead of the one
actually requested.

In this repro: Profile → "Recommendations" (plain `navigate`) → Discover
(plain `navigate`, via `RecommendationsScreen.onDiscoverClick`) left
Profile, Recommendations, and Discover all live/unsaved on the back stack.
Tapping the Profile tab re-triggered the same aliasing bug through this
different intermediate path than the one already fixed for the Home avatar.

### Exact navigation fix

Added one shared `navigateToTab(route: String)` function in `WeGlowApp()`
wrapping the existing
`popUpTo(Home){saveState = true}; launchSingleTop = true; restoreState = true`
pattern, and replaced **every** call site that navigated to a tab route
(Home/Discover/Scan/Routines/Profile) via a plain
`navigate(){launchSingleTop = true}` with a call to this helper — including
the bottom nav's own `onSelect` (now delegates to it too). Non-tab
destinations (Recommendations, Account Settings, Saved Products, Cart,
ScanResults, HairstyleResults, onboarding, login) were left untouched.

### Why the fix is consistent with the existing architecture

It's the exact pattern the codebase already established correctly in one
place (Home's avatar → Profile, from the prior fix), now applied uniformly
everywhere a tab can be reached, instead of a Profile-specific patch. One
`NavHost`, one `NavController`, no new navigation concepts.

### Files modified for navigation

`app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt` only.

## Fix 2 — Add to Bag on every Discover product card

### How it was added to regular Discover products

- **Grid cards**: a new compact `AddToBagIconButton` (circular, shopping-cart
  icon, spinner while pending) added alongside the existing Save toggle.
- **List cards**: the same Save + Add to Bag pair added (previously had
  neither action).
- **Featured card**: kept its full-width "Add to Bag" button and gained the
  same Save toggle it was missing, so all three presentations now have full
  parity.

### Grid and list presentations

Both now support Save and Add to Bag identically.

### How existing CartViewModel/CartRepository were reused

No new ViewModel, repository, or state — every card calls the same
`cartViewModel::addToCart` already wired into `DiscoverScreen`'s
`onAddToCart` parameter (unchanged signature from the previous session),
which goes through the existing `CartRepository` → `SupabaseCartRepository`
→ `public.cart_items`.

### Per-product loading/repeated taps

Reused as-is: `CartUiState.pendingProductNumbers: Set<Int>` (already
existed) tracks in-flight per-`catalogNo` operations; each card only
disables/spins for its own `catalogNo`, other cards remain interactive.
`CartViewModel` already guarded duplicate taps per product — no ViewModel
change was needed or made.

### `catalogNo == null` handling

`AddToBagIconButton` (and the featured button) are disabled whenever
`product.catalogNo == null`; `Product.id`/`Product_ID` is never parsed into
an integer anywhere in this fix.

### Save confirmation

Unchanged — `SaveToggleRow` behavior, click targets, and
`SavedProductsViewModel` wiring are untouched.

## Tests added/changed

None. No `CartViewModel`/`CartRepository` behavior changed (only reused
existing state), so no new test coverage was warranted there. No navigation
test was added: this project has no Robolectric/instrumented
navigation-testing infrastructure, and introducing one for this narrow fix
would be disproportionate scope creep; the fix was verified by compiling,
tracing every affected call site by hand, and cross-referencing the
already-empirically-verified root-cause document.

## Gradle verification results

```
./gradlew architectureCheck testDebugUnitTest lintDebug assembleDebug
```

All four (single combined run): **BUILD SUCCESSFUL**.

| Task | Result |
|---|---|
| `architectureCheck` | ✅ BUILD SUCCESSFUL |
| `testDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| `lintDebug` | ✅ BUILD SUCCESSFUL |
| `assembleDebug` | ✅ BUILD SUCCESSFUL |

## `git diff --stat`

```
 .../com/example/weglow/navigation/WeGlowApp.kt     |  92 ++++++++----------
 .../example/weglow/ui/screens/DiscoverScreen.kt    | 103 +++++++++++++++++++--
 2 files changed, 134 insertions(+), 61 deletions(-)
```

## `git status --short` (app source)

```
 M app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt
 M app/src/main/java/com/example/weglow/ui/screens/DiscoverScreen.kt
```

Nothing was staged, committed, or pushed — all changes remain in the working
tree for review.
