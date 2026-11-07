-- READ ONLY. Run in the SOURCE project's SQL Editor, and later in the destination.
-- Returns schema/security metadata and aggregate counts, never user records,
-- password hashes, session tokens, object paths or function bodies.
with
app_tables as (
    select n.nspname as schema_name, c.relname as table_name, c.relrowsecurity as rls_enabled,
           c.relforcerowsecurity as force_rls, c.reltuples::bigint as estimated_rows
    from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname in ('public', 'private') and c.relkind in ('r', 'p')
    order by 1, 2
),
app_columns as (
    select table_schema, table_name, column_name, data_type, udt_name,
           is_nullable, is_identity, identity_generation, is_generated
    from information_schema.columns
    where table_schema in ('public', 'private')
    order by table_schema, table_name, ordinal_position
),
app_constraints as (
    select n.nspname as schema_name, c.relname as table_name, k.conname,
           k.convalidated, pg_get_constraintdef(k.oid) as definition
    from pg_constraint k join pg_class c on c.oid = k.conrelid
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname in ('public', 'private') order by 1, 2, 3
),
app_policies as (
    select schemaname, tablename, policyname, permissive, roles, cmd, qual, with_check
    from pg_policies where schemaname in ('public', 'private')
        or (schemaname = 'storage' and tablename in ('objects', 'buckets'))
    order by 1, 2, 3
),
app_grants as (
    select table_schema, table_name, grantee, privilege_type
    from information_schema.role_table_grants
    where grantee in ('anon', 'authenticated', 'service_role')
        and table_schema in ('public', 'private') order by 1, 2, 3, 4
),
app_functions as (
    select n.nspname as schema_name, p.proname as function_name,
           pg_get_function_identity_arguments(p.oid) as arguments,
           p.prosecdef as security_definer, p.proconfig as settings,
           has_function_privilege('anon', p.oid, 'EXECUTE') as anon_execute,
           has_function_privilege('authenticated', p.oid, 'EXECUTE') as authenticated_execute,
           has_function_privilege('service_role', p.oid, 'EXECUTE') as service_execute
    from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname in ('public', 'private') and p.prokind in ('f', 'p')
    order by 1, 2, 3
),
app_triggers as (
    select n.nspname as schema_name, c.relname as table_name, t.tgname as trigger_name,
           pn.nspname as function_schema, p.proname as function_name, t.tgenabled as enabled
    from pg_trigger t join pg_class c on c.oid = t.tgrelid
    join pg_namespace n on n.oid = c.relnamespace
    join pg_proc p on p.oid = t.tgfoid join pg_namespace pn on pn.oid = p.pronamespace
    where not t.tgisinternal and n.nspname in ('public', 'private', 'auth', 'storage')
    order by 1, 2, 3
),
buckets as (
    select id, public, file_size_limit, allowed_mime_types from storage.buckets order by id
),
storage_counts as (
    select bucket_id, count(*) as object_count from storage.objects group by bucket_id order by bucket_id
),
extensions as (
    select e.extname, e.extversion, n.nspname as schema_name
    from pg_extension e join pg_namespace n on n.oid = e.extnamespace order by 1
)
select jsonb_pretty(jsonb_build_object(
    'captured_at', now(),
    'app_tables', coalesce((select jsonb_agg(to_jsonb(r)) from app_tables r), '[]'::jsonb),
    'columns', coalesce((select jsonb_agg(to_jsonb(r)) from app_columns r), '[]'::jsonb),
    'constraints', coalesce((select jsonb_agg(to_jsonb(r)) from app_constraints r), '[]'::jsonb),
    'policies', coalesce((select jsonb_agg(to_jsonb(r)) from app_policies r), '[]'::jsonb),
    'grants', coalesce((select jsonb_agg(to_jsonb(r)) from app_grants r), '[]'::jsonb),
    'functions', coalesce((select jsonb_agg(to_jsonb(r)) from app_functions r), '[]'::jsonb),
    'triggers', coalesce((select jsonb_agg(to_jsonb(r)) from app_triggers r), '[]'::jsonb),
    'buckets', coalesce((select jsonb_agg(to_jsonb(r)) from buckets r), '[]'::jsonb),
    'storage_counts', coalesce((select jsonb_agg(to_jsonb(r)) from storage_counts r), '[]'::jsonb),
    'auth_user_count', (select count(*) from auth.users),
    'extensions', coalesce((select jsonb_agg(to_jsonb(r)) from extensions r), '[]'::jsonb)
)) as migration_inventory;
