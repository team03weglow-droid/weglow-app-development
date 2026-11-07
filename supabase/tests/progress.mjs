import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
const { PGlite } = await import(process.env.WEGLOW_PGLITE_MODULE ?? '@electric-sql/pglite');
const alice = '00000000-0000-4000-8000-0000000000a1';
const bob = '00000000-0000-4000-8000-0000000000b2';
// Synthetic fixtures only. This runner never connects to a hosted database.
const plan = { morning: [{ label: 'Cleanse', product: { id: 'test-product' }, reasons: [] }, { label: 'Unmatched', product: null, reasons: [] }], evening: [] };
const revised = { ...plan, evening: [{ label: 'Moisturize', product: { id: 'test-product-2' }, reasons: [] }] };
const migration = await readFile(new URL('../migrations/20261104000000_combined_progress.sql', import.meta.url), 'utf8') + '\n' +
  await readFile(new URL('../migrations/20261106000000_product_independent_routines.sql', import.meta.url), 'utf8');
for (const legacy of [true, false]) {
  const db = new PGlite();
  try {
    await db.exec(`
      create role anon; create role authenticated; create role service_role bypassrls;
      create schema auth;
      create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
      grant usage on schema public, auth to anon, authenticated;
      create table public.profiles(id uuid primary key);
      create table public."Scan-profiles"(${legacy ? '' : 'id uuid primary key default gen_random_uuid(),'} user_prof_id uuid references public.profiles(id), "Date_time" timestamptz, dark_spots smallint);
      insert into public.profiles values ('${alice}'), ('${bob}');
      insert into public."Scan-profiles"(user_prof_id, "Date_time", dark_spots) values ('${alice}', now() - interval '1 day', 25);
      alter default privileges in schema public grant all on tables to anon, authenticated;
    `);
    await db.exec(migration);
    await db.exec(migration); // repeatable, preserves legacy row and unknown settings
    const oldScan = (await db.query('select * from public."Scan-profiles"')).rows[0];
    assert.equal(oldScan.dark_spots, 25);
    assert.equal(oldScan.detection_settings, null);
    assert.match(oldScan.progress_scan_id, /^[0-9a-f-]{36}$/);
    const schedule = (await db.query(`insert into public.routine_schedules(profile_id, effective_from, zone_id, plan)
      values ($1, now() - interval '3 days', 'UTC', $2::jsonb) returning id`, [alice, JSON.stringify(plan)])).rows[0].id;
    const yesterday = (await db.query("select (now() at time zone 'UTC')::date - 1 as day")).rows[0].day;
    const taskDate = typeof yesterday === 'string' ? yesterday.slice(0, 10) : yesterday.toISOString().slice(0, 10);
    const session = async (user, sql, args = []) => {
      await db.exec(`set role authenticated; set request.jwt.claim.sub = '${user}'`);
      try { return await db.query(sql, args); } finally { await db.exec('reset role'); }
    };
    const set = (user, completed, index = 0, day = taskDate) => session(user,
      'select public.set_routine_task_completion($1, $2::date, true, $3, $4)', [schedule, day, index, completed]);
    await set(alice, true); await set(alice, true); await set(alice, true);
    assert.equal((await db.query('select count(*)::int as n from public.routine_task_completions')).rows[0].n, 1);
    await set(alice, false);
    assert.equal((await db.query('select completed from public.routine_task_completions')).rows[0].completed, false);
    await set(alice, true);
    await assert.rejects(set(bob, true), /Invalid scheduled task/);
    await assert.rejects(set(alice, true, 1), /no scheduled product/);
    await assert.rejects(set(alice, true, 0, '2099-01-01'), /not due/);
    await assert.rejects(set(alice, true, 0, '2000-01-01'), /not due/);
    await assert.rejects(set(alice, true, -1), /Invalid scheduled task/);
    assert.equal((await session(bob, 'select * from public.routine_schedules')).rows.length, 0);
    assert.equal((await session(bob, 'select * from public.routine_task_completions')).rows.length, 0);
    assert.equal((await session(alice, 'select * from public.routine_task_completions')).rows.length, 1);
    await assert.rejects(session(alice, 'update public.routine_schedules set plan = $1', [JSON.stringify(revised)]), /permission denied/);
    await assert.rejects(session(alice, 'delete from public.routine_task_completions'), /permission denied/);
    await db.exec('set role anon');
    await assert.rejects(db.query('select * from public.routine_task_completions'), /permission denied/);
    await assert.rejects(db.query('select public.register_routine_schedule($1::jsonb, $2)', [JSON.stringify(plan), 'UTC']), /permission denied/);
    await db.exec('reset role');
    await session(alice, 'select public.register_routine_schedule($1::jsonb, $2)', [JSON.stringify(revised), 'UTC']);
    const schedules = (await db.query('select * from public.routine_schedules order by effective_from')).rows;
    assert.equal(schedules.length, 2);
    assert.deepEqual(schedules[0].plan, plan);
    assert.deepEqual(schedules[1].plan, revised);
    assert.equal((await db.query('select effective_from > now() as future from public.routine_schedules where id = $1', [schedules[1].id])).rows[0].future, true);
    await session(alice, 'select public.register_routine_schedule($1::jsonb, $2)', [JSON.stringify(revised), 'UTC']);
    assert.equal((await db.query('select count(*)::int as n from public.routine_schedules')).rows[0].n, 2);
    await assert.rejects(session(alice, 'select public.register_routine_schedule($1::jsonb, $2)', [JSON.stringify(revised), 'invalid-zone']), /Invalid time zone/);
    await assert.rejects(session(alice, 'select public.register_routine_schedule($1::jsonb, $2)', ['{}', 'UTC']), /Invalid routine/);
    // Both server and task owner/date identities survive changes; deleting the account removes both tables.
    await db.exec(`delete from public."Scan-profiles"; delete from public.profiles where id = '${alice}'`);
    assert.equal((await db.query('select count(*)::int as n from public.routine_schedules')).rows[0].n, 0);
    assert.equal((await db.query('select count(*)::int as n from public.routine_task_completions')).rows[0].n, 0);
    console.log(`PASS: ${legacy ? 'audited legacy' : 'UUID primary-key'} schema, repeatable migration, immutable schedule history, idempotent checkboxes, due-time validation, two-account isolation and deletion cascades`);
  } finally { await db.close(); }
}
