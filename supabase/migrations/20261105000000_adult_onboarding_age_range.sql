begin;

-- Keep legacy cohorts readable without claiming that their users were 18+.
-- New selections use 18–24; existing 14–24 values may remain unchanged.
alter table public.profiles drop constraint if exists profiles_allowed_age_range;
alter table public.profiles add constraint profiles_allowed_age_range
    check (age_range is null or age_range in ('18–24', '14–24', '25–35', '36–45', '46–59', '60+')) not valid;

create or replace function public.enforce_adult_profile_age_range()
returns trigger language plpgsql set search_path = '' as $$
begin
    if new.age_range = '14–24' then
        if tg_op = 'INSERT' then
            raise exception 'Select an age range starting at 18 or above.' using errcode = '23514';
        elsif new.age_range is distinct from old.age_range then
            raise exception 'Select an age range starting at 18 or above.' using errcode = '23514';
        end if;
    end if;
    return new;
end;
$$;

revoke all on function public.enforce_adult_profile_age_range() from public, anon, authenticated;
drop trigger if exists enforce_adult_profile_age_range on public.profiles;
create trigger enforce_adult_profile_age_range
before insert or update of age_range on public.profiles
for each row execute function public.enforce_adult_profile_age_range();

commit;
