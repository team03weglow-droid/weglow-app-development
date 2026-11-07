# Saved Products & Cart Implementation

Continuation of the Profile / Account Settings / Cart / Saved Products / Order
History task, now that the Supabase backend for Saved Products and Cart has
been manually created and verified. This report covers what was implemented
against the confirmed live schema (`public.saved_products`, `public.cart_items`,
and `products."No"` as the real relational key), what was deliberately left
alone (Order History), and full verification results.

No database migrations were run, no existing tables/policies were touched,
and nothing was committed, staged, or pushed — all changes remain in the
working tree for review.

## 1. Architecture used

Unchanged from the existing pattern, extended consistently:

```
Compose UI → ViewModel → Domain repository interface → Supabase repository implementation → Supabase
```

No second `NavHost`, no second `Product`/user-identity model, no Supabase
calls inside any composable.

## 2. Domain models added/changed

- **`Product.kt`** — added `catalogNo: Int? = null`, the real
  `products."No"` smallint PK. Nullable/optional so every existing call site
  (7 test files) compiles unchanged; only rows lacking it are treated as
  "not save/cart-able," never fabricated.
- **`CartItem.kt`** (new) — `data class CartItem(val product: Product, val quantity: Int)`.

## 3. Repositories added/changed

- **`SavedProductRepository`** (new interface) + **`SupabaseSavedProductRepository`**
  (new impl) — `getSavedProducts`, `saveProduct`, `removeSavedProduct`, all
  keyed on `userId` + `productNo`.
- **`CartRepository`** (new interface) + **`SupabaseCartRepository`** (new
  impl) — `getCartItems`, `addToCart`, `updateQuantity`, `removeFromCart`.
- **`SupabaseCatalogRepository.toProductOrNull()`** — now also extracts
  `catalogNo` from the confirmed `"No"` column.
- New data DTOs: `data/model/SavedProductRow.kt` (`SavedProductInsert`),
  `data/model/CartItemRow.kt` (`CartItemInsert`, `CartQuantityRow`) — same
  convention as the existing `ScanProfileRow.kt`.
- **`AppContainer`** — wired `savedProductRepository`, `cartRepository`.

## 4. ViewModels added/changed

- **`SavedProductsViewModel`** (new) — shared by Discover's heart toggle and
  the Saved Products screen; `load`, `toggleSaved`, `save`/`remove`,
  `consumeError`, `clear`.
- **`CartViewModel`** (new) — shared by Discover's "Add to Bag," Saved
  Products' "Add to Cart," and the Cart screen; `load`, `addToCart`,
  `incrementQuantity`, `decrementQuantity`, `removeFromCart`, `consumeError`,
  `clear`.
- **`ProfileViewModel`** — untouched beyond the previous session's work (no
  changes this turn).

## 5. Screens added/changed

- **`SavedProductsScreen.kt`** (new), **`CartScreen.kt`** (new).
- **`DiscoverScreen.kt`** — real Save (heart) toggle on grid cards, real
  "Add to Bag" on the featured card, new Cart icon in the header.
- **`ProfileScreen.kt`** — "Saved Products" is now real and clickable (moved
  into the "Quick links" card, `BookmarkBorder` icon); "Order History" is
  untouched (`· Coming soon`), per the explicit instruction.

## 6. Navigation changes

Added `Destination.SavedProducts` (`saved_products`) and `Destination.Cart`
(`cart`) as two new `composable(...)` entries in the existing `WeGlowApp.kt`
`NavHost` — no second `NavHost`. Back navigation uses the standard
`navController.popBackStack()`. Bottom navigation bar is untouched; Cart is
reached via a new icon in Discover's header (not a bottom-nav tab), Saved
Products via Profile's Quick links.

## 7. Discover integration changes

- Grid card "Save" placeholder → real heart toggle (filled/outlined, spinner
  while pending, tap disabled if `catalogNo` is null).
- Featured card "Add to Bag" placeholder → real button (spinner while
  pending, disabled if `catalogNo` is null).
- New Cart icon button in the header, next to the avatar (mirrors Home's
  notification-bell pattern).
- List-view cards were left exactly as they were — they never had a Save or
  Add-to-Bag placeholder to begin with, so nothing was added there (avoids
  redesigning Discover beyond the two named placeholders).

## 8. How `products."No"` is represented in Android

`Product.catalogNo: Int?`, parsed in
`SupabaseCatalogRepository.toProductOrNull()` from the exact confirmed
column `"No"` (with `no`/`row_no` as harmless normalized-fallback candidates
only). It is completely separate from `Product.id` (the flexible display
identifier, often `Product_ID` text like `"SPA_0001"`) — `id` is never
parsed into `catalogNo`, and `catalogNo` is never used for display/matching.
Every Saved Products/Cart repository call uses `catalogNo` exclusively.

## 9. Saved Products behavior

