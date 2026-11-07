# Delete Account — Secure Architecture Investigation

Investigation-only report for a future Delete Account feature, reached via
Profile → Account Settings → Delete Account. Nothing was modified, no
migrations were run, nothing was changed in Supabase, and nothing was
staged, committed, or pushed. The completed 14+ minimum-age change was not
touched.

## 1. Current Authentication Architecture

- **`AuthRepository`** (domain interface): `signUp`, `signIn`,
  `signInWithGoogle`, `signOut`, `currentUserId()`, `hasActiveSession()`,
  `currentUserDisplayName()`, `currentUserEmail()`. **No delete-account
  method exists.**
- **`SupabaseAuthRepository`**: thin wrapper around `client.auth`
  (Supabase Auth, using only the **publishable/anon key** — confirmed via
  `SupabaseClientProvider`, which only ever configures
  `AppConfig.supabasePublishableKey`). No admin client, no service-role
  key anywhere in this class or anywhere else in `app/src/main`.
- **`AppContainer`**: builds one `by lazy` `supabaseClient` and wires
  every repository (`authRepository`, `profileRepository`,
  `profileImageRepository`, `scanProfileRepository`,
  `savedProductRepository`, `cartRepository`, `catalogRepository`,
  `hairstyleRepository`, `environmentRepository`) from that same client.
  There is exactly one Supabase client instance app-wide, and it is never
  given elevated credentials.
- **`ProfileViewModel`**: loads `displayName`, `email`,
  `profileImagePath`/`profileImage`, `gender` via
  `authRepository`/`profileRepository`/`profileImageRepository`. No
  delete-account state exists yet.
- **`AccountSettingsScreen`**: currently shows a "Profile" card with
  read-only Name/Email rows and an editable Gender row (via the existing
  `onGenderSelected` → `ProfileRepository.updateGender` path). No
  destructive-action section exists yet.
- **Current-user/session handling**: `AuthRepository.currentUserId()` =
  `client.auth.currentUserOrNull()?.id`; `authenticationState` is a
  `Flow<AuthenticationState>` mapped from `client.auth.sessionStatus`.
  Session persistence itself is handled internally by the Supabase Auth
  SDK (not app code).
- **`signOut` behavior** (directly reusable — see §F):
  `AuthViewModel.signOut()` calls `authRepository.signOut()`, and on
  success emits `AuthEvent.SignedOut`. A single, app-lifetime
  `LaunchedEffect(authState.event)` in `WeGlowApp.kt` reacts to that event
  by clearing `scanViewModel`, `hairstyleViewModel`, `scanFlowViewModel`,
  `savedProductsViewModel`, `cartViewModel`, and navigating to
  `Destination.Login` with `popUpTo(graph.id){inclusive=true}` — a full,
  already-centralized "wipe local state and return to Login" pathway that
  any future "account deleted" event can reuse verbatim.

## 2. Current Supabase User-Data Relationships

| Table/bucket | Relationship to `auth.users.id` | Cascade confirmed? |
|---|---|---|
| `public.profiles` | `id uuid primary key references auth.users(id) on delete cascade` | **Confirmed** — `supabase/migrations/20260909000000_create_profiles.sql`, line: `id uuid primary key references auth.users (id) on delete cascade` |
| `public.Scan-profiles` | Written by `SupabaseScanProfileRepository` with a `user_profile_id` column; **no migration file exists in this repository for this table's creation** | **Not confirmed from repo.** Must be checked live (§K/§L) |
| `public.saved_products` | `profile_id uuid not null references public.profiles(id) on delete cascade`, per the schema described when that table was manually created | **Stated as cascade at creation time, but no migration file in this repo defines it** — cannot be independently verified from repository artifacts alone; treat as unconfirmed until checked live |
| `public.cart_items` | Same pattern: `profile_id uuid not null references public.profiles(id) on delete cascade`, per the schema described | Same caveat as `saved_products` — must be verified live |
| `profile-images` Storage bucket | Objects live at path `<userId>/<timestamp>.<ext>` (`SupabaseProfileImageRepository`) — a **storage object path convention only**, not a database foreign key | **Never cascades.** Supabase Storage objects are a separate subsystem from Postgres FKs; deleting a Postgres row never deletes a Storage object unless an explicit trigger or application step does it. No such trigger exists in this repo's migrations. |

None of the "stated as cascade" rows are assumed to actually behave that
way in the live database — see §K.

## 3. Profile Images

