# Home Header — Matched to Discover's Exact Geometry

## Summary

A previous pass fixed Home's header by simply removing its fixed `64.dp`
row height. That was insufficient: Home's logo, avatar, and spacing values
still didn't match Discover's actual header geometry. This pass instead
reads Discover's current header implementation directly and adapts Home's
header to reuse those exact values — same logo size, same avatar size, same
`SpaceBetween`/`CenterVertically` convention — with only the Home-specific
notification icon inserted immediately before the avatar, capped so it
cannot make the row taller than Discover's.

## Discover's Actual Header Values (source of truth)

Read directly from `DiscoverScreen.kt`, lines 116–130:

| Property | Discover's actual value |
|---|---|
| Header Row horizontal inset | `24.dp`, via the Row's own `padding(horizontal = 24.dp, ...)` — Discover's `LazyColumn` has no `contentPadding` at all |
| Header Row vertical inset | `16.dp`, via `padding(..., vertical = 16.dp)` |
| Logo size | `Modifier.size(24.dp)` |
| Avatar size | `ProfileAvatar(size = 36.dp)`, plain — no border, no button chrome |
| Arrangement | `horizontalArrangement = Arrangement.SpaceBetween` |
| Alignment | `verticalAlignment = Alignment.CenterVertically` |

```kotlin
// DiscoverScreen.kt — the reference implementation
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 16.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Image(
        painter = painterResource(R.drawable.weglow_logo),
        contentDescription = null,
        modifier = Modifier.size(24.dp)
    )
    ProfileAvatar(image = rememberDecodedBitmap(profileImage), size = 36.dp)
}
```

`DiscoverScreen.kt` was read only — never modified.

## Exactly How Home Was Changed to Reuse These Values

- **Logo**: `Modifier.size(width = 40.dp, height = 44.dp)` → `Modifier.size(24.dp)` — Discover's exact size.
- **Header Row**: replaced `height(64.dp)` + `padding(horizontal = 5.dp)` + a `Spacer(Modifier.weight(1f))` push-apart trick with `fillMaxWidth()` + `horizontalArrangement = Arrangement.SpaceBetween` + `verticalAlignment = Alignment.CenterVertically` — structurally identical to Discover's Row.
- **Horizontal inset**: Home's `LazyColumn` already applies `start/end = 24.dp` via `contentPadding` to every item (Discover applies its 24dp per-item instead, since it has no `contentPadding`). Since that 24dp is already present, the header Row itself now adds **no extra horizontal padding** — this reproduces Discover's 24dp total inset without doubling it.
- **Vertical inset**: Home's `contentPadding.top` changed from `12.dp` → **`16.dp`**, directly reusing Discover's number. Because Home's Row now adds no padding of its own (unlike Discover's Row, which carries the 16dp itself), relocating that same 16dp value to `contentPadding.top` reproduces an identical total vertical offset via Home's different (contentPadding-based, vs. per-item) structure.
- **Avatar**: replaced the `IconButton` (`.size(40.dp)` + a decorative green border + `ProfileAvatar(size = 38.dp)`) with a bare `Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onProfileClick))` wrapping `ProfileAvatar(size = 36.dp)` — matching Discover's 36dp avatar exactly, with no border or button chrome, while preserving click behavior via `clickable` and the `"Open profile"` accessibility label via `semantics`.
- **Notification icon**: kept as an `IconButton`, sized to `36.dp` (capped to match the avatar) so it cannot exceed Discover's tallest-child height of 36dp, placed in a nested `Row` immediately before the avatar.
- **Header-to-content spacing**: the `Spacer` between the header Row and the date text changed from `12.dp` → `16.dp`, reusing Discover's Row-to-content gap (its `vertical = 16.dp` bottom padding).
- Removed the now-unused `androidx.compose.foundation.border` import.

## Full Diff