Load, save, remove, and "is this saved" (derived as `Set<Int>` from the
loaded list, no extra round trip) are all implemented. Re-saving an
already-saved product is a safe no-op both server-side
(`upsert(ignoreDuplicates = true)` on `UNIQUE(profile_id, product_no)`) and
client-side (a pending-set guard blocks a duplicate tap while one is in
flight). Loading/Success/Empty ("No saved products yet")/Error states are
all handled; errors surface the real repository message, never a fabricated
one.

## 10. Cart behavior

Load, add, increment, decrement, explicit remove, and subtotal (computed
from each line's **current** `Product.priceLkr` × quantity, never a stored
snapshot) are all implemented. Decrementing from quantity 1 removes the line
— the one documented, consistent behavior for reaching zero; quantity is
never allowed ≤ 0 (`require(quantity > 0)` in the repository as a second
layer under the ViewModel's own guard). Loading/Success/Empty ("Your cart is
empty")/Error states are all handled. The Cart screen shows a subtotal and a
`WeGlowPlannedFeature("Checkout")` row — an honest stop, no fake
order/payment.

## 11. Duplicate/concurrency handling

No new database function was needed or added. `addToCart` uses two
Postgres-guaranteed atomic primitives, retried as an optimistic-concurrency
loop (up to 5 attempts):

1. **No existing line:** `upsert(..., onConflict = "profile_id,product_no", ignoreDuplicates = true)`
   — an atomic `INSERT ... ON CONFLICT DO NOTHING`. A losing concurrent
   insert affects zero rows (never a duplicate row, never a thrown error).
2. **Existing line:** a compare-and-swap
   `UPDATE ... SET quantity = <old+1> WHERE ... AND quantity = <old>` —
   atomic per Postgres row-locking; if a concurrent writer already changed
   the quantity, this affects zero rows and the loop retries with a fresh
   read.

This was verified against the actual `postgrest-kt` 3.x API (decompiled the
local Gradle-cached jar to confirm `UpsertRequestBuilder.ignoreDuplicates` /
`onConflict`, `PostgrestResult.decodeList` row-count semantics, `Columns.raw`
for the join selects) before writing it, since correctness here couldn't be
guessed.

## 12. Account Settings status

Fully preserved from the previous session, unchanged in behavior. Still
reads/writes through `ProfileViewModel` → `ProfileRepository` →
`SupabaseProfileRepository`; gender still uses `profiles.gender` as the
single source of truth via the same `onGenderSelected` path — no duplicate
gender logic was added.

## 13. Order History status

Left exactly as `"Order History · Coming soon"`, non-clickable. No fake
orders, no fake payment, no local order history, no pretended checkout.
Untouched pending a real checkout/payment decision.

## 14. Production files modified

`AppContainer.kt`, `SupabaseAuthRepository.kt`, `SupabaseCatalogRepository.kt`,
`Product.kt`, `AuthRepository.kt`, `ProfileViewModel.kt` (only the same
email field from the previous session, untouched this turn), `Destination.kt`,
`WeGlowApp.kt`, `DiscoverScreen.kt`, `ProfileScreen.kt`.

**New:**
- `app/src/main/java/com/example/weglow/data/model/CartItemRow.kt`
- `app/src/main/java/com/example/weglow/data/model/SavedProductRow.kt`
- `app/src/main/java/com/example/weglow/data/repository/SupabaseCartRepository.kt`
- `app/src/main/java/com/example/weglow/data/repository/SupabaseSavedProductRepository.kt`
- `app/src/main/java/com/example/weglow/domain/model/CartItem.kt`
- `app/src/main/java/com/example/weglow/domain/repository/CartRepository.kt`
- `app/src/main/java/com/example/weglow/domain/repository/SavedProductRepository.kt`
- `app/src/main/java/com/example/weglow/feature/cart/CartViewModel.kt`
- `app/src/main/java/com/example/weglow/feature/savedproducts/SavedProductsViewModel.kt`
- `app/src/main/java/com/example/weglow/ui/screens/CartScreen.kt`
- `app/src/main/java/com/example/weglow/ui/screens/SavedProductsScreen.kt`

## 15. Test files modified

`SupabaseCatalogRepositoryTest.kt` (+3 `catalogNo` tests), `AuthViewModelTest.kt`,
`OnboardingViewModelTest.kt`, `RecommendationViewModelTest.kt`,
`RoutineViewModelTest.kt` (unrelated `currentUserEmail()` fake additions,
needed only because `AuthRepository` gained that method last session and
these fakes hadn't all been touched — no new changes here this turn beyond
what compiles).

**New:**
- `app/src/test/java/com/example/weglow/feature/cart/CartViewModelTest.kt` (14 tests)
- `app/src/test/java/com/example/weglow/feature/savedproducts/SavedProductsViewModelTest.kt` (10 tests)

## 16. Gradle verification results

```
./gradlew architectureCheck testDebugUnitTest lintDebug assembleDebug
```

All four: **BUILD SUCCESSFUL**. New/updated test classes: `CartViewModelTest`
14/14 passed, `SavedProductsViewModelTest` 10/10 passed,
`SupabaseCatalogRepositoryTest` 15/15 passed (0 skipped, 0 failed across all
three).

One architecture-check violation was hit and fixed during implementation: a
KDoc comment in `CartRepository.kt` (domain layer) literally contained the
substring `com.example.weglow.data` in a `[ClassName]` reference — the check
does a text scan, not just import parsing. Fixed by rewording the comment;
no real dependency existed.

## 17. `git diff --stat`

```
 .../java/com/example/weglow/app/AppContainer.kt    |  12 ++
 .../data/repository/SupabaseAuthRepository.kt      |   3 +
 .../data/repository/SupabaseCatalogRepository.kt   |   4 +
 .../com/example/weglow/domain/model/Product.kt     |  10 ++
 .../weglow/domain/repository/AuthRepository.kt     |   8 +
 .../weglow/feature/profile/ProfileViewModel.kt     |   5 +
 .../com/example/weglow/navigation/Destination.kt   |   3 +
 .../com/example/weglow/navigation/WeGlowApp.kt     |  93 ++++++++++-
 .../example/weglow/ui/screens/DiscoverScreen.kt    |  87 ++++++++++-
 .../com/example/weglow/ui/screens/ProfileScreen.kt | 141 ++---------------
 .../repository/SupabaseCatalogRepositoryTest.kt    |  38 +++++
 .../weglow/feature/auth/AuthViewModelTest.kt       |   1 +
 .../feature/onboarding/OnboardingViewModelTest.kt  |   2 +
 .../weglow/feature/profile/ProfileViewModelTest.kt |  18 +++
 .../recommendation/RecommendationViewModelTest.kt  |   1 +
 .../weglow/feature/routine/RoutineViewModelTest.kt |   1 +
 docs/ARCHITECTURE_REVIEW.md                        | 170 ++++++++++-----------
 17 files changed, 376 insertions(+), 221 deletions(-)
```

(plus 13 new untracked files listed in §14/§15; `docs/ARCHITECTURE_REVIEW.md`
predates this session)

## 18. `git status --short`

```
 M app/src/main/java/com/example/weglow/app/AppContainer.kt
 M app/src/main/java/com/example/weglow/data/repository/SupabaseAuthRepository.kt
 M app/src/main/java/com/example/weglow/data/repository/SupabaseCatalogRepository.kt
 M app/src/main/java/com/example/weglow/domain/model/Product.kt
 M app/src/main/java/com/example/weglow/domain/repository/AuthRepository.kt
 M app/src/main/java/com/example/weglow/feature/profile/ProfileViewModel.kt
 M app/src/main/java/com/example/weglow/navigation/Destination.kt
 M app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt
 M app/src/main/java/com/example/weglow/ui/screens/DiscoverScreen.kt
 M app/src/main/java/com/example/weglow/ui/screens/ProfileScreen.kt
 M app/src/test/java/com/example/weglow/data/repository/SupabaseCatalogRepositoryTest.kt
 M app/src/test/java/com/example/weglow/feature/auth/AuthViewModelTest.kt
 M app/src/test/java/com/example/weglow/feature/onboarding/OnboardingViewModelTest.kt
 M app/src/test/java/com/example/weglow/feature/profile/ProfileViewModelTest.kt
 M app/src/test/java/com/example/weglow/feature/recommendation/RecommendationViewModelTest.kt
 M app/src/test/java/com/example/weglow/feature/routine/RoutineViewModelTest.kt
 M docs/ARCHITECTURE_REVIEW.md
?? app/src/main/java/com/example/weglow/data/model/CartItemRow.kt
?? app/src/main/java/com/example/weglow/data/model/SavedProductRow.kt
?? app/src/main/java/com/example/weglow/data/repository/SupabaseCartRepository.kt
?? app/src/main/java/com/example/weglow/data/repository/SupabaseSavedProductRepository.kt
?? app/src/main/java/com/example/weglow/domain/model/CartItem.kt
?? app/src/main/java/com/example/weglow/domain/repository/CartRepository.kt
?? app/src/main/java/com/example/weglow/domain/repository/SavedProductRepository.kt
?? app/src/main/java/com/example/weglow/feature/cart/
?? app/src/main/java/com/example/weglow/feature/savedproducts/
?? app/src/main/java/com/example/weglow/ui/screens/AccountSettingsScreen.kt
?? app/src/main/java/com/example/weglow/ui/screens/CartScreen.kt
?? app/src/main/java/com/example/weglow/ui/screens/SavedProductsScreen.kt
?? app/src/test/java/com/example/weglow/feature/cart/
?? app/src/test/java/com/example/weglow/feature/savedproducts/
?? docs/SAVED_PRODUCTS_CART_IMPLEMENTATION.md
?? (other pre-existing untracked docs/* files, unrelated to this session)
```

Nothing was staged, committed, or pushed — all changes remain in the working
tree for review.

## Note

No additional database function or schema change was required — the
concurrency-safe add-to-cart works entirely with the existing `postgrest-kt`
client against the tables and RLS policies already created manually.
