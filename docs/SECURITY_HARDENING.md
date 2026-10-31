# Security hardening — 31 October 2026

## Implemented in this change

- Shared account-generation work ownership for chat, cart, saved products, profile, onboarding,
  recommendations, routines, and environmental readings. Cancel outstanding jobs and reject late
  responses even when a repository catches cancellation. Reset personal state on identity changes.
- Session expiry follows logout cleanup/navigation. Direct identity changes reset scan workflows.
  Offline logout clears the device session even if remote token revocation cannot complete.
- On-device scan persistence captures the account before inference. Unique profile-photo object
  names prevent rollback from deleting the previous photograph. Source image reads stop at 12 MiB;
  normalized uploads remain subject to the existing 5 MiB bucket limit.
- Passwords and recovery codes remain in transient UI state. OAuth uses explicit PKCE.
- Database migrations capture missing user tables, enforce restrictive ownership policies alongside
  any legacy permissive policies, revoke unnecessary grants, constrain cart quantities and profile
  enum values, and restrict image uploads to flat image filenames.
- Postgres-backed chat/weather/deletion quotas apply across Edge Function instances. Reservation
  and release RPCs are service-role-only; clients cannot reset counters. Quota errors fail closed.
- Chat validates JSON before provider work: 64 KiB streamed body, 4,000-character message,
  20 history entries, 8,000 characters per history entry, 32,000 total conversation characters.
  Output is limited to 1,024 tokens. Every upstream request has a deadline.
- Gemini profile context excludes full name, exact date of birth, photo reference, and location
  name. The app explains processing before chat and updates its privacy disclosure. User-entered
  messages can still contain identifying information; prompt text is not a security boundary.
- Password recovery uses a verified emailed recovery code, then updates the password, requests
  global sign-out, and clears the recovery session. UI responses do not reveal account existence.
- Account deletion verifies the caller with Supabase Auth, requires a sign-in within 15 minutes
  and explicit DELETE confirmation, removes caller-owned profile photos through the Storage API,
  then atomically deletes owned database data and the auth user through a service-only RPC.
  No client-supplied user ID/path controls deletion. The device journal is removed after success.
- Removed the unused WeatherAPI key from APK BuildConfig. Weather credentials stay on the server.

## Deployment order and prerequisites

No live database migrations, Edge Function deployments, auth-setting changes, or account deletions
were performed during implementation.

1. Take a database backup and use a disposable staging Supabase project first. Capture and inspect
   the live schema (`supabase db pull` in a separate checkout), including foreign keys, grants,
   policies, triggers, and storage ownership. `products` is externally provisioned; the new tables
   reference `products."No"`, which must already exist and be unique. Existing `Scan-profiles`
   ownership must be `user_prof_id`, matching the Android serialization. Do not rename columns
   based on older investigation documents that say `user_profile_id`.
2. Apply the existing migrations, followed by these three new migrations in order:
   - `20261031000000_user_data_security.sql`
   - `20261031010000_api_quotas.sql`
   - `20261031020000_account_deletion.sql`
   The legacy repository has duplicate migration timestamps; reconcile its migration history
   before using CLI push. Do not blindly rename migrations already recorded in production.
   `CONSOLIDATED_SETUP.sql` also includes the new migrations for SQL Editor deployments.
3. Inspect legacy age/gender/quantity values before validating the new `NOT VALID` constraints.
   New writes, including updates to existing invalid rows, are checked immediately. Remediate
   invalid legacy rows intentionally; do not invent an age for an existing user. Then run:
   ```sql
   alter table public.profiles validate constraint profiles_allowed_age_range;
   alter table public.profiles validate constraint profiles_allowed_gender;
   alter table public.cart_items validate constraint cart_quantity_bounds;
   ```
4. Confirm Edge Function runtime secrets: SUPABASE_URL, SUPABASE_ANON_KEY,
   SUPABASE_SERVICE_ROLE_KEY, GEMINI_API_KEY, and WEATHER_API_KEY. The service-role key belongs
   only in server secrets. Quota-dependent functions return 503 until the quota migration and
   server key are available. The Android app must receive only the publishable/anon key.
5. Deploy `chat`, `smart-endpoint`, and `delete-account` together with `_shared` code. Keep JWT
   verification enabled. Verify your project's signing-key compatibility with the platform's
   gateway JWT setting; every handler also validates the caller through `/auth/v1/user`.
6. In Supabase Auth → Email Templates → Reset Password, include the one-time code:
   ```html
   <h2>Reset your WeGlow password</h2>
   <p>Enter this recovery code in the WeGlow app:</p>
   <p>{{ .Token }}</p>
   <p>If you did not request this, ignore this message.</p>
   ```
   The app's recovery UI uses a code, not automatic navigation from a recovery link. Configure
   code expiry and Auth rate limits. Without this template, the stock link email does not match
   the implemented recovery UI. Test email delivery and token expiry before releasing it.
