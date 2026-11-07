# Discover Price Filter Regression — Investigation Report

Investigation only. No source files were modified as part of this report.

## A. Root cause

`SupabaseCatalogRepository.toProductOrNull()` maps the price field using an ordered list of
candidate column names, but its lookup helper (`matchingElement`) does an **exact,
case-sensitive key match across *all* candidate names before ever falling back to normalized
(case/underscore-insensitive) matching**. The live schema has both `Price_LKR` (numeric) and
`price_text_raw` (text). Because `"price_text_raw"` is in the candidate list and matches a live
column name *exactly* (case included), it wins the exact-match pass immediately — before the
normalized pass that would have matched `"price_lkr"` → `Price_LKR` ever runs. So `priceLkr` is
populated from the unreliable **`price_text_raw`** text column, not the authoritative numeric
`Price_LKR` column, regardless of candidate ordering.

`price_text_raw` is a free-form text field (not guaranteed to be clean numeric text). Wherever it
is blank, non-numeric, or otherwise fails the `PRICE_NUMBER` regex extraction, `Product.priceLkr`
ends up `null` for that row. In `DiscoverScreen`'s filter predicate, any product with
`priceLkr == null` is **excluded** the moment a min or max price is set
(`product.priceLkr?.let {...} == true` evaluates to `false` for null). If `price_text_raw` is
null/blank/malformed across most or all rows in the current dataset, every product loses
price-filter eligibility simultaneously — which is exactly the "0 products" symptom, while
unfiltered browsing still works (unfiltered mode never reads `priceLkr` at all, only the display
label `priceLabel`).

## B. Exact affected file(s)

- `app/src/main/java/com/example/weglow/data/repository/SupabaseCatalogRepository.kt` — defect
  origin (price field mapping precedence).
