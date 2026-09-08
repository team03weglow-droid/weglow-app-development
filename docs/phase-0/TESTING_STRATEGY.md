# Testing Strategy

Phase 1 establishes three levels of verification:

1. **Build-time architecture enforcement** — `architectureCheck` is attached to `preBuild` and rejects forbidden dependency directions.
2. **Unit tests** — ViewModels are tested against fake domain repositories. `AuthViewModelTest` proves startup and sign-in routing without Supabase.
3. **Android verification** — `testDebugUnitTest`, `lintDebug`, and `assembleDebug` must run on the developer machine, followed by a smoke test on an emulator/device.

Future phases should add repository tests, navigation tests, Compose UI tests, camera error/permission tests, and API/ML contract tests.
