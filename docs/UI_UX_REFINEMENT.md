# WeGlow UI/UX refinement

## Design direction

Warm ivory canvas, forest-green actions, sage surfaces and restrained coral accents.
Serif headings retain the brand personality; sans-serif body text, labels and controls
prioritize readability. Important secondary text uses tested high-contrast colors.

## Implemented

- Shared typography scale and contrast tokens across onboarding and the main screens.
- Visible selected-tab indicator, larger routine checkboxes with accessibility state.
- Content-sized profile shelf and hairstyle cards; scrollable onboarding at larger text sizes.
- Four-step onboarding labels, non-overlapping age ranges, explicit age selection,
  balanced skin option and an explicit not-sure/skip path.
- Home routine and Profile shelf navigation, recommendation empty-state actions.
- Home's hairstyle action opens the scan entry point (which offers hairstyle analysis).
- Home and Routines share completion state using the same original plan indices.
- Device-local, account-scoped routine journal with save confirmation and error feedback.
  Notes are per calendar date; completion is per date, period and step. No cloud sync.
  Journal storage is excluded from Android backup and device transfer.
- Calendar month/year, previous/next week and Today controls. Manual morning/evening
  selection no longer resets every 30 seconds.
- Scan results appear when ready, without artificial multi-second waits or invented
  completion percentages. Branded analysis animation is retained.
- Scan uncertainty appears before concern details; overlays can be switched off and
  photo dimensions are available in expandable details.
- Product packshots use Fit instead of being cropped.
- No unsupported hydration measurement or fabricated skin score is presented as real data.

## Retained integrations, explicitly marked Coming soon

Shopping bag, saved products, orders, account settings, password recovery, product
pairing, trending, skin-score metrics/detail view, skincare guide, hairstyle filters,
AR Mirror and Try AR remain discoverable. Their pending controls are disabled with
readable labels. This is a presentation state, not a completed backend integration.
Connect each real action before removing its Coming soon state.

## Follow-up validation

Verified on 2026-09-12: debug app and test APK build succeeded; 21 focused unit tests
passed, including 16 contrast pairs; all 10 device tests passed on the connected phone.
Normal and 130% text fixture screenshots were inspected. The earlier full unit run
passed 143/144 tests; BundledAcneModelTest failed with a native-runtime UnsatisfiedLinkError.

The automated fixtures cover narrow layouts, enlarged text, disabled shopping,
routine key consistency, note callbacks and local journal persistence/account isolation.
They do not validate commerce, AR, recovery, scan-history services or other pending integrations.
Real signed-in visual review, TalkBack across every screen, and very large system-font
settings still warrant a separate device pass. The Home action ordering, header/avatar
consolidation and remaining catalogue duplication can be refined further after that review.

Do not run connectedDebugAndroidTest against the user's phone: its cleanup may uninstall
the main app. Install test APKs with adb install -r and invoke instrumentation directly.
