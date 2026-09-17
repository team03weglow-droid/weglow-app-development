-- Persist the latest on-device skin scan summary so the chat AI and
-- recommendations can personalize on the detected concerns, mirroring the
-- face_shape pattern (20260913010000_add_profile_face_shape.sql).
--
-- skin_concerns is a short human-readable summary written by the app after
-- every acne scan, e.g. "Blackheads (2), Pimples (1)". last_scan_at is the
-- scan time so the AI can tell how recent the summary is.
alter table public.profiles
    add column if not exists skin_concerns text,
    add column if not exists last_scan_at timestamptz;

-- Row Level Security is unchanged: the per-user SELECT/UPDATE policies from
-- the initial migration already confine these new columns to their owner.