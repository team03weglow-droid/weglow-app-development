# Fresh WeGlow Supabase project

Owner decision: start fresh with no migrated Auth users or personal records.
Destination: `nkrxygqtptvarntzxawk`.
Dashboard: https://supabase.com/dashboard/project/nkrxygqtptvarntzxawk
API URL: https://nkrxygqtptvarntzxawk.supabase.co
Copy the real hairstyle catalog only. Do not copy product records.
Development/production configuration now points at this project using an ignored
local publishable key. The rebuilt WeGlow Dev APK is installed on the emulator.
The agent has made read-only public Auth/access checks, not hosted setup changes.

Owner checkpoint: setup/import reportedly completed without SQL errors and
returned an import result. The owner then supplied `verify-setup.sql` results:
342 hairstyles, 0 products, 0 incomplete hairstyle rows; private photo bucket,
routine schedule/completion RPCs, account deletion RPC and app-table RLS checks
all returned true. These are owner-reported SQL results, not direct inspection.
Hosted Auth, image delivery, Storage API isolation and application cutover remain
unverified. Next: follow `AUTH_SETUP.md`.

## Order

1. Open the destination dashboard above and select SQL Editor / New query.
2. Run the entire `setup.sql`. This creates all app tables, an empty hairstyle
   catalog and an empty product compatibility table. It is one transaction and
   stops before creating anything if app/catalog tables or Auth users already
   exist. Expected result:
   `weglow_fresh_project_setup_complete`.
3. Run `../migration/export-hairstyles.sql` in the **old project's** SQL Editor.
   It only reads real hairstyle rows. Copy the complete `catalog_import_sql`
   result into a new query in the destination and run it. Compare
   `imported_hairstyles` with the old project's `exported_rows`. It copies the
   app-facing name, shape, gender, length and image URL fields, generates new IDs,
   and guards against accidentally importing twice. No user/product data moves.
4. Configure Email/password and Google Auth, the narrow
   `weglow://login-callback` allowlist, the new Google OAuth callback, SMTP and
   password-recovery email code `{{ .Token }}`. Review confirmation/password/rate
   limit settings. SQL does not configure dashboard/provider settings.
5. Deploy `smart-endpoint`, `delete-account`, and the required `chat` retirement
   stub with `_shared`. Set the weather secret on the destination and verify
   function authentication against its token-signing configuration.
6. Create disposable test accounts. Verify onboarding, Google/email login,
   recovery, catalog photos, scan saves, routine completions/progress, cart,
   saved products, weather, two-account ownership and account deletion.
7. Once verified, update Android's ignored `local.properties` URL and
   publishable key, update the deployment reference, and rebuild. Users must
   create new accounts; old personal records are not being copied.

The setup includes profile fields/constraints/triggers, scan history, cart,
saved products, routine history/completions, private photo storage/policies,
quota functions and account deletion. It inserts no users or demo records.
The product table remains solely because the current catalog/cart/routine code
references it; it has zero product records. With the current Android behavior,
Discover/recommendations and catalog-built skincare routines cannot populate.
Combined Progress is unavailable until actual scheduled routine tasks exist.
Supporting product-independent routines is a separate application change.
The old staging SQL uses synthetic catalogs and must not substitute for this setup.
Catalog image URLs pointing to the old project's Storage need an asset-copy or
URL update before retiring it. Old private profile photos are not being copied.

The generated SQL is **not rerunnable over an initialized app**; its guard is
intentional. Apply individual migrations for later updates. Never bypass the
guard by dropping existing tables. This explicit bundle does not reconcile CLI
migration history automatically.

Regenerate after changing source migrations:

```powershell
node supabase/fresh-project/build-setup.mjs
```

See `docs/SUPABASE_PROJECT_MOVE.md` for the inventory and cutover checklist.

Local verification: `supabase/tests/fresh-project.mjs` exercises setup on an empty
database, private Storage, validated constraints, empty catalogs, ownership/RPC/
quota checks, and safe rejection of reruns. It also verifies real-value hairstyle
export/import, including quotes, Unicode, NULLs and duplicate-import rejection,
using synthetic isolated fixtures. No hosted project is contacted by these tests.

### Age range update (2026-11-05)

Onboarding now starts at 18–24. Existing projects must apply only
`supabase/migrations/20261105000000_adult_onboarding_age_range.sql`, not rerun the
fresh setup. Applied to nkrxygqtptvarntzxawk on 2026-11-05. Existing 14–24 cohorts
remain unchanged; new inserts or selections of that legacy cohort are rejected.
The generated fresh setup includes this migration. App onboarding tests and the
isolated SQL age-range/fresh-setup checks pass.