- Stored path: `profiles.profile_image_url` holds a Storage **object
  path** (e.g. `<userId>/1699999999999.jpg`), never a signed URL (per the
  doc comment on `UserProfile.profileImagePath`).
- Deletion mechanism that already exists:
  `ProfileImageRepository.deleteProfileImage(path)` →
  `SupabaseProfileImageRepository.deleteProfileImage` →
  `client.storage.from("profile-images").delete(path)`. This is exactly
  the primitive account deletion needs to reuse — read
  `profiles.profile_image_url` for the user **before** deleting their
  `profiles` row, then call this same delete.
- Confirmed: nothing in this codebase or its migrations deletes a Storage
  object automatically when a `profiles` row is deleted. This must be an
  explicit step in the deletion flow, done **before** the row (and the
  path it holds) is gone.

## 4. Scan-Profiles

**Cannot be confirmed from this repository.** No migration file creates
`Scan-profiles`, so its column definitions, foreign keys, and `ON DELETE`
behavior cannot be inspected from anything in this repo. This must be
checked directly against the live Supabase schema before implementation —
see the read-only query in §L. Until confirmed, the safe assumption for
design purposes is that it does **not** cascade, and the deletion flow
should explicitly delete the user's `Scan-profiles` rows itself (a
redundant explicit delete is harmless even if a cascade turns out to
already exist).

## 5. Supabase Auth User Deletion — Architecture

- Supabase's Auth Admin API exposes `DELETE /auth/v1/admin/users/{id}`
  (equivalently `supabase.auth.admin.deleteUser(id)` if using the
  `supabase-js` SDK server-side). This call **requires the service-role
  key** — it is not available to an anon/publishable-key client, and
  correctly so.
- **The existing Edge Function architecture directly supports adding
  this.** `supabase/functions/smart-endpoint/index.ts` already establishes
  the exact right shape: `Deno.serve`, a bearer-token check against
  `${SUPABASE_URL}/auth/v1/user` using the caller's own
  `Authorization`/`apikey` headers, and secrets pulled via
  `Deno.env.get(...)` (currently `WEATHER_API_KEY`) rather than ever being
  shipped to Android. A `delete-account` function would follow the
  identical pattern, with one necessary extension: `smart-endpoint`'s
  current `requireSupabaseUser` only checks `response.ok` and **discards
  the response body** — a delete function must instead **parse that JSON
  body's `id` field** to know which account to delete (it must never
  accept a user id from the request itself).
- This function was **not implemented** — only confirmed the existing
  architecture supports it, and the one gap (id extraction) that needs to
  be added.

## 6. Security Requirements — How the Investigated Architecture Satisfies Them

