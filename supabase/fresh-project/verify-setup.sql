-- READ ONLY. Run on nkrxygqtptvarntzxawk after setup and hairstyle import.
select
  (select count(*) from public.hairstyles) as hairstyle_count,
  (select count(*) from public.products) as product_count,
  (select count(*) from public.hairstyles
     where hairstyle_name is null or btrim(hairstyle_name) = ''
        or face_shape is null or btrim(face_shape) = ''
        or gender is null or btrim(gender) = '') as incomplete_hairstyle_rows,
  exists(select 1 from storage.buckets where id = 'profile-images'
     and public = false and file_size_limit = 5242880) as private_photo_bucket_ready,
  to_regprocedure('public.register_routine_schedule(jsonb,text)') is not null as routine_schedule_rpc_ready,
  to_regprocedure('public.set_routine_task_completion(uuid,date,boolean,integer,boolean)') is not null as routine_completion_rpc_ready,
  to_regprocedure('public.delete_account_data(uuid)') is not null as account_deletion_rpc_ready,
  (select count(*) = 8 and bool_and(c.relrowsecurity)
     from pg_class c join pg_namespace n on n.oid = c.relnamespace
     where n.nspname = 'public' and c.relname in
       ('profiles', 'Scan-profiles', 'products', 'hairstyles', 'cart_items',
        'saved_products', 'routine_schedules', 'routine_task_completions')) as app_tables_rls_ready;
