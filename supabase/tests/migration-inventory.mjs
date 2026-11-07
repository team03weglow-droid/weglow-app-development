import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
const { PGlite } = await import(process.env.WEGLOW_PGLITE_MODULE ?? '@electric-sql/pglite');
const db = new PGlite();
try {
  await db.exec(`
    create role anon; create role authenticated; create role service_role;
    create schema auth; create schema storage; create schema private;
    create table auth.users(id uuid primary key, encrypted_password text);
    insert into auth.users values ('00000000-0000-4000-8000-0000000000a1', 'DO_NOT_EXPOSE_PASSWORD');
    create table storage.buckets(id text primary key, public boolean, file_size_limit bigint, allowed_mime_types text[]);
    create table storage.objects(id uuid primary key default gen_random_uuid(), bucket_id text, name text);
    insert into storage.buckets values ('profile-images', false, 5242880, array['image/jpeg']);
    insert into storage.objects(bucket_id, name) values ('profile-images', 'DO_NOT_EXPOSE_PATH');
    create table public.products("No" smallint primary key, product_name text);
    insert into public.products values (1, 'DO_NOT_EXPOSE_ROW');
    create function public.inventory_test() returns text language sql as $$ select 'DO_NOT_EXPOSE_FUNCTION_BODY'::text $$;
  `);
  const sql = await readFile(new URL('../migration/project-inventory.sql', import.meta.url), 'utf8');
  await db.exec('begin read only');
  const result = (await db.query(sql)).rows[0].migration_inventory;
  await db.exec('commit');
  const inventory = typeof result === 'string' ? JSON.parse(result) : result;
  assert.equal(inventory.auth_user_count, 1);
  assert.equal(inventory.storage_counts[0].object_count, 1);
  assert.equal(inventory.app_tables.find(t => t.table_name === 'products').schema_name, 'public');
  assert(inventory.columns.some(c => c.table_name === 'products' && c.column_name === 'No'));
  assert(inventory.functions.some(f => f.function_name === 'inventory_test'));
  assert(!JSON.stringify(inventory).includes('DO_NOT_EXPOSE'));
  assert.equal((await db.query('select count(*)::int as n from public.products')).rows[0].n, 1);
  console.log('PASS: migration inventory executes in a read-only transaction and reports metadata/aggregate counts without exporting records, passwords, object paths or function bodies');
} finally { await db.close(); }
