-- Persist the latest face-shape scan on the authenticated user's profile.
alter table public.profiles add column if not exists face_shape text;
