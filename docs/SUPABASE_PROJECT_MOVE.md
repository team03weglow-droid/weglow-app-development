# Move WeGlow to the new Supabase account

Status: the owner completed fresh SQL setup, hairstyle import and Google/SMTP/
recovery-template configuration on the new project. Local development/production
settings now use its publishable key; the rebuilt Dev APK is installed on the
emulator. Agent-side hosted checks so far are read-only Auth/access probes.
Authenticated app acceptance tests, function deployment and final release
cutover/signing remain pending. No old users or personal data were migrated.

Selected destination: `nkrxygqtptvarntzxawk`. The owner selected a fresh start
with the existing real **hairstyle catalog only**, and no product records.
Use `supabase/fresh-project/README.md` and its generated `setup.sql` for this path.
The setup now creates empty app-compatible catalog tables; the older alternatives
below describe other migration paths and are not the selected import procedure.

## Decisions to record before importing

| Item | Status |
| --- | --- |
| Existing users and personal data: preserve or start fresh | Owner selected fresh start; no old users or personal records |
| New project URL/reference | `nkrxygqtptvarntzxawk`; https://nkrxygqtptvarntzxawk.supabase.co |
| Old project ownership/admin access | Confirm with owner |
| Real product and hairstyle catalog export | Owner selected hairstyles only; no product records |
| Storage objects and all image locations | Inventory required |
| Google OAuth project/client ownership | Confirm with owner |
| Email sender/SMTP account | Confirm with owner |
| Maintenance window if preserving data | Choose before final export |

