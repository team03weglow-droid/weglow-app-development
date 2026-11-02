// Reconstruct only audited metadata in disposable, in-memory PostgreSQL.
// No production connection or user records are used. Catalog/Auth/Storage
// infrastructure is a minimal fixture, not a replica of hosted Supabase.
import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';

const { PGlite } = await import(process.env.WEGLOW_PGLITE_MODULE ?? '@electric-sql/pglite');
const auditPath = process.argv[2] ?? fileURLToPath(new URL('../../security-audit.json', import.meta.url));
const audit = JSON.parse(await readFile(auditPath, 'utf8'));
const quote = value => `"${value.replaceAll('"', '""')}"`;
const owned = ['profiles', 'Scan-profiles', 'cart_items', 'saved_products'];
const migrations = [
  '20260915000000_add_env_to_profiles.sql',
  '20261031000000_user_data_security.sql',
  '20261031010000_api_quotas.sql',
  '20261031020000_account_deletion.sql',
];
const db = new PGlite();
try {
  await db.exec(`
    create role anon; create role authenticated; create role service_role bypassrls;
    create schema auth; create schema storage;
    create table auth.users(id uuid primary key, email text);
    create function auth.uid() returns uuid language sql stable as $$
      select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
    $$;
    create function storage.foldername(name text) returns text[] language sql immutable as $$
      select (string_to_array(name, '/'))[1:array_length(string_to_array(name, '/'), 1)-1]
    $$;
    grant usage on schema public, auth, storage to anon, authenticated, service_role;
    create table public.products("No" smallint primary key);
    insert into public.products values (1);
    create table public.hairstyles(id integer primary key);
    create table storage.objects(id uuid primary key default gen_random_uuid(), bucket_id text, name text);
    create table storage.buckets(id text primary key, public boolean, file_size_limit bigint, allowed_mime_types text[]);
  `);
  for (const table of owned) {
    const columns = audit.columns.filter(column => column.table_name === table);
    assert.ok(columns.length > 0, `Missing audited columns: ${table}`);
    // Expressions come from the supplied metadata and are used only in this sandboxed database.
    await db.exec(`create table public.${quote(table)} (${columns.map(column =>
      `${quote(column.column_name)} ${column.data_type}` +
      (column.is_nullable === 'NO' ? ' not null' : '') +
      (column.column_default === null ? '' : ` default ${column.column_default}`)
    ).join(',')})`);
  }
  // Referenced PK/unique constraints must exist before foreign keys are added.
  const constraints = [...audit.constraints].sort((a, b) =>
    Number(a.definition.startsWith('FOREIGN KEY')) - Number(b.definition.startsWith('FOREIGN KEY')));
  for (const constraint of constraints) {
    await db.exec(`alter table public.${quote(constraint.table_name)} add constraint ${quote(constraint.conname)} ${constraint.definition}`);
  }
  for (const table of audit.tables.filter(table => table.rls_enabled)) {
    await db.exec(`alter table ${quote(table.schema_name)}.${quote(table.table_name)} enable row level security`);
  }
  // Catalog RLS flags were not included in the original audit. Enable them in
  // this fixture, without treating that assumption as evidence about production.
  await db.exec('alter table public.products enable row level security; alter table public.hairstyles enable row level security');
  for (const policy of audit.policies) {
    await db.exec(`create policy ${quote(policy.policyname)} on ${quote(policy.schemaname)}.${quote(policy.tablename)}` +
      ` as ${policy.permissive} for ${policy.cmd} to ${policy.roles.map(quote).join(',')}` +
      (policy.qual === null ? '' : ` using (${policy.qual})`) +
      (policy.with_check === null ? '' : ` with check (${policy.with_check})`));
  }
  for (const grant of audit.grants) {
    await db.exec(`grant ${grant.privilege_type} on ${quote(grant.table_schema)}.${quote(grant.table_name)} to ${quote(grant.grantee)}`);
  }
  for (const bucket of audit.photo_bucket) {
    await db.query('insert into storage.buckets values ($1,$2,$3,$4)',
      [bucket.id, bucket.public, bucket.file_size_limit, bucket.allowed_mime_types]);
  }
  const sentinel = '00000000-0000-4000-8000-0000000000c3';
  await db.query('insert into auth.users values ($1,$2)', [sentinel, 'fixture@example.invalid']);
  await db.query('insert into public.profiles(id, age_range) values ($1,$2)', [sentinel, 'Under 14']);
  await db.query('insert into public.cart_items(profile_id, product_no, quantity) values ($1,1,1001)', [sentinel]);
  await db.query('insert into public.saved_products(profile_id, product_no) values ($1,1)', [sentinel]);
  await db.query('insert into public."Scan-profiles"(user_prof_id,"Date_time") values ($1,now())', [sentinel]);
  const original = await db.query('select id, quantity from public.cart_items');
  for (let pass = 0; pass < 2; pass++) {
    for (const name of migrations) await db.exec(await readFile(new URL(`../migrations/${name}`, import.meta.url), 'utf8'));
  }
  assert.deepEqual((await db.query('select id, quantity from public.cart_items')).rows, original.rows);
  assert.equal((await db.query(`select column_default from information_schema.columns where table_name='cart_items' and column_name='id'`)).rows[0].column_default, 'gen_random_uuid()');
  assert.equal((await db.query(`select count(*)::int as n from information_schema.columns where table_name='profiles' and column_name in ('uv_index','uv_category','humidity','location_name','last_env_at')`)).rows[0].n, 5);
  for (const table of [...owned, 'hairstyles']) {
    for (const role of ['anon', 'authenticated']) {
      for (const privilege of ['TRUNCATE', 'REFERENCES', 'TRIGGER']) {
        const result = await db.query('select has_table_privilege($1,$2,$3) as allowed', [role, `public.${quote(table)}`, privilege]);
        assert.equal(result.rows[0].allowed, false, `${role} retains ${privilege} on ${table}`);
      }
    }
  }
  for (const signature of ['public.delete_account_data(uuid)', 'public.reserve_api_request(uuid,text)', 'public.release_api_request(uuid)']) {
    for (const role of ['anon', 'authenticated', 'service_role']) {
      const result = await db.query("select has_function_privilege($1,$2,'EXECUTE') as allowed", [role, signature]);
      assert.equal(result.rows[0].allowed, role === 'service_role', `${role} has unexpected access to ${signature}`);
    }
  }
  await db.exec(await readFile(new URL('./ownership.sql', import.meta.url), 'utf8'));
  console.log('PASS: audited user-table shape accepts repairs twice; UUID defaults, IDs and legacy rows preserved; excessive grants removed; environment columns added; ownership, RPC and quotas pass');
  const alice = '00000000-0000-4000-8000-0000000000a1';
  await db.query('insert into auth.users values ($1,$2)', [alice, 'alice@example.invalid']);
  await db.query('insert into public.profiles(id) values ($1)', [alice]);
  await db.query('insert into public."Scan-profiles"(user_prof_id,"Date_time") values ($1,now())', [alice]);
  await db.query('insert into storage.objects(bucket_id,name) values ($1,$2)', ['profile-images', `${alice}/photo.jpg`]);
  await assert.rejects(db.query('select public.delete_account_data($1)', [alice]), /Remove account photos/);
  // Model successful Storage API cleanup only; hosted Storage remains untested.
  await db.query('delete from storage.objects where name=$1', [`${alice}/photo.jpg`]);
  await db.query('select public.delete_account_data($1)', [alice]);
  assert.equal((await db.query('select count(*)::int as n from auth.users where id=$1', [alice])).rows[0].n, 0);
  assert.equal((await db.query('select count(*)::int as n from public."Scan-profiles" where user_prof_id=$1', [alice])).rows[0].n, 0);
  assert.equal((await db.query('select count(*)::int as n from public.profiles where id=$1', [sentinel])).rows[0].n, 1);
  console.log('PASS: deletion handles the audited non-cascading scan FK, refuses remaining photos and preserves another account');
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
} finally {
  await db.close();
}
