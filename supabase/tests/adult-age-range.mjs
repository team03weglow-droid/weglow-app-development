import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
const { PGlite } = await import(process.env.WEGLOW_PGLITE_MODULE ?? '@electric-sql/pglite');
const db = new PGlite();
try {
  await db.exec(`create role anon; create role authenticated;
    create table public.profiles(id int primary key, age_range text, full_name text);
    insert into public.profiles values (1, '14–24', 'Legacy');`);
  await db.exec(await readFile(new URL('../migrations/20261105000000_adult_onboarding_age_range.sql', import.meta.url), 'utf8'));
  await db.exec("insert into public.profiles values (2, '18–24', 'Adult')");
  await assert.rejects(db.exec("insert into public.profiles values (3, '14–24', 'New legacy selection')"));
  await assert.rejects(db.exec("update public.profiles set age_range = '14–24' where id = 2"));
  await assert.rejects(db.exec("insert into public.profiles values (4, 'Under 18', 'Invalid')"));
  await db.exec("update public.profiles set full_name = 'Updated' where id = 1");
  await db.exec("update public.profiles set age_range = '14–24' where id = 1");
  assert.equal((await db.query('select age_range from public.profiles where id = 1')).rows[0].age_range, '14–24');
  await db.exec("update public.profiles set age_range = '18–24' where id = 1");
  console.log('PASS: adult ranges accepted, new teen cohorts rejected, existing cohorts preserved and editable');
} finally { await db.close(); }
