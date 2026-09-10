-- The Discover catalog is available to signed-in app users.
alter table public.products enable row level security;

grant select on table public.products to authenticated;

do $$
begin
    if not exists (
        select 1
        from pg_policies
        where schemaname = 'public'
          and tablename = 'products'
          and policyname = 'Authenticated users can view products'
    ) then
        create policy "Authenticated users can view products"
            on public.products
            for select
            to authenticated
            using (true);
    end if;
end
$$;
