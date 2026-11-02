# WeGlow staging security checks

Target project: **tkljmhlrmutcsciqimlq** (`weglow-staging`).
Dashboard: https://supabase.com/dashboard/project/tkljmhlrmutcsciqimlq

The current application and `supabase/config.toml` still target the existing
backend. Creating this project does not redirect the app or deploy functions.

1. Open this staging project's SQL Editor and create a new query as postgres.
2. Paste the **entire** `setup.sql` file and run it. Expect
   `staging_setup_complete`. It creates the four audited user-table shapes,
   a private photo bucket and synthetic catalogs, then applies the repairs.
   It creates no Auth accounts and copies no production user records.
3. If it reports existing app tables or buckets, stop and inspect the project.
   Do not remove the guard or delete objects to get past it. This setup is
   intentionally for a fresh project; a successful run cannot simply be replayed.
4. After successful setup, run the entire `../tests/ownership.sql` in a separate
   SQL Editor query. It creates temporary fixture identities/rows, simulates
   two database roles, verifies isolation/quotas, and rolls its fixtures back.
   A successful query does not prove real Auth login or Storage API behavior.
5. Run `../tests/live-security-audit.sql` in another query and save its result
   separately from the original backend audit for comparison.

This checks the database repair path. Production catalog columns, custom
triggers, Auth configuration, real Storage operations and deployed endpoints
still need separate validation. The synthetic catalog is not suitable for
product/recommendation accuracy checks. Do not point the signed release app at
this test project; a dedicated test build/configuration should be used later.

Local verification: `node supabase/tests/staging-setup.mjs` passed in PGlite.
It checks setup plus SQL ownership/quota tests, rollback of test identities,
private bucket configuration, catalog write denial, and refusal to modify a
database with existing app tables. The local platform fixture is minimal;
actual hosted setup and Auth/Storage API checks remain separate steps.

The script includes the product privilege migration to counter new-project
default grants. The four missing-repair migrations follow in the documented
order. They run inside one outer transaction, including the bounded-path policy.

`setup.sql` is generated from the original ignored `security-audit.json` plus
versioned migrations. To regenerate intentionally:
`node supabase/staging/build-setup.mjs [path-to-original-audit.json]`.
Review the diff before using a regenerated script; it deliberately reconstructs
the pre-repair user schema so the security migrations are exercised.
