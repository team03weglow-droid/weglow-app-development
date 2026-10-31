-- Run as postgres against a disposable staging/local Supabase database after migrations.
-- All fixtures roll back. Existing product catalog must contain at least one row.
begin;
insert into auth.users(id, email) values
('00000000-0000-4000-8000-0000000000a1', 'security-alice@example.invalid'),
('00000000-0000-4000-8000-0000000000b2', 'security-bob@example.invalid');
insert into public.profiles(id) values
('00000000-0000-4000-8000-0000000000a1'), ('00000000-0000-4000-8000-0000000000b2');
insert into public."Scan-profiles"(user_prof_id) values
('00000000-0000-4000-8000-0000000000a1'), ('00000000-0000-4000-8000-0000000000b2');
do $$ begin
    if not exists (select 1 from public.products) then raise exception 'Seed a staging product first'; end if;
end $$;
insert into public.cart_items(profile_id, product_no, quantity)
select p.id, (select "No" from public.products limit 1), 1 from public.profiles p
where p.id in ('00000000-0000-4000-8000-0000000000a1', '00000000-0000-4000-8000-0000000000b2');
insert into public.saved_products(profile_id, product_no)
select profile_id, product_no from public.cart_items
where profile_id in ('00000000-0000-4000-8000-0000000000a1', '00000000-0000-4000-8000-0000000000b2');

set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-0000000000a1', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-4000-8000-0000000000a1","role":"authenticated"}', true);
do $$
declare affected integer;
begin
    if (select count(*) from public.profiles) <> 1 then raise exception 'Profile read isolation failed'; end if;
    if (select count(*) from public.cart_items) <> 1 then raise exception 'Cart read isolation failed'; end if;
    if (select count(*) from public.saved_products) <> 1 then raise exception 'Saved-products read isolation failed'; end if;
    if (select count(*) from public."Scan-profiles") <> 1 then raise exception 'Scan read isolation failed'; end if;
    update public.cart_items set quantity = 2 where profile_id = '00000000-0000-4000-8000-0000000000b2';
    get diagnostics affected = row_count;
    if affected <> 0 then raise exception 'Cross-account update succeeded'; end if;
    delete from public.saved_products where profile_id = '00000000-0000-4000-8000-0000000000b2';
    get diagnostics affected = row_count;
    if affected <> 0 then raise exception 'Cross-account delete succeeded'; end if;
    begin
        insert into public."Scan-profiles"(user_prof_id) values ('00000000-0000-4000-8000-0000000000b2');
        raise exception 'Cross-account insert succeeded';
    exception when insufficient_privilege then null; end;
    begin
        update public."Scan-profiles" set user_prof_id = '00000000-0000-4000-8000-0000000000b2';
        raise exception 'Ownership reassignment succeeded';
    exception when insufficient_privilege then null; end;
    begin
        update public.profiles set age_range = 'Under 14';
        raise exception 'Invalid age accepted';
    exception when check_violation then null; end;
    begin
        perform public.delete_account_data('00000000-0000-4000-8000-0000000000b2');
        raise exception 'Client could invoke privileged deletion';
    exception when insufficient_privilege then null; end;
    begin
        perform public.reserve_api_request('00000000-0000-4000-8000-0000000000b2', 'chat');
        raise exception 'Client could reserve quota';
    exception when insufficient_privilege then null; end;
end $$;
reset role;
-- Authenticated quota consumers are limited across requests, even after leases are released.
do $$
declare reservation uuid; n integer;
begin
    for n in 1..10 loop
        reservation := public.reserve_api_request('00000000-0000-4000-8000-0000000000a1', 'chat');
        if reservation is null then raise exception 'Quota rejected allowed request'; end if;
        perform public.release_api_request(reservation);
    end loop;
    if public.reserve_api_request('00000000-0000-4000-8000-0000000000a1', 'chat') is not null then
        raise exception 'Minute quota failed';
    end if;
end $$;
rollback;
