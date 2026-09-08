# State Management Strategy

Use lifecycle-scoped `ViewModel` + `StateFlow` for cross-screen or business-relevant state. Keep only ephemeral widget state (for example password visibility, a currently typed field before submission, or a local animation flag) inside a composable with `remember`.

Current Phase 1 owners:
- `AuthViewModel`: authentication operation state, startup/session decision, auth events.
- `OnboardingViewModel`: name/age/skin type/gender/sensitivity and profile persistence.
- `ScanViewModel`: captured image URI shared between scan and result screens.
- `DiscoverViewModel`: product catalog state from a repository.
- `HairstyleViewModel`: recommendation result from a repository.

Global mutable singleton holders are prohibited for UI/session state.