```diff
diff --git a/app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt b/app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt
@@ -2,7 +2,6 @@ package com.example.weglow.ui.screens
 
 import androidx.compose.foundation.Image
 import androidx.compose.foundation.background
-import androidx.compose.foundation.border
 import androidx.compose.foundation.clickable
 import androidx.compose.foundation.layout.*
 import androidx.compose.foundation.lazy.LazyColumn
@@ -95,34 +94,51 @@ fun HomeScreen(
     LazyColumn(
         state = listState,
         modifier = Modifier.fillMaxSize().background(PageBackground).testTag("home"),
-        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp),
+        // top = 16.dp reproduces DiscoverScreen's header Row `padding(vertical = 16.dp)`:
+        // Discover applies that 16dp directly on its header Row (its LazyColumn has no
+        // contentPadding), while Home's horizontal 24dp is already supplied here for every
+        // item, so the header Row itself adds no additional padding of its own.
+        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp),
         verticalArrangement = Arrangement.spacedBy(20.dp),
     ) {
         item(key = "greeting") {
             Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF8FAF7))) {
+                // Header geometry matches DiscoverScreen's header Row exactly (same
+                // logo size, same avatar size, same SpaceBetween/CenterVertically
+                // convention); the notification icon is the only Home-only addition,
+                // inserted immediately before the avatar and capped to the avatar's
+                // size so it cannot make the row taller than Discover's.
                 Row(
-                    Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 5.dp),
+                    modifier = Modifier.fillMaxWidth(),
+                    horizontalArrangement = Arrangement.SpaceBetween,
                     verticalAlignment = Alignment.CenterVertically,
                 ) {
-                    Image(painterResource(R.drawable.weglow_logo), "WeGlow", Modifier.size(width = 40.dp, height = 44.dp))
-                    Spacer(Modifier.weight(1f))
-                    IconButton(
-                        onClick = { showNotifications = true },
-                        modifier = Modifier.semantics { contentDescription = "Open alerts" },
-                    ) {
-                        Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = DarkGreen)
-                    }
-                    IconButton(
-                        onClick = onProfileClick,
-                        modifier = Modifier
-                            .size(40.dp)
-                            .border(1.dp, Color(0x338BA889), CircleShape)
-                            .semantics { contentDescription = "Open profile" },
-                    ) {
-                        ProfileAvatar(image = rememberDecodedBitmap(profileImage), size = 38.dp)
+                    Image(
+                        painter = painterResource(R.drawable.weglow_logo),
+                        contentDescription = "WeGlow",
+                        modifier = Modifier.size(24.dp),
+                    )
+                    Row(verticalAlignment = Alignment.CenterVertically) {
+                        IconButton(
+                            onClick = { showNotifications = true },
+                            modifier = Modifier
+                                .size(36.dp)
+                                .semantics { contentDescription = "Open alerts" },
+                        ) {
+                            Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = DarkGreen)
+                        }
+                        Box(
+                            modifier = Modifier
+                                .size(36.dp)
+                                .clip(CircleShape)
+                                .clickable(onClick = onProfileClick)
+                                .semantics { contentDescription = "Open profile" },
+                        ) {
+                            ProfileAvatar(image = rememberDecodedBitmap(profileImage), size = 36.dp)
+                        }
                     }
                 }
-                Spacer(Modifier.height(12.dp))
+                Spacer(Modifier.height(16.dp))
                 Text(today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())),
                     style = MaterialTheme.typography.labelMedium, color = SoftGray)
                 Spacer(Modifier.height(4.dp))
```

## File(s) Modified

Only `app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt`.
`DiscoverScreen.kt` was read for reference only and never written to.

## Confirmations

- **No redesign**: date, greeting, "A little care, every day.", Skin sync,
  AI Face Analysis, Environment, Routine, shortcuts, cards, colors,
  typography, bottom navigation — all untouched (verified in the diff; only
  the header block and one `contentPadding` value changed).
- **Avatar navigation fix intact**: tested live on an emulator — Home →
  avatar → Profile → bottom-nav Home works correctly.
- **Scroll-reset fix intact**: `remember { LazyListState() }` untouched;
  Home still opens at the top after leaving and returning via bottom nav.
- `WeGlowApp.kt` was not touched.

## Gradle Validation Results

| Task | Result |
|---|---|
| `architectureCheck` | ✅ BUILD SUCCESSFUL |
| `compileDebugKotlin` | ✅ BUILD SUCCESSFUL |
| `testDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| `assembleDebug` | ✅ BUILD SUCCESSFUL |

(`lintDebug` was not part of this task's requested validation list; no
unrelated changes were made for the known pre-existing `NonObservableLocale`
issue.)

## `git diff --name-only`

```
app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt
```

Only that one file changed. No git operations (`add`, `commit`, `push`,
etc.) were run.

## Visual Verification

Side-by-side emulator screenshots (Home vs. Discover, same session) show
Home's logo, notification icon, and avatar now sitting at very close to the
same vertical and horizontal position as Discover's header. Final
confirmation is left to the user on the physical device used for the
original reference screenshot.
