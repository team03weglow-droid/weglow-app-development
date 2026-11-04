-- Apply after the existing user-data security migrations. No legacy counts/settings are invented.
begin;
alter table public."Scan-profiles" add column if not exists progress_scan_id uuid not null default gen_random_uuid();
alter table public."Scan-profiles" add column if not exists detection_settings text;
create unique index if not exists scan_progress_id_unique on public."Scan-profiles"(progress_scan_id);
create index if not exists scan_progress_owner_date on public."Scan-profiles"(user_prof_id, "Date_time");

create table if not exists public.routine_schedules (
    id uuid primary key default gen_random_uuid(),
    profile_id uuid not null references public.profiles(id) on delete cascade,
    effective_from timestamptz not null,
    zone_id text not null,
    plan jsonb not null check (jsonb_typeof(plan) = 'object'),
    created_at timestamptz not null default now(),
    unique(profile_id, effective_from)
);
create table if not exists public.routine_task_completions (
    schedule_id uuid not null references public.routine_schedules(id) on delete cascade,
    profile_id uuid not null references public.profiles(id) on delete cascade,
    task_date date not null,
    morning boolean not null,
    step_index integer not null check(step_index between 0 and 19),
    completed boolean not null,
    updated_at timestamptz not null default now(),
    primary key(schedule_id, task_date, morning, step_index)
);
create index if not exists routine_completion_owner on public.routine_task_completions(profile_id);

alter table public.routine_schedules enable row level security;
alter table public.routine_task_completions enable row level security;
revoke all on public.routine_schedules, public.routine_task_completions from public, anon, authenticated;
grant select on public.routine_schedules, public.routine_task_completions to authenticated;
drop policy if exists routine_schedule_owner on public.routine_schedules;
create policy routine_schedule_owner on public.routine_schedules for select to authenticated
    using (profile_id = (select auth.uid()));
drop policy if exists routine_completion_owner on public.routine_task_completions;
create policy routine_completion_owner on public.routine_task_completions for select to authenticated
    using (profile_id = (select auth.uid()));

-- Only this bounded RPC can write schedules. Caller identity and dates are server-derived.
-- Initial activation is now; later changes begin at the next midnight in the stored zone.
-- A not-yet-active schedule can be revised; an active/historical snapshot cannot be changed.
create or replace function public.register_routine_schedule(p_plan jsonb, p_zone text)
returns void language plpgsql security definer set search_path = '' as $$
declare
    owner_id uuid := auth.uid();
    active public.routine_schedules%rowtype;
    pending public.routine_schedules%rowtype;
    starts timestamptz;
    step jsonb;
begin
    if owner_id is null then raise exception 'Sign in to save your routine'; end if;
    perform 1 from public.profiles where id = owner_id for update;
    if not found then raise exception 'Profile is unavailable'; end if;
    if p_zone is null or not exists(select 1 from pg_timezone_names where name = p_zone) then
        raise exception 'Invalid time zone';
    end if;
    if p_plan is null or jsonb_typeof(p_plan) <> 'object'
        or jsonb_typeof(p_plan->'morning') is distinct from 'array'
        or jsonb_typeof(p_plan->'evening') is distinct from 'array'
        or octet_length(p_plan::text) > 131072 then raise exception 'Invalid routine'; end if;
    if jsonb_array_length(p_plan->'morning') > 20 or jsonb_array_length(p_plan->'evening') > 20 then
        raise exception 'Too many routine steps';
    end if;
    for step in select value from jsonb_array_elements((p_plan->'morning') || (p_plan->'evening')) loop
        if jsonb_typeof(step) <> 'object' or jsonb_typeof(step->'label') is distinct from 'string'
            or (step ? 'product' and step->'product' <> 'null'::jsonb and
                (jsonb_typeof(step->'product') <> 'object' or jsonb_typeof(step->'product'->'id') is distinct from 'string')) then
            raise exception 'Invalid routine step';
        end if;
    end loop;
    select * into active from public.routine_schedules
        where profile_id = owner_id and effective_from <= now() order by effective_from desc limit 1;
    select * into pending from public.routine_schedules
        where profile_id = owner_id and effective_from > now() order by effective_from limit 1;
    if active.id is null then
        insert into public.routine_schedules(profile_id, effective_from, zone_id, plan)
            values(owner_id, now(), p_zone, p_plan);
    elsif active.plan = p_plan and active.zone_id = p_zone then
        -- Cancelling a future revision never changes tasks that were already due.
        delete from public.routine_schedules where id = pending.id;
    else
        starts := ((now() at time zone active.zone_id)::date + 1)::timestamp at time zone active.zone_id;
        if pending.id is not null then
            update public.routine_schedules set plan = p_plan, zone_id = p_zone where id = pending.id;
        else
            insert into public.routine_schedules(profile_id, effective_from, zone_id, plan)
                values(owner_id, starts, p_zone, p_plan);
        end if;
    end if;
end $$;

-- Idempotent SET, never increment/toggle. One row per scheduled occurrence.
create or replace function public.set_routine_task_completion(
    p_schedule_id uuid, p_task_date date, p_morning boolean, p_step_index integer, p_completed boolean
) returns void language plpgsql security definer set search_path = '' as $$
declare
    owner_id uuid := auth.uid();
    schedule public.routine_schedules%rowtype;
    due_at timestamptz;
    step jsonb;
begin
    if owner_id is null then raise exception 'Sign in to save completion'; end if;
    select * into schedule from public.routine_schedules where id = p_schedule_id and profile_id = owner_id;
    if schedule.id is null or p_task_date is null or p_morning is null or p_completed is null
        or p_step_index is null or p_step_index not between 0 and 19 then raise exception 'Invalid scheduled task'; end if;
    step := schedule.plan -> (case when p_morning then 'morning' else 'evening' end) -> p_step_index;
    if step is null or step->'product' is null or step->'product' = 'null'::jsonb then
        raise exception 'This task has no scheduled product';
    end if;
    due_at := (p_task_date + (case when p_morning then time '08:00' else time '20:00' end)) at time zone schedule.zone_id;
    if due_at < schedule.effective_from or due_at > now() or exists(
        select 1 from public.routine_schedules where profile_id = owner_id
            and effective_from > schedule.effective_from and effective_from <= due_at
    ) then raise exception 'This task is not due in that schedule'; end if;
    insert into public.routine_task_completions(schedule_id, profile_id, task_date, morning, step_index, completed)
        values(p_schedule_id, owner_id, p_task_date, p_morning, p_step_index, p_completed)
    on conflict(schedule_id, task_date, morning, step_index)
        do update set completed = excluded.completed, updated_at = now();
end $$;
revoke all on function public.register_routine_schedule(jsonb,text) from public, anon;
revoke all on function public.set_routine_task_completion(uuid,date,boolean,integer,boolean) from public, anon;
grant execute on function public.register_routine_schedule(jsonb,text) to authenticated;
grant execute on function public.set_routine_task_completion(uuid,date,boolean,integer,boolean) to authenticated;
-- Both new tables cascade on profile deletion, including the existing deletion RPC.
commit;