- `app/src/main/java/com/example/weglow/ui/screens/DiscoverScreen.kt` — where the resulting
  `null` `priceLkr` gets converted into "0 products" (the filter predicate itself is logically
  correct, it's just being fed bad/missing data).

## C. Current broken logic

`SupabaseCatalogRepository.kt:44-52`:

```kotlin
val price = text(
    "price_lkr",
    "price",
    "lkr_price",
    "product_price",
    "selling_price",
    "sale_price",
    "price_text_raw",   // <- added in commit 32cc3a5
)
```

`matchingElement` (`SupabaseCatalogRepository.kt:96-103`):

```kotlin
keys.firstNotNullOfOrNull { key -> this[key] }?.let { return it }   // exact match wins first, ANY key
val normalizedKeys = keys.mapTo(mutableSetOf(), String::normalizedColumnName)
return entries.firstOrNull { (columnName, _) -> columnName.normalizedColumnName() in normalizedKeys }?.value
```

Since `"price_text_raw"` matches a real column exactly, the numeric `Price_LKR` column (which
only matches via the second, normalized pass) is never reached.

## D. Example showing why a valid product becomes excluded

Product row: `Price_LKR = 2500`, `price_text_raw = ""` (or any non-numeric/blank value, which is
a realistic state for a "raw" free-text price column).

- `matchingElement("price_lkr", ..., "price_text_raw")` returns the `price_text_raw` element
  (exact match) → value `""`.
- `"".toLkrAmount()` → regex finds nothing → `priceLkr = null`.
- User applies min=1000, max=5000. Filter: `product.priceLkr?.let {...} == true` →
  `null?.let{} == true` → `false`.
- Product is excluded even though its real price (2500) is well inside [1000, 5000]. If this
  pattern is common across the dataset, the visible count goes to 0.

## E. Whether Supabase data/mapping is involved

Yes — this is fundamentally a **mapping bug**, not a Supabase config/data issue. Supabase is
returning correct rows; the app-side column-precedence logic silently discards the numeric
`Price_LKR` value in favor of a free-text field it shouldn't have been trusted over.

## F. Whether recent teammate merge caused it

**No.** This was not introduced by the face-shape/TFLite integration (`19d09f1`, `2c88a23`,
`bc1c2c0`, `b1e6188`) or the CameraX 16 KB compatibility fix (`db6d720`, `5f07681`). Neither
branch touches `SupabaseCatalogRepository.kt` at all — verified via `git log` and the commit
graph. The defect was introduced earlier, in commit **`32cc3a5` — "chore: harden security and
improve app performance"** — which added `"price_text_raw"` to the candidate-key list without
accounting for `matchingElement`'s exact-match-first precedence. It has been sitting on `main`
since before either recent feature branch was created, and both branches simply merged it in
unchanged.

## G. Existing test coverage

- `SupabaseCatalogRepositoryTest.productRow_mapsRealDatasetColumnCasing` (line 78) tests
  `Price_LKR` mapping — but **only with `Price_LKR` present, `price_text_raw` absent**. It never
  exercises the case where both columns coexist, so it can't catch the precedence bug.
- No test in the suite ever supplies both `Price_LKR` and `price_text_raw` on the same row.
- No test exists for the actual filter predicate (`DiscoverScreen`'s `filteredProducts`
  `remember` block) at all — it's inline in a private `@Composable` and isn't covered by
  `DiscoverViewModelTest` (which only tests loading/caching, not filtering; `DiscoverViewModel`
  has no price-filter state — filtering lives entirely in the UI layer).
- Cached results (`app/build/test-results/testDebugUnitTest/...`) confirm both suites pass fully:
  `SupabaseCatalogRepositoryTest` 7/7, `DiscoverViewModelTest` 4/4, timestamped today. (Gradle/Java
  is not available in this environment, so live re-execution wasn't possible; findings rely on
  inspecting these existing result artifacts plus manual trace.)

## H. Existing tests pass despite the runtime bug — why

None of the current tests construct a row containing **both** `Price_LKR` and `price_text_raw`
together, which is the actual live schema shape. So the exact-match-precedence flaw never gets
exercised. Additionally, there is zero test coverage of the price-filter predicate itself
(min-only, max-only, both, boundaries, decimals, blank/invalid input, null-price products) since
that logic lives inside a private Composable in `DiscoverScreen.kt` rather than in a
testable/pure function.

## I. Minimum safe fix proposed (NOT applied — awaiting approval)

In `text()`'s candidate-key resolution, prefer the normalized/case-insensitive match consistently,
or reorder priority so a dedicated numeric field always outranks `price_text_raw` regardless of
exact-case luck — e.g., resolve `price_lkr`-equivalent columns (via normalized match) before ever
considering `price_text_raw`, or simply keep `price_text_raw` only as a last-resort fallback used
solely for the *display label*, not for `priceLkr`. This is a small, localized change to
`toProductOrNull()`/`matchingElement` in `SupabaseCatalogRepository.kt` only — no UI, no Supabase,
no filter-logic changes needed.

## J. Tests that should be added

1. `SupabaseCatalogRepositoryTest`: a row with both `Price_LKR` (numeric, e.g. `2500`) and
   `price_text_raw` (a differently-formatted or blank string) present together — assert
   `priceLkr` comes from `Price_LKR`.
2. A row where `price_text_raw` is blank/non-numeric and `Price_LKR` is valid — assert `priceLkr`
   is still correctly populated (not null).
3. Extract the Discover price-filter predicate into a small pure/testable function and add unit
   tests for: min only, max only, min+max, boundary equality (price == min, price == max),
   decimal prices, blank min/max, invalid input, and products with `priceLkr == null`.

## K. Risk to Discover/recommendations/routines from the proposed fix

Low, if scoped correctly: the fix only changes which source column feeds
`priceLkr`/`priceLabel` construction inside `SupabaseCatalogRepository`. `Product.priceLkr` is
currently only consumed by the Discover price filter (per this trace); `priceLabel` feeds display
text everywhere products are shown (Discover, and potentially Recommendations/Routines cards) —
care must be taken that the fix doesn't change `priceLabel`'s fallback behavior for products that
only have `price_text_raw` and no numeric field, so as not to regress display of prices for any
edge-case rows lacking `Price_LKR`.

---

**SAFE TO PROCEED WITH FIX: NO** — awaiting explicit approval before making any code changes.
