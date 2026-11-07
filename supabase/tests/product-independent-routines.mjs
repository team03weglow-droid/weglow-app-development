import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
const { PGlite } = await import(process.env.WEGLOW_PGLITE_MODULE ?? '@electric-sql/pglite');
const db = new PGlite();
const alice = '00000000-0000-4000-8000-0000000000a1';
const bob = '00000000-0000-4000-8000-0000000000b2';
const legacy = { morning: [{ label: 'Missing catalog slot', product: null, reasons: [] }], evening: [] };
const plan = { morning: [{ label: 'Cleanser', product: null, reasons: [], trackable: true }], evening: [] };
try {
  await db.exec(`create role anon; create role authenticated; create role service_role;
    create schema auth;
    create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
    grant usage on schema public, auth to anon, authenticated;
    create table public.profiles(id uuid primary key);
    create table public."Scan-profiles"(user_prof_id uuid references public.profiles(id), "Date_time" timestamptz);
    insert into public.profiles values ('${alice}'), ('${bob}');`);
  await db.exec(await readFile(new URL('../migrations/20261104000000_combined_progress.sql', import.meta.url), 'utf8'));
  const old = (await db.query("insert into public.routine_schedules(profile_id,effective_from,zone_id,plan) values ($1,now()-interval '3 days','UTC',$2) returning id", [alice, JSON.stringify(legacy)])).rows[0].id;
  const migration = await readFile(new URL('../migrations/20261106000000_product_independent_routines.sql', import.meta.url), 'utf8');
  await db.exec(migration); await db.exec(migration);
  async function session(user, sql, args = []) {
    await db.exec(`set role authenticated; set request.jwt.claim.sub = '${user}'`);
    try { return await db.query(sql, args); } finally { await db.exec('reset role'); }
  }
  await session(alice, "select public.register_routine_schedule($1,'UTC')", [JSON.stringify(plan)]);
  let rows = (await db.query('select *,effective_from <= now() as active from public.routine_schedules order by effective_from')).rows;
  assert.equal(rows.length, 2); assert.deepEqual(rows[0].plan, legacy); assert.equal(rows[1].active, true);
  const id = rows[1].id;
  // Synthetic test only: make a past occurrence due without modifying a real user's history.
  await db.query("update public.routine_schedules set effective_from=date_trunc('day',now())-interval '1 day' where id=$1", [id]);
  const date = (await db.query("select to_char(now()-interval '1 day','YYYY-MM-DD') as day")).rows[0].day;
  const complete = (user, value, schedule = id) => session(user, 'select public.set_routine_task_completion($1,$2,true,0,$3)', [schedule,date,value]);
  await complete(alice,true); await complete(alice,true); await complete(alice,false);
  rows = (await db.query('select * from public.routine_task_completions')).rows;
  assert.equal(rows.length,1); assert.equal(rows[0].completed,false);
  await assert.rejects(complete(bob,true), /Invalid scheduled task/);
  await assert.rejects(complete(alice,true,old), /no scheduled product/);
  assert.equal((await session(bob,'select * from public.routine_task_completions')).rows.length,0);
  const revised = { ...plan, evening: [{ label: 'Moisturizer',product:null,trackable:true }] };
  await session(alice,"select public.register_routine_schedule($1,'UTC')",[JSON.stringify(revised)]);
  assert.deepEqual((await db.query('select plan from public.routine_schedules where id=$1',[id])).rows[0].plan,plan);
  assert.equal((await db.query('select count(*)::int as n from public.routine_schedules where effective_from>now()')).rows[0].n,1);
  await assert.rejects(session(alice,"select public.register_routine_schedule($1,'UTC')",[JSON.stringify({morning:[{label:'Cleanser',trackable:'yes'}],evening:[]})]), /Invalid routine step/);
  console.log('PASS: product-free tasks, idempotent completion, legacy exclusion, unchanged history, future revisions, country-independent ownership checks');
} finally { await db.close(); }
