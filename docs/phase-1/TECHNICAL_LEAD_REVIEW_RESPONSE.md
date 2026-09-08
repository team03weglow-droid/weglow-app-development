# Technical-lead review response

This revision was made against the architectural review shown by the developer.

| Review finding | Phase 1 revision |
|---|---|
| No ViewModel / feature state | Added real `AuthViewModel`, `OnboardingViewModel`, `ScanViewModel`, `DiscoverViewModel`, `HairstyleViewModel` with `StateFlow` where state is needed. Removed empty feature marker files. |
| Data/domain stack unreachable | Login/signup and session startup now call `AuthViewModel -> AuthRepository -> SupabaseAuthRepository`; onboarding save calls `OnboardingViewModel -> ProfileRepository -> SupabaseProfileRepository`. |
| Dependency direction documented only | Added `architectureCheck` Gradle verification and attached it to `preBuild`. |
| No evidence build passes | Full Android build still must run on the developer machine; this environment cannot download the Gradle 9.5 distribution. Exact command is documented. |
| Global mutable holders | Removed `OnboardingAnswersHolder` and `ScanPhotoHolder`; replaced with lifecycle-scoped ViewModels. |
| Business/catalog logic inside composables | Product catalog and hairstyle recommendation data moved behind domain repository contracts with in-memory data implementations. |
| Onboarding data discarded | Age, skin type, gender, sensitivity, and signup name are retained in `OnboardingViewModel` and persisted through `ProfileRepository`. |
| Scan capture error emits unwritten URI | Corrected the CameraX error path so failure does not emit a bogus cache URI. |
| Raw-string navigation / god graph | Added centralized `Destination` model; all routes now use it. Authentication/session decisions are derived from ViewModel/repository state. |
| Repositories tied to Supabase singleton | Supabase repositories now receive `SupabaseClient` by constructor injection. `AppContainer` is the composition root. |
| Theme layer bypassed | Existing prototype palette is centralized in `ui/theme/Color.kt` and `WeGlowTheme` now installs the project typography. Full component extraction remains Phase 2. |
| Naming drift | Normalized lowercase screen filenames to PascalCase names matching their symbols. |
| Backup/privacy default | Disabled Android backup for the prototype because future profile/scan data is privacy-sensitive. |
| No meaningful unit tests | Added `AuthViewModelTest` using fake repository implementations and coroutine test dispatcher. |

## Remaining non-Phase-1 work
The current scan analysis animation, routine content, and several profile/home values are still prototype content. Replacing those with real ML/database functionality belongs to their feature phases. Large Compose screens may be decomposed into reusable components in Phase 2 without changing the architecture established here.
