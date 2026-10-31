-- Quotas live in Postgres, shared by all Edge Function instances. No client can reserve/release.
begin;
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;
create table if not exists private.api_requests (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    service text not null check (service in ('chat', 'weather', 'delete-account')),
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    finished_at timestamptz
);
create index if not exists api_requests_user_service_time on private.api_requests(user_id, service, created_at);
alter table private.api_requests enable row level security;

create or replace function public.reserve_api_request(p_user_id uuid, p_service text)
returns uuid language plpgsql security definer set search_path = '' as $$
declare minute_limit integer; day_limit integer; active_limit integer; reservation uuid;
begin
    if p_service = 'chat' then minute_limit := 10; day_limit := 100; active_limit := 2;
    elsif p_service = 'weather' then minute_limit := 30; day_limit := 500; active_limit := 4;
    elsif p_service = 'delete-account' then minute_limit := 2; day_limit := 10; active_limit := 1;
    else raise exception 'Invalid service'; end if;
    perform pg_advisory_xact_lock(hashtextextended(p_user_id::text || ':' || p_service, 0));
    delete from private.api_requests where user_id = p_user_id and service = p_service and created_at < now() - interval '2 days';
    if (select count(*) from private.api_requests where user_id = p_user_id and service = p_service and created_at >= now() - interval '1 minute') >= minute_limit
    or (select count(*) from private.api_requests where user_id = p_user_id and service = p_service and created_at >= date_trunc('day', now() at time zone 'UTC') at time zone 'UTC') >= day_limit
    or (select count(*) from private.api_requests where user_id = p_user_id and service = p_service and finished_at is null and expires_at > now()) >= active_limit then
        return null;
    end if;
    insert into private.api_requests(user_id, service, expires_at)
        values (p_user_id, p_service, now() + interval '90 seconds') returning id into reservation;
    return reservation;
end $$;
create or replace function public.release_api_request(p_request_id uuid)
returns void language sql security definer set search_path = '' as $$
    update private.api_requests set finished_at = now() where id = p_request_id;
$$;
revoke all on function public.reserve_api_request(uuid, text) from public, anon, authenticated;
revoke all on function public.release_api_request(uuid) from public, anon, authenticated;
grant execute on function public.reserve_api_request(uuid, text) to service_role;
grant execute on function public.release_api_request(uuid) to service_role;
commit;