7. Keep the OAuth redirect allowlist narrow (`weglow://login-callback`). Verify Google provider
   configuration, email confirmation, password rules, authentication throttling, and provider
   billing caps. Custom-scheme callbacks remain interceptable by other installed apps; PKCE
   prevents an intercepted code from being exchanged without its verifier. A verified HTTPS
   App Link needs an owned domain and hosted assetlinks.json and is a separate deployment step.
8. Rebuild Android with the normal local Supabase/chat configuration. Do not ship an APK until
   its matching functions and migrations are deployed and the checks below pass.

## Quotas and limits

| Service | Per minute | Per UTC day | Concurrent reservations |
|---|---:|---:|---:|
| Chat | 10 | 100 | 2 |
| Weather | 30 | 500 | 4 |
| Account deletion | 2 | 10 | 1 |

Failed provider calls count toward quotas. Leases expire after 90 seconds if release fails.
Counters older than two days are pruned when the same account/service next requests service;
configure a scheduled cleanup for inactive-account counters if required. These are per-account
controls, not a global provider spending cap or signup-abuse defense. Set provider billing caps
and Supabase Auth abuse controls separately. Storage currently has per-object limits and own-folder
access controls, not an aggregate per-account storage quota.

## Verification

Local commands:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest lintDebug assembleDebug --no-configuration-cache
node --experimental-strip-types --test supabase/tests/*.test.ts
```

Node 24 can execute the dependency-free TypeScript handler/helper tests; the deployed runtime is
Deno. Tests mock external Auth, Storage, database RPC, and Gemini responses and make no real
provider calls. Kotlin tests cover session loss, late responses after account changes, old
requests after re-login to the same account, chat-history separation, recovery, deletion events,
and bounded input streams.

Run `supabase/tests/ownership.sql` as postgres against a **disposable staging database** with
migrations and a seeded catalog. Its fixture data rolls back. It checks two-account row isolation,
rejected ownership changes, enum validation, privileged-RPC denial, and quota exhaustion. It does
not prove the deployed project's schema/policies match the repository.

Also test on devices with two disposable accounts:

- Start slow profile/cart/chat requests; log out, switch accounts, and complete the old requests.
- Revoke a session while a protected screen is open. Confirm login navigation and cleared state.
- Intercept/cancel an OAuth callback; confirm no session without a valid PKCE exchange.
- Request recovery for existing/nonexistent emails; verify generic messaging, valid/expired codes,
  password policy rejection, and login using the new password. Verify old refresh sessions fail.
- Force photo-profile update failure after upload. The previous photo must remain available.
- Delete an account with scan history, cart, saved products, photos, and journal. Verify database,
  storage, and auth removal and inability to sign in. Check that another account is unchanged.
- Force Storage/RPC/network failures during deletion. Never display success before confirmation.

## Remaining operational limitations

- No live Supabase access was available to verify deployment, database ownership rules, auth
  settings, provider data retention, or email templates. Stage and verify before production.
- SDK session persistence still uses its default Android storage; hardware-backed encrypted token
  storage and OEM device-transfer behavior require a separate device-level assessment.
- Storage cleanup and database deletion are separate services. A failure can remove photographs
  while leaving the account intact; retry is safe. Legacy nested photo folders require support
  cleanup. Concurrent uploads and ambiguous network failures require disposable-account testing.
- Journal deletion affects this device only. A journal saved on another device remains in that
  device's private app data until cleared; it is not cloud synchronized.
- Python files under `backend/` are legacy local inference/chat tools and are not the Android
  production path. Their local unauthenticated-development option must never be exposed publicly;
  they do not inherit the Edge Function quota controls. This change does not certify them for
  public deployment.

Reference: [Supabase password recovery](https://supabase.com/docs/reference/kotlin/auth-resetpasswordforemail),
[Auth user management and Storage ownership](https://supabase.com/docs/guides/auth/managing-user-data).

## Local verification result

- Android unit tests: **248 passed**, 0 failures/errors.
- `lintDebug` and `assembleDebug`: passed. Existing lint warnings remain; this is not a claim
  that the entire project has no warnings.
- Five Node backend test groups: passed.
- Strict TypeScript compilation of all three Edge Functions and shared imports: passed, using
  a minimal Deno declaration for its environment and server entry point.
- PostgreSQL integration checks: passed in PGlite with a minimal Supabase infrastructure fixture.
  All migrations apply, new migrations are repeatable, and ownership, privileged RPC denial,
  minute quotas, storage preconditions, and atomic database deletion checks pass.
- `git diff --check`: passed.

For the isolated database runner, install `@electric-sql/pglite` in a temporary directory and
set `WEGLOW_PGLITE_MODULE` to its `dist/index.js` file URL, then run:

```sh
node supabase/tests/database.mjs
```

The fixture is not the live Supabase schema. It does not exercise real Auth session tables,
Storage API behavior, email delivery, device permission prompts, OEM backup behavior, or
provider billing settings. Those checks remain part of staging/device verification.
