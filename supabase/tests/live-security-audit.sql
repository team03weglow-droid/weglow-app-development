-- Read-only metadata audit: one result containing all checks.
-- Does not read user records or change data. Paste this entire file into SQL Editor.
-- Copy the security_audit JSON result after running it.
with
tables as (
with expected(schema_name, table_name) as (values
  ('public', 'profiles'), ('public', 'Scan-profiles'),
  ('public', 'cart_items'), ('public', 'saved_products'),
  ('public', 'products'), ('public', 'hairstyles'),
  ('storage', 'objects')
)
select e.schema_name, e.table_name, c.oid is not null as table_exists,
       c.relrowsecurity as rls_enabled
from expected e
left join pg_namespace n on n.nspname = e.schema_name
left join pg_class c on c.relnamespace = n.oid and c.relname = e.table_name
order by 1, 2
),
policies as (
select schemaname, tablename, policyname, permissive, roles, cmd, qual, with_check
from pg_policies
where (schemaname = 'public' and tablename in
       ('profiles', 'Scan-profiles', 'cart_items', 'saved_products', 'products', 'hairstyles'))
   or (schemaname = 'storage' and tablename = 'objects')
order by schemaname, tablename, policyname
),
grants as (
select table_schema, table_name, grantee, privilege_type
from information_schema.role_table_grants
where grantee in ('anon', 'authenticated')
  and ((table_schema = 'public' and table_name in
       ('profiles', 'Scan-profiles', 'cart_items', 'saved_products', 'products', 'hairstyles'))
       or (table_schema = 'storage' and table_name = 'objects'))
order by 1, 2, 3, 4
),
privileged_functions as (
with expected(signature) as (values
  ('public.delete_account_data(uuid)'),
  ('public.reserve_api_request(uuid,text)'),
  ('public.release_api_request(uuid)')
)
select e.signature, p.oid is not null as routine_exists, p.prosecdef as security_definer,
       p.proconfig as function_settings,
       has_function_privilege('anon', p.oid, 'EXECUTE') as anon_can_execute,
       has_function_privilege('authenticated', p.oid, 'EXECUTE') as user_can_execute,
       has_function_privilege('service_role', p.oid, 'EXECUTE') as service_can_execute
from expected e left join pg_proc p on p.oid = to_regprocedure(e.signature)
),
photo_bucket as (
select id, public, file_size_limit, allowed_mime_types
from storage.buckets where id = 'profile-images'
),
columns as (
select table_name, column_name, data_type, is_nullable, column_default,
       is_identity, identity_generation, is_generated
from information_schema.columns
where table_schema = 'public'
  and table_name in ('profiles', 'Scan-profiles', 'cart_items', 'saved_products')
order by table_name, ordinal_position
),
constraints as (
select c.relname as table_name, k.conname, k.convalidated,
       pg_get_constraintdef(k.oid) as definition
from pg_constraint k
join pg_class c on c.oid = k.conrelid
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relname in ('profiles', 'Scan-profiles', 'cart_items', 'saved_products')
order by 1, 2
)
select jsonb_pretty(jsonb_build_object(
  'tables', coalesce((select jsonb_agg(to_jsonb(r)) from tables r), '[]'::jsonb),
  'policies', coalesce((select jsonb_agg(to_jsonb(r)) from policies r), '[]'::jsonb),
  'grants', coalesce((select jsonb_agg(to_jsonb(r)) from grants r), '[]'::jsonb),
  'privileged_functions', coalesce((select jsonb_agg(to_jsonb(r)) from privileged_functions r), '[]'::jsonb),
  'photo_bucket', coalesce((select jsonb_agg(to_jsonb(r)) from photo_bucket r), '[]'::jsonb),
  'columns', coalesce((select jsonb_agg(to_jsonb(r)) from columns r), '[]'::jsonb),
  'constraints', coalesce((select jsonb_agg(to_jsonb(r)) from constraints r), '[]'::jsonb)
)) as security_audit;
