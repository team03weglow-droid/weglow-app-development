# Profile Placeholders — Account Settings, Cart, Saved Products, Order History

Investigation and implementation report for converting Profile's three
placeholder actions ("Order History · Coming soon", "Saved Products · Coming
soon", "Account Settings · Coming soon") into real functionality, and for
adding cart support where appropriate.

Existing architecture was preserved throughout:

```
Compose UI
    ↓
ViewModel
    ↓
Domain / Repository abstraction
    ↓
Data repository implementation
    ↓
Supabase
```

No new NavHost, no duplicate Product/user-identity model, nothing committed
or pushed — all changes are left in the working tree for review.

## Phase 1 — Investigation findings

### 1. Cart infrastructure
None. No `cart`/`cart_items` table in any migration, no `CartRepository` /
`CartViewModel`, no cart route. Discover's "Add to Bag" is a disabled
`WeGlowPlannedFeature` placeholder — not wired to anything.

### 2. Saved-products infrastructure
None. No table, repository, or ViewModel. Discover's per-card "Save" is also
a disabled `WeGlowPlannedFeature` placeholder.

### 3. Order infrastructure
None. No `orders` / `order_items` table, no checkout/payment SDK anywhere in
the project, no order-creation flow of any kind (real or fake).

### 4. Account/profile infrastructure
Solid and reusable. `profiles` table (`id uuid` FK to `auth.users`, RLS
scoped to `auth.uid() = id`) holds `full_name`, `gender`, `skin_type`,
`age_range`, `is_skin_sensitive`, `face_shape`, `profile_image_url`.
`ProfileRepository` / `SupabaseProfileRepository` already has targeted
single-column updates (`updateProfileImagePath`, `updateFaceShape`, and the
gender editing added in an earlier session, `updateGender`). `AuthRepository`
exposed `currentUserId()` / `currentUserDisplayName()` but had no email
accessor.

### Identity model confirmed
`profiles.id == auth.users.id == auth.uid()`, surfaced via
`AuthRepository.currentUserId()`. This is the one identity mechanism in the
app, and it is what any new table's ownership column must use.

### Products table — important caveat
`public.products` exists and is readable (RLS: `SELECT` only, authenticated),
but it was **populated externally**, not created by a migration in this
repo. `SupabaseCatalogRepository.toProductOrNull()` resolves the product id
from *any of* `product_id`, `id`, `product_code`, `sku`, `uniq_id`,
`unique_id` — meaning this codebase does not actually know, with certainty,
the real primary-key column name or type of the live `products` table. This
directly affects how a `product_id` foreign key in new tables should be
declared (see Open question #1 below).

### Reusable UI already in the project
`WeGlowProductImage`, `ProductGridCard` / `ProductListCard` (Discover),
`SettingsRow` (Profile), `WeGlowCard` / `WeGlowEmptyState` /
`WeGlowErrorView` / `WeGlowLoadingView` (design system) — all suitable to
reuse once the schema below is approved.

## Implemented now — Feature 4: Account Settings (no new schema needed)

Account Settings required no new Supabase tables, so it was implemented in
full:

- Added `AuthRepository.currentUserEmail()` and the `SupabaseAuthRepository`
  implementation (read-only, mirrors the existing `currentUserDisplayName()`
  pattern — no new identity mechanism introduced).
- Added `email` to `ProfileUiState`, populated in `ProfileViewModel.refresh()`.
- New `AccountSettingsScreen.kt`: shows Name and Email read-only (no update
  mechanism exists for either in the current auth architecture, so none was
  invented), and **moved** Gender editing here from Profile's "Personal
  details" section — reusing the exact same `onGenderSelected` →
  `ProfileRepository.updateGender` → `profiles.gender` path, not duplicated.
- `Destination.AccountSettings` route added; wired into `WeGlowApp.kt`'s
  existing `NavHost` (no second `NavHost` created).
- `ProfileScreen.kt`: "Account Settings" is now a real, clickable
  `SettingsRow` inside the existing "Quick links" card, navigating to the
  new screen. "Order History" and "Saved Products" are **left exactly as
  they were** (`· Coming soon`, non-clickable) — see the stop condition
  below for why.

## CRITICAL STOP CONDITION — Saved Products, Cart, Order History

No Supabase tables, RLS policies, or Android repositories/ViewModels exist
for any of these three. Per the task's instructions, implementation stopped
before writing the database-dependent Android code (SavedProducts / Cart /
OrderHistory screens, repositories, ViewModels, routes) and before touching
Discover's Save / Add-to-Bag placeholders, so nothing misleading gets wired
up ahead of real data.

### Proposed schema (draft — needs review, especially item 1)

**Open question #1 (blocking).** The real primary-key column/type of
`products` is not known with certainty (see the caveat above). The proposal
below uses `product_id text not null` with **no FK constraint** to
`products` — it can't be declared safely without confirming the real PK —
enforced only at the application layer using `Product.id`. If the actual
column can be confirmed (e.g. via `supabase db pull` against the hosted
project), a real FK should be added.

**Open question #2 (blocking, Orders only).** There is no real
checkout/payment flow anywhere in this app. Order *creation* has
deliberately not been designed — only what an honest read-only Order History
would query, assuming `orders` / `order_items` existed. Before any write
path is added, a decision is needed: (a) hold off entirely on order creation
until real checkout/payment exists, or (b) allow a clearly-labeled non-paid
`pending` order state created from the cart as a deliberate, honest interim
step. This should not be decided silently.

```sql
-- 1. saved_products — one row per user-saved product
create table public.saved_products (
    id uuid primary key default gen_random_uuid(),
    profile_id uuid not null references public.profiles(id) on delete cascade,
    product_id text not null,             -- Product.id; see open question #1
    created_at timestamptz not null default now(),
    unique (profile_id, product_id)       -- saving twice is a no-op, not a duplicate row
);
create index saved_products_profile_id_idx on public.saved_products(profile_id);
alter table public.saved_products enable row level security;
-- RLS: select/insert/delete only where profile_id = auth.uid(); revoke all from anon.

-- 2. cart_items — current cart, one row per (user, product)
create table public.cart_items (
    id uuid primary key default gen_random_uuid(),
    profile_id uuid not null references public.profiles(id) on delete cascade,
    product_id text not null,
    quantity integer not null default 1 check (quantity > 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),  -- trigger, same pattern as profiles
    unique (profile_id, product_id)       -- adding again increments quantity, no duplicate rows
);
create index cart_items_profile_id_idx on public.cart_items(profile_id);
alter table public.cart_items enable row level security;
-- RLS: select/insert/update/delete only where profile_id = auth.uid(); revoke all from anon.

-- 3. orders — see open question #2 before any INSERT policy/order-creation code is written
create table public.orders (
    id uuid primary key default gen_random_uuid(),
    profile_id uuid not null references public.profiles(id) on delete cascade,
    status text not null default 'pending',   -- exact allowed values depend on question #2
    total_lkr numeric(12,2) not null default 0 check (total_lkr >= 0),
    created_at timestamptz not null default now()
);
create index orders_profile_id_idx on public.orders(profile_id);
alter table public.orders enable row level security;
-- RLS: select only where profile_id = auth.uid(); insert policy depends on question #2; revoke all from anon.

-- 4. order_items — snapshot of price/quantity at order time (never re-derived later)
create table public.order_items (
    id uuid primary key default gen_random_uuid(),
    order_id uuid not null references public.orders(id) on delete cascade,
    product_id text not null,
    quantity integer not null check (quantity > 0),
    unit_price_lkr numeric(12,2) not null check (unit_price_lkr >= 0)
);
create index order_items_order_id_idx on public.order_items(order_id);
alter table public.order_items enable row level security;
-- RLS: select only via EXISTS(select 1 from orders o where o.id = order_id and o.profile_id = auth.uid()).
```

**Why each table is needed:** `saved_products` and `cart_items` map 1:1 to
Features 1/2 and need per-user uniqueness so re-saving/re-adding a product
never creates duplicate rows. `orders` / `order_items` are split into a
header and line items so a line item's price is a permanent snapshot, never
silently drifting if the catalog price changes later — this matches
"calculate subtotal from actual product prices" for the live cart versus an
order's frozen historical total.

**RLS summary.** Every table is owned per-user through `profile_id = auth.uid()`
(directly, or — for `order_items` — indirectly via its parent `orders` row),
matching the exact pattern the existing `profiles` migration already uses.
No `anon` access anywhere; no service-role key is used from the Android app.

### Android files that would follow, after approval

- `domain/model/{SavedProduct,CartItem,Order,OrderItem}.kt`
- `domain/repository/{SavedProductRepository,CartRepository,OrderRepository}.kt`
- Matching `Supabase*Repository` implementations
- `feature/{savedproducts,cart,orders}/*ViewModel.kt`
- `ui/screens/{SavedProducts,Cart,OrderHistory}Screen.kt`
- New `Destination` routes
- Discover's Save / Add-to-Bag wired to the real ViewModels
- Unit tests per the existing fake-repository convention

## Files changed this session

**Production:**
- `app/src/main/java/com/example/weglow/domain/repository/AuthRepository.kt`
- `app/src/main/java/com/example/weglow/data/repository/SupabaseAuthRepository.kt`
- `app/src/main/java/com/example/weglow/feature/profile/ProfileViewModel.kt`
- `app/src/main/java/com/example/weglow/navigation/Destination.kt`
- `app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt`
- `app/src/main/java/com/example/weglow/ui/screens/ProfileScreen.kt`
- `app/src/main/java/com/example/weglow/ui/screens/AccountSettingsScreen.kt` (new)

**Tests:**
- `app/src/test/java/com/example/weglow/feature/profile/ProfileViewModelTest.kt` (+1 new test: email loading)
- `app/src/test/java/com/example/weglow/feature/auth/AuthViewModelTest.kt`
- `app/src/test/java/com/example/weglow/feature/onboarding/OnboardingViewModelTest.kt`
- `app/src/test/java/com/example/weglow/feature/recommendation/RecommendationViewModelTest.kt`
- `app/src/test/java/com/example/weglow/feature/routine/RoutineViewModelTest.kt`

(The last four were updated only to add `currentUserEmail()` to their fake
`AuthRepository` implementations so the interface change compiles.)

## Gradle results

```
./gradlew architectureCheck testDebugUnitTest lintDebug assembleDebug
```

All four: **BUILD SUCCESSFUL**.

## `git diff --stat`

```
 .../data/repository/SupabaseAuthRepository.kt      |   3 +
 .../weglow/domain/repository/AuthRepository.kt     |   8 +
 .../weglow/feature/profile/ProfileViewModel.kt     |   5 +
 .../com/example/weglow/navigation/Destination.kt   |   1 +
 .../com/example/weglow/navigation/WeGlowApp.kt     |  22 ++-
 .../com/example/weglow/ui/screens/ProfileScreen.kt | 134 ++--------------
 .../weglow/feature/auth/AuthViewModelTest.kt       |   1 +
 .../feature/onboarding/OnboardingViewModelTest.kt  |   2 +
 .../weglow/feature/profile/ProfileViewModelTest.kt |  18 +++
 .../recommendation/RecommendationViewModelTest.kt  |   1 +
 .../weglow/feature/routine/RoutineViewModelTest.kt |   1 +
 docs/ARCHITECTURE_REVIEW.md                        | 170 ++++++++++-----------
 12 files changed, 151 insertions(+), 215 deletions(-)
```

> `docs/ARCHITECTURE_REVIEW.md`'s modification predates this session and was
> not touched as part of this work.

## `git status --short`

```
 M app/src/main/java/com/example/weglow/data/repository/SupabaseAuthRepository.kt
 M app/src/main/java/com/example/weglow/domain/repository/AuthRepository.kt
 M app/src/main/java/com/example/weglow/feature/profile/ProfileViewModel.kt
 M app/src/main/java/com/example/weglow/navigation/Destination.kt
 M app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt
 M app/src/main/java/com/example/weglow/ui/screens/ProfileScreen.kt
 M app/src/test/java/com/example/weglow/feature/auth/AuthViewModelTest.kt
 M app/src/test/java/com/example/weglow/feature/onboarding/OnboardingViewModelTest.kt
 M app/src/test/java/com/example/weglow/feature/profile/ProfileViewModelTest.kt
 M app/src/test/java/com/example/weglow/feature/recommendation/RecommendationViewModelTest.kt
 M app/src/test/java/com/example/weglow/feature/routine/RoutineViewModelTest.kt
 M docs/ARCHITECTURE_REVIEW.md
?? app/src/main/java/com/example/weglow/ui/screens/AccountSettingsScreen.kt
?? docs/PROFILE_ACCOUNT_SETTINGS_CART_SAVEDPRODUCTS_ORDERS_INVESTIGATION.md
?? (other pre-existing untracked docs/* files, unrelated to this session)
```

Nothing was staged, committed, or pushed — all changes remain in the working
tree for review.

## Waiting on

1. Confirmation of `products`' real primary-key column/type, so a real
   foreign key can be added to `saved_products.product_id`,
   `cart_items.product_id`, and `order_items.product_id`.
2. A decision on whether/how order creation should work given there is no
   real checkout/payment flow yet (hold off entirely, or a clearly-labeled
   non-paid `pending` order state).

Once both are answered, the schema/migration portion can be applied and the
Saved Products, Cart, and Order History Android code implemented against it.
