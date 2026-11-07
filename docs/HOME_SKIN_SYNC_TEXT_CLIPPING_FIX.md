# "Skin Sync" Text Clipping Fix (Home Greeting Card)

## Summary

The first letter "S" in the "Skin sync · Coming soon" line under the Home
greeting appeared partially clipped on its left side. Root-caused to the
greeting card's rounded-corner clip having zero padding, so the last line
of text sat inside the bottom-left corner's clip arc. Fixed with a single
`padding(bottom = 16.dp)` addition — no redesign, no header changes, no
other content moved.

## 1. Exact Root Cause

The greeting card's outer `Column` was:

```kotlin
Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF8FAF7))) {
```

with **no padding at all**. Every child — including the last line,
`Text("Skin sync · Coming soon", ...)` — sat flush against the Column's raw
edges: flush left at x=0, and, since there was no bottom padding either,
flush against the card's bottom edge too.

Because `clip(RoundedCornerShape(16.dp))` rounds all four corners of that
same box, and "Skin sync" was the content closest to the **bottom-left
corner** (within its 16dp arc both horizontally, at x=0, and vertically, at
the card's bottom edge), the rounded-corner arc sliced through the
leftmost pixels of that line — cutting off the left curve of the "S".

This was confirmed empirically, not just theoretically: the pre-fix code
was rebuilt and installed on an emulator, and a zoomed screenshot crop
shows the "S" with its top-left curve visibly flattened. The same crop
taken after the fix shows the full, unclipped glyph.

Other candidate causes were checked and ruled out:
- No fixed width/size constraint on the `Text` or its ancestors.
- No negative offset or `graphicsLayer` modifiers anywhere in this section.
- Modifier ordering on the header Row (fixed in a prior pass) was already
  correct and unrelated to this issue.
- The typography style (`bodySmall`) has no unusual glyph overhang; the
  clipping was purely a layout/clip-boundary interaction, not a font issue.

## 2. Exact Code Changed

File: `app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt`

```diff
-Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF8FAF7))) {
+Column(
+    Modifier
+        .fillMaxWidth()
+        .clip(RoundedCornerShape(16.dp))
+        .background(Color(0xFFF8FAF7))
+        .padding(bottom = 16.dp),
+) {
```

Nothing else changed — the header Row, avatar, notification icon, and all
text content, order, and styling are untouched.

## 3. Why the Fix Prevents Clipping

Adding `.padding(bottom = 16.dp)` **after** `.clip()` / `.background()` in
the modifier chain means the card's rounded shape and background are
computed over the full size *including* that trailing 16dp of space, while
the actual text content is measured and drawn inside that padded area. This
pushes the last line's bottom edge 16dp above the card's bottom edge —
clearing the 16dp corner radius entirely, so no text row is close enough to
a corner arc to be sliced.

The `16.dp` value is not arbitrary: it exactly matches the card's own
corner radius, reusing the same "pad by the corner radius" convention
already used elsewhere in this same file — `HomeScanHero`'s 20dp-corner
card is padded by exactly `20.dp`. Because the added padding is
`bottom`-only, the header's top position and every text's horizontal
position are completely unchanged; only the whitespace below "Skin sync"
increased slightly.

## 4. Visual Verification (Before / After)

The pre-fix code was temporarily restored (via `git stash`), rebuilt,
installed, and screenshotted, then the fix was restored (`git stash pop`)
and rebuilt again, to get a direct, same-device comparison:

- **Before**: zoomed crop shows the "S" in "Skin sync" with its top-left
  curve visibly flattened by the corner clip.
- **After**: zoomed crop shows the full, rounded "S" glyph rendering
  normally.

## 5. File(s) Modified

Only `app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt`.

## 6. Confirmations

- **Text unchanged**: "Sync Now" / "Skin sync · Coming soon" wording was
  not touched.
- **Header unchanged**: the previously-fixed header Row (logo, notification
  icon, avatar, geometry matching Discover) was not modified.
- **Avatar navigation fix intact**: verified live — Home → avatar → Profile
  → bottom-nav Home still works correctly.
- **Scroll-reset fix intact**: `remember { LazyListState() }` untouched;
  Home still opens at the top after leaving and returning via bottom nav.
- `WeGlowApp.kt` was not touched; no other screen was modified.

## 7. `git diff --name-only`

```
app/src/main/java/com/example/weglow/ui/screens/HomeScreen.kt
```

## 8. Gradle Validation Results

| Task | Result |
|---|---|
| `architectureCheck` | ✅ BUILD SUCCESSFUL |
| `compileDebugKotlin` | ✅ BUILD SUCCESSFUL |
| `testDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| `assembleDebug` | ✅ BUILD SUCCESSFUL |

No git operations (`add`, `commit`, `push`, `merge`, `reset`, `checkout`)
were run, per instructions.
