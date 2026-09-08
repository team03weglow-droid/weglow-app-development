# WEGLOW Screen Map — Phase 0 Baseline

This inventory is based on the Kotlin project supplied for the Phase 1 refactor. Phase 1 does not redesign or implement screens.

| Area | Existing screen | Current role | Later phase |
|---|---|---|---|
| Launch | LoadingScreen | Startup/loading presentation | Phase 3/4 |
| Auth | LoginScreen | Login form UI | Phase 4 |
| Auth | SignUpScreen | Registration form UI | Phase 4 |
| Onboarding | AgeSelectionScreen | Age range selection | Phase 5 |
| Onboarding | SkinTypeScreen | Skin type selection | Phase 5 |
| Onboarding | GenderSelectionScreen | Gender selection | Phase 5 |
| Onboarding | SkinSensitivityScreen | Sensitivity selection | Phase 5 |
| Onboarding | WelcomeIntroScreen | Onboarding completion UI | Phase 5 |
| Main | HomeScreen | Dashboard | Later feature phases |
| Main | DiscoverScreen | Product discovery | Phase 7/9 |
| Scan | ScanScreen | Camera/gallery scan flow | Phase 8 |
| Scan | ScanResultsScreen | Skin scan presentation | Phase 8/9 |
| Hair | HairstyleResultsScreen | Hairstyle recommendations | Phase 9 |
| Routine | RoutinesScreen | Routine presentation | Phase 9 |
| Profile | ProfileScreen | User/profile/settings presentation | Phase 6 |

## Reusable component opportunities

Current large screens contain reusable cards, buttons, chips, bottom-navigation items, settings rows and scan controls. Extraction is intentionally deferred to Phase 2 because Theme + reusable UI is its dedicated scope.
