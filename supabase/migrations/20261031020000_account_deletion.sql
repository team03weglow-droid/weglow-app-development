-- Called only by the verified delete-account Edge Function, after storage cleanup.
-- Database deletion is atomic; existing access tokens lose their backing profile/data.
create or replace function public.delete_account_data(p_user_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
    if exists (select 1 from storage.objects where bucket_id = 'profile-images'
        and (storage.foldername(name))[1] = p_user_id::text) then
        raise exception 'Remove account photos before deleting account';
    end if;
    delete from public."Scan-profiles" where user_prof_id = p_user_id;
    delete from public.cart_items where profile_id = p_user_id;
    delete from public.saved_products where profile_id = p_user_id;
    delete from public.profiles where id = p_user_id;
    delete from auth.users where id = p_user_id;
end $$;
revoke all on function public.delete_account_data(uuid) from public, anon, authenticated;
grant execute on function public.delete_account_data(uuid) to service_role;
