# Privacy Policy & Terms and Conditions Implementation

Implementation report for adding in-app Privacy Policy and Terms &
Conditions screens to WeGlow, reachable from Account Settings, Sign Up, and
Login. Scope was kept strictly to the legal screens, legal navigation, and
authentication/legal links — no unrelated screens were redesigned, no
Supabase schema was touched, no migrations were created, Delete Account was
not implemented, and the existing 14+ minimum-age implementation was not
modified. Nothing was staged, committed, or pushed.

## 1. Files changed/created

**New:**
- `app/src/main/java/com/example/weglow/ui/components/LegalSection.kt`
- `app/src/main/java/com/example/weglow/ui/screens/PrivacyPolicyScreen.kt`
- `app/src/main/java/com/example/weglow/ui/screens/TermsConditionsScreen.kt`
- `app/src/test/java/com/example/weglow/navigation/DestinationTest.kt`

**Modified:**
- `app/src/main/java/com/example/weglow/navigation/Destination.kt`
- `app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt`
- `app/src/main/java/com/example/weglow/ui/screens/AccountSettingsScreen.kt`
- `app/src/main/java/com/example/weglow/ui/screens/LoginScreen.kt`
- `app/src/main/java/com/example/weglow/ui/screens/SignUpScreen.kt`

## 2. Navigation changes

Added `Destination.PrivacyPolicy` (`privacy_policy`) and
`Destination.TermsConditions` (`terms_conditions`) to the existing sealed
class, and two new `composable(...)` entries in the single existing
`NavHost`/`NavController` in `WeGlowApp.kt`. No second `NavHost` was
created.

## 3. Account Settings changes

Added a "Legal" section below the existing "Profile" card, in the exact
same card/heading visual language already used there. Two rows — "Privacy
Policy" and "Terms & Conditions" — via a new `LegalLinkRow` (label +
chevron, matching existing row conventions in this screen), navigating
through the existing `NavController`.

## 4. Sign Up changes

- Wording updated to *"I agree to WeGlow's Terms & Conditions and
  acknowledge the Privacy Policy."*, with "Terms & Conditions" and
  "Privacy Policy" as independently clickable, underlined,
  `DarkGreen`-colored spans inside one `AnnotatedString` (via
  `LinkAnnotation.Clickable`/`withLink`), each opening its own legal
  screen.
- The checkbox (`agreed`) and `formValid` gating logic are byte-for-byte
  unchanged — the links only call `onTermsConditionsClick`/
  `onPrivacyPolicyClick`, never touch `agreed`.
- `fullName`, `email`, `password`, `agreed` were changed from `remember`
  to `rememberSaveable` so navigating to a legal screen and pressing Back
  does not silently clear the form (Navigation Compose disposes a
  screen's plain `remember` state when it's no longer the active
  destination; `rememberSaveable` survives via the entry's own
  SavedStateRegistry).

## 5. Login changes

Added a visually secondary footer — *"Terms & Conditions  •  Privacy
Policy"*, `bodySmall`/`SoftGray`, underlined — below the existing "Create
an account" row. No agreement checkbox was added; this is access-only,
exactly as required. `email`/`password` were also switched to
`rememberSaveable` for the same reason as Sign Up, since this screen now
has an outbound navigation path it didn't have before.

## 6. Privacy Policy screen implementation

Native Compose screen (`PrivacyPolicyScreen.kt`): back-button + title
header (matching the identical pattern already used in
`AccountSettingsScreen`/`RecommendationsScreen`), a scrollable body built
from 14 `LegalSection(title, body)` calls holding the exact provided
content (light bullet/line-break formatting only — no wording or meaning
changed). No WebView, no browser, no second `NavHost`.

## 7. Terms & Conditions screen implementation

Same structure (`TermsConditionsScreen.kt`), 17 sections, same
faithful-content approach.

## 8. How signup consent remains enforced

`formValid` still requires `agreed == true` (unchanged expression), and
`agreed` is still only ever set by the checkbox's own `onCheckedChange`.
The new clickable text spans are structurally incapable of setting
`agreed` — they only invoke the two navigation callbacks.

## 9. Back-navigation behavior

Both legal screens' `onBack` is `{ navController.popBackStack() }` —
standard, already-established pattern. Combined with the `rememberSaveable`
change, pressing Back from either screen returns to Sign Up/Login/Account
Settings with prior state intact (form fields on Sign Up/Login; Account
Settings has no transient form state to lose).

## 10. Tests added/changed

New `DestinationTest.kt` (4 tests): confirms `Destination.PrivacyPolicy`/
`Destination.TermsConditions` exist with the expected routes, are distinct
from each other, and — more broadly — that every destination in the graph
still has a unique route string.

Compose UI/navigation-flow tests for items like "Account Settings can
navigate to both" or "existing 14+ behavior remains intact" were not
added: this project has no Robolectric or Compose UI test harness for
exercising full navigation flows (confirmed during an earlier
investigation this same session) — those are the kind of thing that would
need manual/instrumented verification. The 14+ minimum-age implementation
itself was not touched by this task.

## 11–14. Gradle verification results

```
./gradlew architectureCheck testDebugUnitTest lintDebug assembleDebug
```

| Task | Result |
|---|---|
| `architectureCheck` | ✅ BUILD SUCCESSFUL |
| `testDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| `lintDebug` | ✅ BUILD SUCCESSFUL |
| `assembleDebug` | ✅ BUILD SUCCESSFUL |

`DestinationTest`: 4/4 passed.

## 15. `git diff --stat`

```
 .../com/example/weglow/navigation/Destination.kt   |  2 +
 .../com/example/weglow/navigation/WeGlowApp.kt     | 14 +++++++
 .../weglow/ui/screens/AccountSettingsScreen.kt     | 32 ++++++++++++++++
 .../com/example/weglow/ui/screens/LoginScreen.kt   | 35 +++++++++++++++++-
 .../com/example/weglow/ui/screens/SignUpScreen.kt  | 43 +++++++++++++++++++---
 5 files changed, 119 insertions(+), 7 deletions(-)
```

## 16. `git status --short`

```
 M app/src/main/java/com/example/weglow/navigation/Destination.kt
 M app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt
 M app/src/main/java/com/example/weglow/ui/screens/AccountSettingsScreen.kt
 M app/src/main/java/com/example/weglow/ui/screens/LoginScreen.kt
 M app/src/main/java/com/example/weglow/ui/screens/SignUpScreen.kt
?? app/src/main/java/com/example/weglow/ui/components/LegalSection.kt
?? app/src/main/java/com/example/weglow/ui/screens/PrivacyPolicyScreen.kt
?? app/src/main/java/com/example/weglow/ui/screens/TermsConditionsScreen.kt
?? app/src/test/java/com/example/weglow/navigation/
```

Nothing staged, committed, or pushed.

## 17. Issues/legal-content inconsistencies discovered

None found in the provided content itself — it was transcribed faithfully
and cross-checked against the earlier data-processing investigation (no
forbidden claims were present in the source text, and none were
introduced). One implementation-only note: Section 2 of the Privacy Policy
("Information We Collect") groups three sub-topics (Account, Profile/
questionnaire, Skin analysis) under a single numbered section in the
source; this was rendered as one `LegalSection` with the three sub-labels
as plain lines within the body (rather than three separate numbered
sections), to stay faithful to the source's own numbering rather than
renumbering it — flagged purely as a formatting choice, not a content
change.
