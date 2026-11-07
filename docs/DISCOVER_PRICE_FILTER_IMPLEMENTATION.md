# Discover Price Filter Fix — Implementation Report

## Root Cause

`SupabaseCatalogRepository.matchingElement()` matches exact keys across **all** candidate
column names before ever falling back to normalized (case/underscore-insensitive) matching.
`price_text_raw` was included in the same candidate list as `price_lkr`, so it won the
exact-match pass and shadowed the authoritative `Price_LKR` column (which only matches via
normalization). As a result, `Product.priceLkr` was frequently populated from the unreliable
free-text `price_text_raw` field — becoming `null` whenever that field was blank or
non-numeric — and Discover's price filter silently excludes any product with `priceLkr == null`.

## Files Changed

1. `app/src/main/java/com/example/weglow/data/repository/SupabaseCatalogRepository.kt`
2. `app/src/main/java/com/example/weglow/ui/screens/DiscoverScreen.kt`
3. `app/src/test/java/com/example/weglow/data/repository/SupabaseCatalogRepositoryTest.kt`
4. `app/src/test/java/com/example/weglow/ui/screens/DiscoverPriceFilterTest.kt` (new)

## Exact Mapping Fix

`price_text_raw` is now looked up in its own separate `text("price_text_raw")` call, no longer
sharing a candidate list with the numeric price keys (`price_lkr`, `price`, `lkr_price`,
`product_price`, `selling_price`, `sale_price`):

```kotlin
val numericPrice = text(
    "price_lkr",
    "price",
    "lkr_price",
    "product_price",
    "selling_price",
    "sale_price",
)
val rawTextPrice = text("price_text_raw")
val price = numericPrice ?: rawTextPrice
...
priceLkr = numericPrice.toLkrAmount() ?: rawTextPrice.toLkrAmount(),
```

- Numeric `Price_LKR` (or an equivalent alias) always wins when parseable.
- `price_text_raw` only feeds `priceLkr`/`priceLabel` when no numeric price exists.
- `matchingElement()` itself was **not** modified — no other field mappings are affected.

## Discover Filter Testability

The price predicate was extracted into a top-level pure function in `DiscoverScreen.kt`, with
identical logic to the original inline block — no UI, state, or architecture changes:

```kotlin
internal fun matchesPriceRange(priceLkr: Double?, minimumPrice: Double?, maximumPrice: Double?): Boolean {
    if (minimumPrice == null && maximumPrice == null) return true
    return priceLkr?.let { price ->
        (minimumPrice == null || price >= minimumPrice) &&
            (maximumPrice == null || price <= maximumPrice)
    } == true
}
```

## Tests Added

**`SupabaseCatalogRepositoryTest`** — 6 new tests:
- Blank `price_text_raw` + numeric `Price_LKR` → numeric wins (mandatory live-shaped case)
- Conflicting `price_text_raw` value + numeric `Price_LKR` → numeric wins
- Decimal `Price_LKR` value parses correctly
- Valid numeric `Price_LKR` with non-numeric `price_text_raw` → numeric wins
- Fallback to `price_text_raw` when `Price_LKR` genuinely does not exist

Suite total: **12/12 passing**.

**`DiscoverPriceFilterTest`** (new) — 10 tests covering: no range set, min only, max only,
min + max, exact lower boundary, exact upper boundary, decimal price, blank min/max, and null
product price — including the mandated case `price = 2500, min = 1000, max = 5000 → INCLUDED`.

All **10/10 passing**.

## Gradle Gate Results

Run with `JAVA_HOME` pointed at Android Studio's bundled JBR (no system Java runtime was
present in this environment):

| Task | Result |
|---|---|
| `./gradlew architectureCheck` | BUILD SUCCESSFUL |
| `./gradlew compileDebugKotlin` | BUILD SUCCESSFUL |
| `./gradlew testDebugUnitTest` | BUILD SUCCESSFUL — **150/150 tests passed, 0 failures, 0 errors** |
| `./gradlew lintDebug` | BUILD SUCCESSFUL |
| `./gradlew assembleDebug` | BUILD SUCCESSFUL |

## Confirmations

- ✅ `Price_LKR = 2500` within range `1000–5000` → **passes** (verified by both the repository
  test and the predicate test).
- ✅ `price_text_raw` can no longer override a valid numeric `Price_LKR` (verified by the
  conflicting-value regression test).
- ✅ Discover UI/design → **unchanged** (pure predicate extraction only; no layout, composable
  tree, or state changes).
- ✅ CameraX remains **1.4.1** (`gradle/libs.versions.toml:12`, untouched).
- ✅ No Supabase schema/data, navigation, recommendations scoring, routines, authentication,
  CameraX, ONNX, LiteRT/TFLite, ML Kit, face-shape functionality, or 16 KB compatibility
  configuration was touched.
- ✅ No git add/commit/push/merge performed; pre-existing untracked documentation files
  untouched.

## SAFE TO COMMIT: YES
