-- Persist the latest environment/UV reading on the authenticated user's
-- profile so the chat AI can personalize sunscreen and UV advice.
alter table public.profiles
    add column if not exists uv_index double precision,
    add column if not exists uv_category text,
    add column if not exists humidity int,
    add column if not exists location_name text,
    add column if not exists last_env_at timestamptz;
