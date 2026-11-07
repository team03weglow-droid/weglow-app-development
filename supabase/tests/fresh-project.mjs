import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
const { PGlite } = await import(process.env.WEGLOW_PGLITE_MODULE ?? '@electric-sql/pglite');
const setup = await readFile(new URL('../fresh-project/setup.sql', import.meta.url), 'utf8');
const platformFixture = `
  create role anon; create role authenticated; create role service_role bypassrls;
  create schema auth; create schema storage;
  create table auth.users(id uuid primary key, email text);
  create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
  grant usage on schema public, auth, storage to anon, authenticated, service_role;
  alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
  create table storage.buckets(id text primary key, name text, public boolean, file_size_limit bigint, allowed_mime_types text[]);
  create table storage.objects(id uuid primary key default gen_random_uuid(), bucket_id text, name text);
  alter table storage.objects enable row level security;
  grant select, insert, update, delete on storage.objects to authenticated;
  create function storage.foldername(name text) returns text[] language sql immutable as $$
    select (string_to_array(name, '/'))[1:array_length(string_to_array(name, '/'), 1)-1]
  $$;
`;
const db = new PGlite();
try {
  await db.exec(platformFixture);
  await db.exec(setup);
  for (const table of ['products', 'hairstyles', 'profiles', '"Scan-profiles"', 'cart_items', 'saved_products', 'routine_schedules', 'routine_task_completions']) {
    assert.equal((await db.query(`select count(*)::int as n from public.${table}`)).rows[0].n, 0);
  }
  assert.equal((await db.query('select count(*)::int as n from auth.users')).rows[0].n, 0);
  const bucket = (await db.query("select * from storage.buckets where id = 'profile-images'")).rows[0];
  assert.equal(bucket.public, false); assert.equal(bucket.file_size_limit, 5242880);
  assert.deepEqual(bucket.allowed_mime_types, ['image/jpeg', 'image/png', 'image/webp']);
  const checks = (await db.query("select convalidated from pg_constraint where conname in ('profiles_allowed_age_range','profiles_allowed_gender','cart_quantity_bounds')")).rows;
  assert.equal(checks.length, 3); assert(checks.every(c => c.convalidated));
  assert.equal((await db.query("select to_regprocedure('public.register_routine_schedule(jsonb,text)') is not null as installed")).rows[0].installed, true);
  await assert.rejects(db.exec(setup), /Fresh setup requires no existing/);
  await db.exec('rollback');

  // Synthetic records exist only in this isolated test, never in setup.sql.
  await db.exec(`insert into public.products("No", product_id, product_name) values (1, 'fixture-only', 'Synthetic test product')`);
  await db.exec(await readFile(new URL('./ownership.sql', import.meta.url), 'utf8'));
  console.log('PASS: fresh setup on an empty project creates no users/catalog data, installs progress and private Storage, validates constraints, rejects reruns and passes ownership/RPC/quota checks');

  const sourceRows = [
    ["Women's layered cut", 'oval', 'female', 'medium', "https://images.example.invalid/hair's.jpg"],
    ['ශ්‍රී style', 'round', 'male', null, null],
  ];
  for (const row of sourceRows) await db.query('insert into public.hairstyles(hairstyle_name, face_shape, gender, hair_length, image_url) values ($1,$2,$3,$4,$5)', row);
  const exportSql = await readFile(new URL('../migration/export-hairstyles.sql', import.meta.url), 'utf8');
  const exported = (await db.query(exportSql)).rows[0];
  assert.equal(Number(exported.exported_rows), 2);
  const destination = new PGlite();
  try {
    await destination.exec(platformFixture);
    await destination.exec(setup);
    await destination.exec(exported.catalog_import_sql);
    const copied = (await destination.query('select hairstyle_name, face_shape, gender, hair_length, image_url from public.hairstyles order by id')).rows;
    const original = (await db.query('select hairstyle_name, face_shape, gender, hair_length, image_url from public.hairstyles order by hairstyle_name, face_shape, gender, hair_length, image_url')).rows;
    assert.deepEqual(copied, original);
    assert.equal((await destination.query('select count(*)::int as n from public.products')).rows[0].n, 0);
    const readiness = (await destination.query(await readFile(new URL('../fresh-project/verify-setup.sql', import.meta.url), 'utf8'))).rows[0];
    assert.equal(Number(readiness.hairstyle_count), 2);
    assert.equal(Number(readiness.product_count), 0);
    assert.equal(Number(readiness.incomplete_hairstyle_rows), 0);
    for (const [key, value] of Object.entries(readiness)) if (key.endsWith('_ready')) assert.equal(value, true, key);
    await assert.rejects(destination.exec(exported.catalog_import_sql), /not empty/);
    await destination.exec('rollback');
    assert.equal((await destination.query('select count(*)::int as n from public.hairstyles')).rows[0].n, 2);
    console.log('PASS: hairstyle-only export/import preserves quotes, Unicode and NULLs, leaves products empty and rejects duplicate imports');
  } finally { await destination.close(); }
} finally { await db.close(); }
