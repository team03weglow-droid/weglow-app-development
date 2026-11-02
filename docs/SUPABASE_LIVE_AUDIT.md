# Supabase metadata review - 2 November 2026

Source: the complete seven-group JSON supplied by the owner in the ignored
`security-audit.json` file. This is a supplied snapshot, not direct access to
production. No live SQL, migrations, deployments or account deletions were
performed during this review.

## Confirmed working foundations

- RLS is enabled on profiles, Scan-profiles, cart_items, saved_products and
  storage.objects. The listed permissive policies scope ordinary user-table
  operations to `auth.uid()` and storage access to the owner's folder.
- The profile-images bucket is private, limited to 5 MiB and JPEG/PNG/WebP.
- Cart and saved products have UUID primary keys with `gen_random_uuid()`
  defaults and unique `(profile_id, product_no)` constraints. Android's omitted
  IDs and conflict targets are compatible. Preserve these primary keys.
- profiles.id references auth.users.id with cascade; both product tables
  reference profiles.id and products."No" with cascade. The scan owner FK exists
  without cascade. The deletion RPC explicitly deletes scans first to handle it.
- The onboarding answer-presence constraint exists and is validated. The scan
  column names and types match Android's writes; Date_time must be supplied.

## Confirmed gaps

1. `delete_account_data(uuid)`, `reserve_api_request(uuid,text)` and
   `release_api_request(uuid)` are absent. The repository's current deletion
   and quota-protected weather functions depend on these RPCs. Their newer
   handlers cannot operate successfully on this database until the RPCs are
   deployed. The snapshot does not identify which Edge Function versions are live.
2. Client database grants are broader than necessary: anon has all listed
   privileges on profiles, Scan-profiles and hairstyles; authenticated has
   TRUNCATE/REFERENCES/TRIGGER on profiles, scans, cart, saved products and
   hairstyles, plus catalog writes on hairstyles. RLS does not protect TRUNCATE
   or REFERENCES. This is an unsafe database grant, not proof that a normal
   anonymous REST request can truncate the tables. Ordinary row operations
   still require applicable RLS policies.
3. profiles lacks uv_index, uv_category, humidity, location_name and last_env_at.
   Android updates the first four, so environment persistence fails even if
   fetching/displaying weather succeeds. The existing environment migration
   adds all five without overwriting profile data.
4. cart quantity is checked only as positive; the intended 999 upper bound is
   absent. Allowed age/gender constraints and restrictive ownership/path guards
   from the later security migration are also absent. Existing permissive
   ownership checks are present; this is missing additional hardening.
5. Storage ownership checks allow nested object paths in an owner's folder.
   The new deletion handler supports flat image filenames. The restrictive
   bounded-path policy prevents new nested writes; old nested objects need
   inspection and possibly owner-assisted cleanup through the Storage API.

Catalog RLS flags were not included in the original tables group. The read-only
audit now also includes products and hairstyles; rerun it to establish those
flags. Do not change grants on storage.objects globally based on this review:
those are shared across buckets and must be considered alongside bucket policies.

## Exact repair order to verify in staging

The owner created `weglow-staging` at project ref `tkljmhlrmutcsciqimlq`.
See `supabase/staging/README.md` and its locally verified `setup.sql` for the
fresh-project setup. It recreates audited user-table shapes with synthetic
catalogs and installs the repairs; it does not clone production user data or
change the Android app's backend. Hosted execution remains to be verified.

Take a recoverable backup and inspect migration history first. The repository
has duplicate legacy migration timestamps; do not blindly run CLI db push or
rename migrations already recorded by Supabase. These files already contain
the needed changes; do not run CONSOLIDATED_SETUP.sql over the live database.

1. `supabase/migrations/20260915000000_add_env_to_profiles.sql`
2. `supabase/migrations/20261031000000_user_data_security.sql`
3. `supabase/migrations/20261031010000_api_quotas.sql`
4. `supabase/migrations/20261031020000_account_deletion.sql`

Run the files in order in a disposable staging project with a compatible schema.
The security file commits its table changes before adding the storage policy;
verify both parts succeeded. Storage policy management may require the hosted
project's appropriate administrative role. No file in this list executes an
account deletion while installing the functions.

The age, gender and cart bounds are initially NOT VALID: historical invalid
values survive installation, but subsequent writes to those rows must satisfy
the checks. Inspect/remediate legacy values intentionally before validating;
do not rewrite a person's age or delete rows to make the constraints pass.

Then rerun live-security-audit.sql and confirm the owner guards, bounded-path
policy, environment columns, constraints and service-role-only RPC access.
Run ownership.sql in disposable staging, followed by real two-account database
and Storage tests. Deploy matching smart-endpoint, delete-account and the chat
retirement stub with shared helpers and server-side secrets; verify endpoint
authentication, recovery emails and deletion with disposable accounts. See
SECURITY_HARDENING.md for deployment prerequisites. Repeat the approved changes
in production only after staging succeeds.

## Local verification against the supplied metadata

`supabase/tests/audited-schema.mjs` rebuilds the four audited user-table shapes,
constraints, grants and policies in in-memory PGlite, with minimal mocked
Auth/Storage/catalog infrastructure. It reads no production user rows and has
no production connection. The original audit's catalog RLS flags are explicitly
assumed enabled in the fixture, not inferred for the live project.

The local checks cover applying the four repair migrations twice, preserving
UUID defaults/IDs and historical invalid values, removing excessive privileges,
adding environment columns, service-role-only RPC permissions, two-account row
isolation, quota exhaustion and deletion with the existing non-cascading scan
FK. Storage cleanup is modeled, not a real Storage API deletion.

To run, install `@electric-sql/pglite` in a temporary/ignored directory, set
WEGLOW_PGLITE_MODULE to its `dist/index.js` file URL, and run
`node supabase/tests/audited-schema.mjs [path-to-security-audit.json]`.

Official explanations:
- https://www.postgresql.org/docs/17/ddl-rowsecurity.html
- https://supabase.com/docs/guides/database/postgres/row-level-security
