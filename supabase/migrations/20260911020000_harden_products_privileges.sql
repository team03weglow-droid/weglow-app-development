-- Phase 7: defense-in-depth on public.products table-level privileges.
--
-- 20260911000000_allow_authenticated_product_reads.sql already enabled RLS and
-- added a read-only SELECT policy for authenticated users. That migration did
-- not state what other table-level privileges exist on public.products,
-- because the table itself was not created by a repository migration (it was
-- populated from an external product dataset directly in the hosted project;
-- see the Phase 7 report for the recommended follow-up to capture its real
-- schema with `supabase db pull`).
--
-- This migration makes the intended privilege set explicit and idempotent
-- regardless of how the table was provisioned: authenticated users may only
-- SELECT from the catalog, never INSERT/UPDATE/DELETE it, and the anonymous
-- role has no access at all (the app requires sign-in before Discover loads).
-- REVOKE on a privilege that was never granted is a safe no-op.

revoke all on table public.products from anon;
revoke insert, update, delete, truncate, references, trigger
    on table public.products
    from authenticated;

grant select on table public.products to authenticated;
