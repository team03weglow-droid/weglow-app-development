# WeGlowApp.kt Merge Conflict Resolution

Report for resolving the single unresolved Git merge conflict in
`app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt` while
merging `origin/main` (teammate's chatbot/application work) with local
changes (the newly implemented Privacy Policy and Terms & Conditions
navigation). Only this one conflict was touched — no other file was
modified, nothing was staged, no commit was made, no push occurred, and
the merge was not aborted.

## Conflict location

`app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt`, inside the
`composable(Destination.AccountSettings.route)` block's
`AccountSettingsScreen(...)` call — the single conflict in this file.

## What the conflict actually was

Both sides supplied the exact same 8 pre-existing arguments with identical
values — it was a pure **formatting divergence**, not a logic conflict:

- **"ours"**: compact, one-argument-per-line style, plus our two new legal
  callbacks.
- **"theirs"** (teammate's incoming side): the same 8 arguments reformatted
  onto the expanded, blank-line-separated multi-line style already used
  everywhere else in their merged version of this file (e.g.
  `onLogout =\n    authViewModel::signOut` a few lines above) — but
  **without** the two legal callbacks, since that side predates their
  existence.

## What was preserved from each side

- **From "theirs" (teammate)**: the expanded multi-line formatting style
  for all 8 shared arguments — `onBack`, `displayName`, `email`, `gender`,
  `isUpdatingGender`, `genderError`, `onGenderSelected`,
  `onConsumeGenderError` — kept verbatim, matching the surrounding file's
  now-prevailing style.
- **From "ours" (local)**: the two new legal callbacks —
  `onPrivacyPolicyClick` and `onTermsConditionsClick`, each navigating via
  the existing `NavController` to `Destination.PrivacyPolicy`/
  `Destination.TermsConditions` with `launchSingleTop = true` — appended
  in the same multi-line block style for consistency, and the two
  `composable(Destination.PrivacyPolicy.route) { ... }` /
  `composable(Destination.TermsConditions.route) { ... }` blocks
  immediately after, which were already unconflicted and untouched.

Nothing else in the file was touched — everything else was already
auto-merged (teammate's chatbot/navigation and other changes throughout
`WeGlowApp.kt` are exactly as they arrived from the merge), and no other
file was modified.

## Resolved code

```kotlin
composable(Destination.AccountSettings.route) {

    LaunchedEffect(Unit) {
        profileViewModel.refresh()
    }

    AccountSettingsScreen(

        onBack = {
            navController.popBackStack()
        },

        displayName =
            profileState.displayName,

        email =
            profileState.email,

        gender =
            profileState.gender,

        isUpdatingGender =
            profileState.isUpdatingGender,

        genderError =
            profileState.genderError,

        onGenderSelected =
            profileViewModel::onGenderSelected,

        onConsumeGenderError =
            profileViewModel::consumeGenderError,

        onPrivacyPolicyClick = {
            navController.navigate(Destination.PrivacyPolicy.route) {
                launchSingleTop = true
            }
        },

        onTermsConditionsClick = {
            navController.navigate(Destination.TermsConditions.route) {
                launchSingleTop = true
            }
        },
    )
}

composable(Destination.PrivacyPolicy.route) {
    PrivacyPolicyScreen(onBack = { navController.popBackStack() })
}

composable(Destination.TermsConditions.route) {
    TermsConditionsScreen(onBack = { navController.popBackStack() })
}
```

## Verification

```
grep -n "^<<<<<<<\|^=======\|^>>>>>>>" app/src/main/java/com/example/weglow/navigation/WeGlowApp.kt
```

→ no matches. Zero conflict markers remain.

## State left for review

- `git status` still shows `UU` for this file — it is **not staged** (no
  `git add` was run).
- No commit, no push, no merge abort.
