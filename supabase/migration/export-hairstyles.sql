-- READ ONLY: run this on the OLD project. Does not change its database.
-- Exports the real fields consumed by Android, not user records or product data.
-- Copy the entire catalog_import_sql cell into a new query on destination
-- nkrxygqtptvarntzxawk AFTER its fresh-project/setup.sql has completed.
-- Legacy IDs/unused columns are not copied; destination IDs are generated.
with rows_to_move as (
  select hairstyle_name, face_shape, gender, hair_length, image_url
  from public.hairstyles
), statements as (
  select format(
    'insert into public.hairstyles(hairstyle_name, face_shape, gender, hair_length, image_url) values (%L, %L, %L, %L, %L);',
    hairstyle_name, face_shape, gender, hair_length, image_url
  ) as statement,
  row_number() over(order by hairstyle_name, face_shape, gender, hair_length, image_url) as position
  from rows_to_move
)
select count(*) as exported_rows,
  E'-- REAL hairstyle catalog; destination nkrxygqtptvarntzxawk.\nbegin;\n'
  || E'do $catalog_guard$ begin if exists(select 1 from public.hairstyles) then raise exception ''Destination hairstyle catalog is not empty. Import cancelled to avoid duplicates.''; end if; end $catalog_guard$;\n'
  || coalesce(string_agg(statement, E'\n' order by position), '')
  || E'\ncommit;\nselect count(*) as imported_hairstyles from public.hairstyles;\n'
  as catalog_import_sql
from statements;
