# WEGLOW Architecture Plan

## Runtime dependency flow

```text
Compose screen
    -> feature ViewModel / StateFlow UI state
    -> domain repository contract
    -> data repository implementation
    -> Supabase / in-memory source / later API or ML source
```

`AppContainer` is the Phase 1 composition root for repository dependencies. `WeGlowApp` constructs lifecycle-scoped ViewModels using those contracts. Supabase repositories receive a `SupabaseClient` in their constructor; they do not fetch a global client themselves.

## Enforced boundaries
The `architectureCheck` Gradle task runs before `preBuild` and fails the build when:
- `domain` imports Android, Compose, data, or Supabase APIs;
- `feature` imports Supabase or `data.remote` infrastructure;
- `ui` imports Supabase or `data.remote` infrastructure.

## State ownership
- Authentication/session outcome: `AuthViewModel`.
- Onboarding answers and persistence: `OnboardingViewModel`.
- Captured scan URI: `ScanViewModel`.
- Product catalog presentation seam: `DiscoverViewModel` + `CatalogRepository`.
- Hairstyle recommendation presentation seam: `HairstyleViewModel` + `HairstyleRepository`.

No process-global mutable holder is used for onboarding or scan-photo state.

## Navigation
Routes are centralized in `Destination`. Navigation decisions are in the app/navigation composition layer. Login success and app startup are session-aware instead of unconditionally routing to Home.

## Later phases
Phase 2 may extract a full reusable design system from existing Compose screens. Later backend/AI phases may replace in-memory repository implementations without changing the domain/presentation dependency direction.
