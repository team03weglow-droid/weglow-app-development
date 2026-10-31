import { readFile, readdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

// Optional temporary dependency; never connects to a real Supabase database.
const { PGlite } = await import(process.env.WEGLOW_PGLITE_MODULE ?? '@electric-sql/pglite');
const db = new PGlite();
const root = fileURLToPath(new URL('../', import.meta.url));
try {
  await db.exec(`
    create role anon;
    create role authenticated;
    create role service_role bypassrls;
    create schema auth;
    create schema storage;
    create table auth.users(id uuid primary key, email text);
    create function auth.uid() returns uuid language sql stable as $$
      select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
    $$;
    grant usage on schema auth, storage, public to anon, authenticated, service_role;
    grant execute on function auth.uid() to anon, authenticated, service_role;
    alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
    create table public.products("No" integer primary key, name text);
    insert into public.products values (1, 'staging fixture');
    create table public.hairstyles(id integer primary key);
    create table storage.buckets(id text primary key, name text, public boolean, file_size_limit bigint, allowed_mime_types text[]);
    create table storage.objects(id uuid primary key default gen_random_uuid(), bucket_id text, name text);
    alter table storage.objects enable row level security;
    grant select, insert, update, delete on storage.objects to authenticated;
    create function storage.foldername(name text) returns text[] language sql immutable as $$
      select (string_to_array(name, '/'))[1:array_length(string_to_array(name, '/'), 1)-1]
    $$;
  `);
  const migrations = (await readdir(`${root}/migrations`)).filter(x => x.endsWith('.sql')).sort();
  for (const name of migrations) {
    await db.exec(await readFile(`${root}/migrations/${name}`, 'utf8'));
  }
  // New security migrations must also be safely repeatable.
  for (const name of migrations.filter(x => x.startsWith('20261031'))) {
    await db.exec(await readFile(`${root}/migrations/${name}`, 'utf8'));
  }
  await db.exec(await readFile(`${root}/tests/ownership.sql`, 'utf8'));
  console.log('PASS: all migrations apply, security migrations repeat, ownership/privileged-RPC/quota checks pass');
  await db.exec(`
    insert into auth.users values ('00000000-0000-4000-8000-0000000000a1', 'alice@example.invalid');
    insert into public.profiles(id) values ('00000000-0000-4000-8000-0000000000a1');
    insert into public.cart_items(profile_id, product_no) values ('00000000-0000-4000-8000-0000000000a1', 1);
    insert into public.saved_products(profile_id, product_no) values ('00000000-0000-4000-8000-0000000000a1', 1);
    insert into public."Scan-profiles"(user_prof_id) values ('00000000-0000-4000-8000-0000000000a1');
    insert into storage.objects(bucket_id, name) values ('profile-images', '00000000-0000-4000-8000-0000000000a1/photo.jpg');
  `);
  await assert.rejects(db.query(`select public.delete_account_data('00000000-0000-4000-8000-0000000000a1')`), /Remove account photos/);
  assert.equal((await db.query('select count(*)::int as count from auth.users')).rows[0].count, 1);
  // Storage API removal is mocked here by deleting the fixture object as database owner.
  await db.exec(`delete from storage.objects;
    select public.delete_account_data('00000000-0000-4000-8000-0000000000a1');`);
  for (const table of ['auth.users', 'public.profiles', 'public.cart_items', 'public.saved_products', 'public."Scan-profiles"']) {
    assert.equal((await db.query(`select count(*)::int as count from ${table}`)).rows[0].count, 0);
  }
  console.log('PASS: deletion rejects remaining photos and atomically removes auth and app records after cleanup');
} finally { await db.close(); }
