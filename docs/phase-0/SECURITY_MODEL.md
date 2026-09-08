# Security & Privacy Model — Planning

Phase 1 establishes configuration boundaries; security implementation is Phase 10 except where required to avoid embedding configuration in source.

## Principles

- Never ship Supabase service-role keys in the mobile application.
- A client publishable/anon key is configuration, not authorization; database security must rely on Row Level Security.
- User face/skin images are sensitive application data and should have explicit retention/access rules.
- Do not log passwords, tokens, raw face images or private scan payloads.
- User-scoped caches/state must be cleared at logout.
- External network and storage operations must fail visibly rather than simulate success.

## Phase 1 change

Supabase URL/publishable key are read through build configuration (`AppConfig`) instead of being literals in Kotlin source. Missing configuration fails only when backend infrastructure is actually accessed.
