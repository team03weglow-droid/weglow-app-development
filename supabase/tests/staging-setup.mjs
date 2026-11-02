import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';

const { PGlite } = await import(process.env.WEGLOW_PGLITE_MODULE ?? '@electric-sql/pglite');
const setup = await readFile(new URL('../staging/setup.sql', import.meta.url), 'utf8');
const platform = `
  create role anon; create role authenticated; create role service_role bypassrls;
  create schema auth; create schema storage;
  create table auth.users(id uuid primary key, email text);
  create function auth.uid() returns uuid language sql stable as $$
    select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
  $$;
  create function storage.foldername(name text) returns text[] language sql immutable as $$
    select (string_to_array(name, '/'))[1:array_length(string_to_array(name, '/'), 1)-1]
  $$;
  grant usage on schema auth, storage, public to anon, authenticated, service_role;
  alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
  create table storage.buckets(id text primary key, name text, public boolean, file_size_limit bigint, allowed_mime_types text[]);
  create table storage.objects(id uuid primary key default gen_random_uuid(), bucket_id text, name text);
  alter table storage.objects enable row level security;
  grant select, insert, update, delete on storage.objects to authenticated;
`;
const fresh = new PGlite();
const existing = new PGlite();
try {
  await fresh.exec(platform);
  await fresh.exec(setup);
  await fresh.exec(await readFile(new URL('./ownership.sql', import.meta.url), 'utf8'));
  assert.equal((await fresh.query('select count(*)::int as n from auth.users')).rows[0].n, 0);
  for (const name of ['profiles', 'cart_items', 'saved_products', 'Scan-profiles']) {
    assert.equal((await fresh.query(`select count(*)::int as n from public."${name}"`)).rows[0].n, 0);
  }
  const bucket = (await fresh.query("select public, file_size_limit from storage.buckets where id='profile-images'")).rows[0];
  assert.equal(bucket.public, false);
  assert.equal(Number(bucket.file_size_limit), 5242880);
  for (const role of ['anon', 'authenticated']) {
    for (const privilege of ['INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER']) {
      assert.equal((await fresh.query('select has_table_privilege($1,$2,$3) as allowed', [role, 'public.products', privilege])).rows[0].allowed, false);
    }
  }
  await assert.rejects(fresh.exec(setup), /requires an unused project/);
  await fresh.exec('rollback');
  assert.equal((await fresh.query('select count(*)::int as n from public.products')).rows[0].n, 1);
  console.log('PASS: fresh staging setup, two-account SQL isolation/quota checks, private bucket, catalog grants and rerun guard');

  await existing.exec(platform);
  await existing.exec("create table public.profiles(id integer primary key); insert into public.profiles values (42)");
  await assert.rejects(existing.exec(setup), /requires an unused project/);
  await existing.exec('rollback');
  assert.equal((await existing.query('select id from public.profiles')).rows[0].id, 42);
  assert.equal((await existing.query("select to_regclass('public.products') as table_id")).rows[0].table_id, null);
  console.log('PASS: existing database rejected without modifying its table or creating app objects');
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
} finally {
  await fresh.close();
  await existing.close();
}