A Supabase account, organization, and project are distinct. If only account/
organization ownership needs to change, an existing project can be transferred
between organizations subject to the platform's access requirements. If the
owner wants a new project/reference, use the project migration path below.
[Supabase project transfer](https://supabase.com/docs/guides/platform/project-transfer).

## What WeGlow needs

| Area | App dependency | Migration work |
| --- | --- | --- |
| Auth | Email/password and Google; PKCE | Preserve Auth users/identities and UUIDs if requested; reconfigure providers, redirects and emails |
| Catalogs | `products`, `hairstyles` | Selected path: empty product compatibility table, real hairstyle app-facing fields only |
| Personal data | `profiles`, `Scan-profiles`, `saved_products`, `cart_items` | Copy if preserving; preserve ownership FKs, policies, grants, indexes and triggers |
| Progress | `routine_schedules`, `routine_task_completions`, scan metadata | Copy if already installed; otherwise apply `20261104000000_combined_progress.sql` on the destination |
| Storage | Private `profile-images` bucket | Copy actual files and bucket configuration/policies; preserve owner UUID/path relationships |
| Weather | `smart-endpoint`, quota RPCs, `private.api_requests` | Deploy handler/shared helpers; configure destination keys and `WEATHER_API_KEY`; quota leases need no historical continuity |
| Account deletion | `delete-account`, `delete_account_data(uuid)` | Deploy handler and RPC; verify Storage cleanup and cascades with disposable users |
| Retired chat | `chat` retirement handler | Retain HTTP 410 stub if supporting older clients; no Gemini integration is needed for current Android |
| Android | `WEGLOW_SUPABASE_URL`, `WEGLOW_SUPABASE_PUBLISHABLE_KEY` | Change local build configuration only at cutover; rebuild and verify login |
| Device-local data | Daily notes and legacy completion keys | Not part of a database export; stored on each device and keyed by Auth UUID |

The legacy catalog schema is externally provisioned and absent from repository
migrations. The selected fresh setup creates explicit app-compatible columns,
not an exact clone of unused legacy metadata. `supabase/staging/setup.sql` has
synthetic test catalogs. `CONSOLIDATED_SETUP.sql` is not a complete new-project restore
and does not include the new progress migration. Legacy migration filenames
contain duplicate timestamps; reconcile history before using automatic db push.

## 1. Inventory and retain a backup

Run `supabase/migration/project-inventory.sql` in the **old project's SQL Editor**.
It is read-only and returns table/column/security metadata, bucket configuration,
and aggregate counts. It omits user records, passwords, tokens, object paths and
function bodies. Share the result for schema planning; retain full backups
locally outside Git. Record all image hosts/buckets, including catalog image URLs
that may still point at the old project. Confirm whether other services use this
backend before changing anything.

The source metadata, not an assumed schema, determines the exact restore bundle.
Keep the old project operational until destination validation and app cutover.

## 2. Build the destination using the selected path

**Preserve users/data:** use a supported Supabase backup/restore procedure with
Auth included. User UUIDs must survive because profiles and private photo paths
depend on them. Supabase supports migration of Auth users and password hashes;
changing project token-signing configuration can require users to sign in again.
Do not recreate the same emails as new users and assume their UUIDs will match.
[Auth migration](https://supabase.com/kb/troubleshooting/migrating-auth-users-between-projects).

**Start fresh:** install a complete verified application schema and real catalog
data. Omit old Auth users, profiles, scans, checkmarks, carts and saved products.
Create disposable users for verification, then normal users through the app's
signup/onboarding flow. Reuse the current application schema unless the owner
separately specifies structural changes; moving projects does not require a
database redesign.

Database restore does not by itself complete a project move: actual Storage
files, Auth configuration, Edge Functions and runtime secrets need separate work.
Select backup/restore tools after confirming available project backups and local
tooling. On this machine Node is available; Supabase CLI, PostgreSQL tools and
Docker were not found on PATH during preparation.
[Official restore procedure](https://supabase.com/docs/guides/platform/migrating-within-supabase/backup-restore).

## 3. Configure authentication and services

- Enable the intended Email/password and Google providers on the destination.
- Configure the app's exact redirect: `weglow://login-callback`. Register the new
  project's Google OAuth callback URL in the Google OAuth client configuration.
- Set email confirmation, password rules, OTP expiry/rate limits and production
  email delivery. The Reset Password template must contain `{{ .Token }}` because
  WeGlow accepts an emailed recovery code.
- Keep the `profile-images` bucket private, with a 5 MiB limit and JPEG/PNG/WebP
  allowlist. Verify two-account Storage isolation through the API, not only SQL.
- Deploy `smart-endpoint`, `delete-account`, and the required chat stub with
  `_shared` helpers. Check gateway JWT verification against the new project's
  signing configuration; retain handler-side authenticated-user verification.
- Verify built-in Supabase function environment values refer to the destination;
  set `WEATHER_API_KEY` there. Never put a service-role/secret key in Android or Git.
- Preserve/remap catalog image references deliberately. A database row pointing
  at an old bucket does not move the underlying image.

## 4. Verify before switching Android

- Compare source/destination schemas, exact row counts, keys, policies/grants,
  constraints and Storage object counts as appropriate to the selected path.
- Test fresh signup, confirmation, email login, Google login, onboarding, password
  recovery and logout on the destination.
- Test catalog/image loading, scan save/retry, routine check/uncheck, progress,
  saved products, cart and weather with an Android build pointing there.
- Use two disposable accounts to verify database and Storage ownership. Verify
  anonymous denial and restricted privileged RPC execution.
- Delete a disposable account and confirm Auth, photos and owned rows are removed.
- For preserved data, freeze source writes for the final export/sync so the
  destination does not miss records created during copying. Record rollback and
  post-cutover write handling before changing the production build.

## 5. Cut over

Only after verification, update the ignored `local.properties` values for
`WEGLOW_SUPABASE_URL` and `WEGLOW_SUPABASE_PUBLISHABLE_KEY`, update the project
reference used for function deployment, rebuild Android, and sign in against the
destination. Do not copy old access tokens as a shortcut. Keep the source for the
agreed rollback period. Do not delete either project as part of preparation.

No passwords, database connection strings, secret keys, Auth dumps or personal
data exports should be pasted into chat or committed to this repository.
