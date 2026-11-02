# Supabase schema and Android write review — 2 November 2026

Evidence: the user's constraint descriptions and earlier pasted rows, repository
migrations, DTO serialization and repository/ViewModel call sites. The latest
screenshot is partial. No live SQL or production writes were performed.

## Compatible mappings

| Flow | Android mapping | Evidence and limits |
| --- | --- | --- |
| Authentication/profile | Auth returns the signed-in user ID; onboarding upserts `profiles.id` with it | Matches `profiles.id -> auth.users.id`. Signup does not insert a profile before an authenticated session. |
| Onboarding | `age_range`, `gender`, `is_skin_sensitive`, optional `skin_type`, `onboarding_completed` in one write | Repository check requires age, gender and sensitivity when completed; full deployed check still needs confirmation. Skipping skin type remains supported. |
| Scan saving | `Scan-profiles`, `user_prof_id = profiles.id`; `Date_time` and concern-count columns | Owner FK matches visible evidence. Full scan column types/defaults are not shown. No rename to `user_profile_id` is justified. |
| Cart | `cart_items.profile_id`, `product_no`, `quantity`; conflict target `(profile_id,product_no)` | Matches visible uniqueness and FKs. `Product.catalogNo` comes from `products."No"`; flexible display `Product.id` is not used as the FK. |
| Saved products | `saved_products.profile_id`, `product_no`; same composite conflict target | Product FK is visible. Ownership FK and composite uniqueness appeared in earlier pasted rows but must be checked in the complete current result if relying only on the partial screenshot. |

Cart and saved-product inserts omit `id`. This is compatible with a surrogate
primary key **if the deployed column has a generated/default value**. The visible
primary key does not establish that default. No primary keys were changed.

## Fixes

1. Cart add/increment writes decoded rows without requesting returned data. Added
   `select(quantity)` to the insert and compare-and-swap update. Successful writes
   are recognized; genuine zero-row conflicts can retry without treating every
   successful mutation as a failed attempt.
2. Onboarding previously serialized the full profile DTO, including unrelated
   photo, face-shape, scan and environment fields with null defaults. Replaced the
   write payload with only onboarding-owned columns and explicit conflict target
   `id`. Existing unrelated profile columns are not part of the upsert payload.

No schema migrations, row rewrites, constraint removals, or production changes
were made for this review. Existing surrogate IDs and relationships are preserved.
The migration fallback definitions use composite keys for newly created cart and
saved-product tables; `CREATE TABLE IF NOT EXISTS` does not replace existing ID
primary keys. Both shapes support the current composite conflict targets when
their corresponding uniqueness exists.

## Still unresolved

- Complete deployed columns, types, defaults, nullability and all constraints,
  including `profiles_onboarding_requires_answers` and saved-product uniqueness.
- Live RLS enabled flags, policy expressions, grants, storage and RPC permissions.
  `convalidated = true` describes the displayed constraints, not access security.
- Whether later security checks exist: absence from a partial screenshot cannot
  establish missing migrations. Do not apply the consolidated setup blindly.
- Real two-account isolation, real Auth/Storage deletion, and mail recovery tests.
- Duplicate historical migration timestamp `20260911000000`; inspect recorded
  history before any CLI migration deployment. Do not rename recorded migrations.

## Exact read-only SQL to request

Run the **entire** `supabase/tests/live-security-audit.sql` in SQL Editor and share
the single `security_audit` JSON result. It contains all seven metadata groups in
one result, including full constraint definitions and ID defaults/identity fields.
It does not select user records or mutate data. Review metadata first; use
`supabase/tests/ownership.sql` only in disposable staging after schema alignment.

## Verification

- Android production and unit-test Kotlin compilation: passed.
- 55 selected tests passed: real SDK write-contract tests (4), profile mapping,
  scan mapping/account ownership, onboarding, cart and saved-product ViewModels.
- Tests use an in-memory HTTP engine; no requests reach Supabase.
- Selected tests ran offline with `build/schema-contract-tests.init.gradle` to
  exclude the uncached JVM ONNX runtime from their test runtime only. They do not
  execute native inference. Tracked build dependencies were not changed for this.
- `git diff --check`: passed. Live database policies and integration remain unverified.