| Requirement | How it's satisfied |
|---|---|
| Require a valid authenticated Supabase user | Reuse `smart-endpoint`'s bearer-token verification pattern (round-trip to `/auth/v1/user`) |
| Derive user ID from the verified session, never trust Android | Extract `id` from that same verified `/auth/v1/user` response body — never from a request parameter |
| Never expose service-role/admin credentials to Android | Service-role key lives only as an Edge Function secret (`Deno.env.get`), exactly like `WEATHER_API_KEY` today — confirmed no such key exists anywhere in `app/src/main` currently, and this pattern keeps it that way |
| Only delete the caller's own account | Enforced structurally — the function only ever knows the id it extracted from the caller's own verified token, never an id supplied by the client |
| Clean up user-owned data/storage | Explicit server-side cleanup steps in the function (see §F), rather than relying on unconfirmed cascades |
| Fail safely | Standard Edge-Function pattern already used (`try/catch`, explicit status codes, generic error messages that never leak internals — matches `smart-endpoint`'s existing style) |
| Sign the Android client out after success | `AuthRepository.signOut()`/`client.auth.signOut()` already exists — call it locally after a successful server response, reusing the exact `AuthEvent.SignedOut` pathway described in §1 |

## 7. UX Recommendation for AccountSettingsScreen (not implemented)

Smallest, existing-design-compatible addition:
- A new section below the existing "Profile" card — styled the same way
  (`Text` heading + `CardWhite`-clipped `Column`) — containing a single
  destructive row: **"Delete Account"**, styled in `LogoutRed` (the same
  color already used for "Log Out" in `ProfileScreen.kt`), consistent with
  how this app already marks its one existing destructive action.
- Tapping it opens a Material3 `AlertDialog` — a pattern already used in
  this app (`PriceFilterDialog` in `DiscoverScreen.kt`, the notifications
  dialog in `HomeScreen.kt`) — with explicit, permanent-deletion wording
  (e.g. *"This will permanently delete your account and all your data.
  This cannot be undone."*), a **Cancel** button, and a clearly
  differentiated **Delete Account** confirm button (red/error-colored,
  distinct from the dialog's default action color).
- For "deliberate confirmation," the simplest sufficient option is the
  two-button `AlertDialog` itself (an explicit second tap is already
  meaningful friction, matching how most production apps gate this). A
  stronger option — a text field requiring the user to type a
  confirmation phrase before the button enables — is available if more
  friction is wanted, but starting with the simpler dialog is recommended
  unless there's a specific reason to add typed confirmation.
- Loading/error states should follow the exact same pattern already
  established for gender updates in this screen
  (`isUpdatingGender`/`genderError` → `CircularProgressIndicator` swap +
  dismissible error text) — i.e. an `isDeletingAccount`/
  `deleteAccountError` pair on `ProfileUiState`, not a new state-handling
  convention.

## 8. Re-Authentication

- There is no "recent authentication" concept currently built into this
  app's auth architecture (no timestamp tracking of last sign-in, no
  Supabase MFA/step-up flow in use).
- **Email/password accounts**: `AuthRepository.signIn(email, password)`
  already exists and could realistically be reused as an explicit
  "confirm your password" step immediately before requesting deletion, if
  extra friction is wanted. This is not a new system — it's the exact
  same call already used for normal login.
- **Google OAuth accounts**: there is no password to re-enter. The only
  equivalent is re-triggering `signInWithGoogle()` (also already
  supported), which is a full browser round-trip — meaningfully more
  disruptive UX for comparatively little extra assurance, given the
  account already requires a currently-valid session to reach this screen
  at all.
- **Recommendation**: don't invent a new re-auth system. A
  currently-valid Supabase session (which the Edge Function independently
  re-verifies server-side, per §5) is a reasonable and realistic bar for
  this app's risk profile, combined with the explicit confirmation dialog
  in §7. If the product wants stronger assurance specifically for
  email/password accounts, reusing the existing `signIn` call as an
  optional step is architecturally free; there's no clean equivalent for
  Google accounts without materially worse UX for uncertain benefit.

## 9. Exact Live-Supabase Checks Required Before Implementation

1. **`Scan-profiles` schema and FK behavior** — does it reference
   `profiles(id)` or `auth.users(id)` directly, and with what `ON DELETE`
   action (if any)?
2. **`saved_products` and `cart_items` FK behavior** — confirm the `ON
   DELETE CASCADE` described earlier actually exists in the live database
   (no migration file in this repo defines it, so it cannot be verified
   from the repository).
3. **`profiles` table's RLS policies** — confirmed from
   `20260909000000_create_profiles.sql`: only SELECT/INSERT/UPDATE
   policies exist, **no DELETE policy**. This means the Android app
   cannot delete a `profiles` row directly under RLS even if it tried —
   reinforcing that account deletion must go through a privileged
   (service-role) path, not a client-side RLS-permitted delete. Worth
   re-confirming live that no DELETE policy was added out-of-band since
   this migration.
4. **Whether Supabase project settings enable "soft delete" or retain
   auth user data** (e.g. Supabase's `auth.users` supports a soft-delete
   option in some configurations) — this is a project-dashboard setting,
   not visible in this repo at all.
5. **Whether any other Supabase-side objects reference `auth.users.id`
   or `profiles.id`** that aren't visible from this Android repository
   (e.g., something added directly in the Supabase dashboard, matching
   how `products`, `hairstyles`, and `Scan-profiles` were all provisioned
   outside of this repo's migrations).

## 10. Read-Only SQL Queries for Those Checks

Run these directly in the Supabase SQL editor (or `psql`) against the live
project. All are read-only — no `CREATE`/`ALTER`/`DROP`/`DELETE`.

```sql
-- 1. Every foreign key in the public schema that ultimately chains back to
--    auth.users or profiles, and its ON DELETE action, in one pass.
select
    tc.table_schema,
    tc.table_name,
    kcu.column_name,
    ccu.table_schema  as foreign_table_schema,
    ccu.table_name    as foreign_table_name,
    ccu.column_name   as foreign_column_name,
    rc.delete_rule
from information_schema.table_constraints tc
join information_schema.key_column_usage kcu
    on tc.constraint_name = kcu.constraint_name
    and tc.table_schema = kcu.table_schema
join information_schema.constraint_column_usage ccu
    on tc.constraint_name = ccu.constraint_name
    and tc.table_schema = ccu.table_schema
join information_schema.referential_constraints rc
    on tc.constraint_name = rc.constraint_name
    and tc.table_schema = rc.constraint_schema
where tc.constraint_type = 'FOREIGN KEY'
  and tc.table_schema = 'public'
order by tc.table_name;

-- 2. Full column list for Scan-profiles specifically (name/type/nullability).
select column_name, data_type, is_nullable
from information_schema.columns
where table_schema = 'public'
  and table_name = 'Scan-profiles'
order by ordinal_position;

-- 3. All RLS policies currently defined on the tables account deletion touches.
select schemaname, tablename, policyname, cmd, roles, qual, with_check
from pg_policies
where schemaname = 'public'
  and tablename in ('profiles', 'Scan-profiles', 'saved_products', 'cart_items')
order by tablename, cmd;

-- 4. Confirm RLS is actually enabled (not just policies present) on each table.
select relname, relrowsecurity, relforcerowsecurity
from pg_class
where relname in ('profiles', 'Scan-profiles', 'saved_products', 'cart_items');

-- 5. Any storage.objects RLS policies relevant to the profile-images bucket,
--    to confirm the existing per-user path scoping this Android code already
--    relies on for upload/download/delete.
select policyname, cmd, roles, qual, with_check
from pg_policies
where schemaname = 'storage'
  and tablename = 'objects';
```

## 11. Risks / Failure Cases

- **Unconfirmed cascades create a real risk of orphaned rows**
  (`Scan-profiles`, and possibly `saved_products`/`cart_items` if the live
  schema doesn't actually match what was described) if the deletion flow
  relies on cascade alone instead of explicit server-side cleanup.
- **Storage objects will never be cleaned up automatically** — if the
  Edge Function deletes the auth user without first reading and deleting
  the `profile-images` object, that object becomes permanently orphaned
  (private bucket, but still occupying storage indefinitely and
  technically still containing that user's photo).
- **No `profiles` DELETE RLS policy exists**, so any implementation must
  go through an elevated (service-role) path — a naive attempt to delete
  via the normal Postgrest client with the anon key would silently affect
  0 rows (RLS denies it) or error, never actually removing the row.
- **Partial-failure risk**: if the Edge Function deletes some rows/storage
  but then the `auth.users` deletion call itself fails, the account would
  be "damaged but still able to log in" — the function should be written
  so a failure partway through reports a clear error rather than silently
  leaving a half-deleted account (the exact recovery strategy is an
  implementation decision, not resolved in this investigation).
- **Local session desync**: if the Edge Function succeeds but the Android
  client fails to call local `signOut()` afterward (e.g., network drop
  right after the success response), the app could continue believing the
  user is signed in against an account that no longer exists server-side,
  likely surfacing as confusing errors on the next action rather than a
  clean "signed out" state.

## 12. Recommended Implementation Sequence (future task — not started here)

1. Run the read-only queries in §10 against the live database; resolve
   every "unconfirmed" item in §2/§9.
2. Design and get sign-off on the exact Edge Function contract (request
   shape — likely no body needed at all, since the id comes from the
   verified token; response shape; error codes).
3. Implement the Edge Function: verify token → extract user id → read
   `profiles.profile_image_url` → delete the storage object (if present)
   → explicitly delete `Scan-profiles` (and, depending on §10 results,
   `saved_products`/`cart_items`) rows for that user → call the Auth
   Admin delete-user endpoint with the service-role key → return
   success/failure.
4. Add `AuthRepository.deleteAccount(): Result<Unit>` (interface) and its
   `SupabaseAuthRepository` implementation (calls the Edge Function with
   the current session's token, then `client.auth.signOut()` locally on
   success).
5. Add `AuthViewModel.deleteAccount()`, reusing the existing
   `AuthEvent.SignedOut` transition on success so `WeGlowApp.kt`'s
   already-centralized cleanup/navigation logic (§1) needs no changes.
6. Add the Delete Account UI section + confirmation dialog to
   `AccountSettingsScreen` (§7), with loading/error state on
   `ProfileUiState` following the existing gender-update convention.
7. Test against a disposable/test Supabase account before touching
   production data, verifying storage cleanup, row cleanup across all
   four tables, and that the account is genuinely unable to sign in
   afterward.

---

Investigation complete. Nothing was modified, migrated, or pushed.
