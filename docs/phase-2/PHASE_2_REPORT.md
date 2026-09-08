# WEGLOW Phase 2 — Theme + Reusable UI

## Scope
Phase 2 implements the central design-system foundation only. It deliberately does not implement Phase 3+ navigation behavior, complete authentication, onboarding persistence, database schema, AI scanning, recommendations, routines, or final security/testing.

## Implemented
- Central semantic color palette in `ui/theme/Color.kt`; screen-level hexadecimal color literals were removed from `ui/screens`.
- Central typography scale in `ui/theme/Type.kt`, using the existing WEGLOW Junge font and preserving Hurricane for the wordmark.
- Central spacing, radius, size, and elevation tokens in `ui/theme/Tokens.kt`.
- Reusable Compose components in `ui/components/WeGlowComponents.kt`:
  - `WeGlowPrimaryButton`
  - `WeGlowSecondaryButton`
  - `WeGlowTextField`
  - `WeGlowPasswordField`
  - `WeGlowCard`
  - `WeGlowLoadingView`
  - `WeGlowErrorView`
  - `WeGlowProgressIndicator`
  - `WeGlowEmptyState`
- Reusable `WeGlowBottomNavigation` and `WeGlowNavItem` in `ui/components/WeGlowBottomNavigation.kt`.
- Login and signup screens now consume reusable fields/buttons/error components instead of maintaining duplicate field/button implementations.
- Main bottom navigation now consumes the reusable design-system component.
- Password field owns visibility behavior consistently and exposes an accessible show/hide description.
- Non-implemented social login was changed from fake clickable controls to a non-interactive "coming soon" message. Forgot-password is also non-interactive until its authentication phase implementation.

## Visual compatibility
The existing WEGLOW palette, fonts, screen structure, and assets are retained. Phase 2 centralizes and reuses them rather than redesigning product flows. Figma remains the visual source of truth for subsequent UI refinement.

## Verification
Static source checks confirm there are no raw `Color(0x...)` literals left under `ui/screens`.
A full Gradle compile could not be executed in the packaging environment because `services.gradle.org` is unreachable here. Run the standard local verification command in Android Studio before closing Phase 2:

`./gradlew clean testDebugUnitTest lintDebug assembleDebug`

Then launch the app and visually smoke-test all existing screens.
