# Home Header Vertical Alignment Fix

## Summary

Home's top header area (WeGlow/star logo, notification icon, profile
avatar) had noticeably too much empty vertical space around it compared to
the same region on Discover. The Home design itself was approved and
unchanged — this was purely a spacing/alignment defect. Fixed with a single,
non-arbitrary line change in `HomeScreen.kt`.

## 1. Exact Cause of the Home Header Alignment Difference

Home's header `Row` (containing the logo, notification icon, and avatar)
had a **fixed `.height(64.dp)`**:

```kotlin
Row(
    Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
) { ... }
```

Discover's equivalent header `Row` has **no height constraint at all** — it
sizes to its tallest child:

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 16.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) { ... }
```

Discover's tallest child is its 36dp avatar, so its Row naturally comes out
about 36dp tall. Home's Row was forced to 64dp regardless of its content —
its tallest child would naturally be the notification `IconButton`'s
Material3 default 48dp touch target (`IconButton` has "an overall minimum
touch target size of 48 x 48dp" per its own documentation, confirmed against
the exact `material3-android:1.4.0` sources this project depends on). That
forced 64dp band, combined with `verticalAlignment = Alignment.CenterVertically`,
added visible empty space above and around the logo/icons that Discover
simply doesn't have.

Both screens are rendered inside the same `Scaffold`/`NavHost` in
`WeGlowApp.kt` with identical outer padding, so the discrepancy was entirely
internal to each screen's own header layout — not a status-bar-inset or
navigation-chrome difference.

## 2. Exact Home Spacing/Height Value Changed

Removed `.height(64.dp)` from the header `Row`'s modifier chain in
`HomeScreen.kt`, inside the `item(key = "greeting")` block. Nothing else in
that `Row` — icon sizes, spacing, horizontal padding, alignment — was
touched.

## 3. Discover Value/Convention Used as Reference

Discover's header `Row` has no explicit height — it lets `fillMaxWidth()`
plus its children (a 24dp logo image and 36dp avatar) determine the Row's
height naturally. Home's Row now follows that same "content-sized, no fixed
height" convention rather than an arbitrary dp value, per the instruction
not to guess a random number.

## 4. File(s) Modified

Only `app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt`.

### Diff

```diff
diff --git a/app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt b/app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt
@@ -101,7 +101,11 @@ fun HomeScreen(
         item(key = "greeting") {
             Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF8FAF7))) {
                 Row(
-                    Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 5.dp),
+                    // No fixed height here, matching DiscoverScreen's header Row
+                    // convention: the row sizes to its tallest child instead of an
+                    // arbitrary fixed height, which was adding extra empty space
+                    // above/around the logo, notification icon, and avatar.
+                    Modifier.fillMaxWidth().padding(horizontal = 5.dp),
                     verticalAlignment = Alignment.CenterVertically,
                 ) {
                     Image(painterResource(R.drawable.weglow_logo), "WeGlow", Modifier.size(width = 40.dp, height = 44.dp))
```

## 5. Confirmation: No Home Redesign Occurred

Confirmed. No changes to logo artwork, notification icon, avatar, avatar
click behavior, date/greeting text or styling, fonts, font sizes, colors,
cards, the AI Face Analysis section, the Environment section, routines,
shortcuts, images, bottom navigation, section ordering, or card
sizing/styling. The only change is removing one fixed-height constraint on
the header `Row`.

## 6. Confirmation: Avatar Navigation Fix Remains Intact

Verified live on an emulator: **Home → avatar → Profile → bottom-nav Home**
still correctly returns to Home. `WeGlowApp.kt` (where that fix lives) was
not touched by this change.

## 7. Confirmation: Tab Scroll-Reset Behavior Remains Intact

The `remember { LazyListState() }` scroll-reset implementation in Home was
untouched — the diff shows only the header `Row`'s modifier changed, nothing
near the `listState` declaration. Verified visually: Home still opens at the
top after the avatar → Profile → Home round trip.

## 8. Gradle Validation Results

| Task | Result |
|---|---|
| `architectureCheck` | ✅ BUILD SUCCESSFUL |
| `compileDebugKotlin` | ✅ BUILD SUCCESSFUL |
| `testDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| `lintDebug` | ❌ FAILED — pre-existing, unrelated issue (see below) |
| `assembleDebug` | ✅ BUILD SUCCESSFUL |

**Lint failure detail:** `HomeScreen.kt:130`, `NonObservableLocale` for a
`Locale.getDefault()` call inside a composable. This is the same known
pre-existing issue — the line number shifted from 126 to 130 only because
of the comment lines this fix added above it; the flagged code itself was
not touched, per instructions not to perform unrelated changes to silence it.

## 9. `git diff --name-only`

```
app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt
```

No git operations (`add`, `commit`, `push`, `merge`, `pull`, `checkout`,
`switch`, `reset`, `restore`) were run, per instructions.

## Visual Verification

Before/after screenshots were captured on the emulator, side by side with
Discover, showing Home's header collapsing from a noticeably taller band
down to a position closely matching Discover's header start. Final visual
sign-off is left to the user on the same physical device used for the
original comparison.
