-- Hairstyle recommendations are public catalog data for signed-in app users.
alter table public.hairstyles enable row level security;

grant select on table public.hairstyles to authenticated;

drop policy if exists "Authenticated users can view hairstyles" on public.hairstyles;
create policy "Authenticated users can view hairstyles"
    on public.hairstyles
    for select
    to authenticated
    using (true);
