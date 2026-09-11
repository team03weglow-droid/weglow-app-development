# Phase 11 — Manual Device Test Matrix

This matrix covers everything in this phase that cannot be truthfully verified by a JVM unit
test: real Supabase network behavior, real camera hardware, real on-device gallery/file pickers,
real navigation/back-stack behavior on a running Activity, and real ONNX inference on-device.

**Actual Result** and **Status** are intentionally left blank. Claude did not run these on an
emulator or device and must not mark any of them PASS. A human tester fills these in.

Status values to use: `PASS`, `FAIL`, `BLOCKED`, `NOT RUN`.

| ID | Area | Precondition | Steps | Expected Result | Actual Result | Status | Notes |
|----|------|--------------|-------|------------------|----------------|--------|-------|
| A1 | Fresh install | App data cleared / fresh install | Launch the app | Login screen is shown after the loading animation; no crash | | | |
| B1 | Signup | Signed out | Tap "Create an account", enter a new email + name + password (≥8 chars, 1 digit), submit | Onboarding (Age Selection) appears; no error | | | |
| B2 | Signup validation | Signup screen | Enter a password with no digit or <8 chars | Inline/error message rejects it; no account created | | | |
| B3 | Onboarding completion | Mid-signup | Complete Age → Skin Type (or Skip) → Gender → Sensitivity | Home screen appears after WelcomeIntro; onboarding not repeated | | | |
| C1 | Persistence — session | Completed account from B1–B3 | Close the app fully, reopen | App goes straight to Home (no login, no onboarding) | | | |
| C2 | Persistence — profile name | Same account | Open Profile tab | Real full name is shown (not "Team 03" or any placeholder) | | | |
| D1 | Profile picture — camera | Profile tab | Tap avatar → Take Photo → grant camera permission → capture | New photo appears as the avatar; upload succeeds | | | |
| D2 | Profile picture — persistence | After D1 | Restart the app, open Profile | Same photo still shown (loaded from Supabase Storage, not local-only) | | | |
| D3 | Profile picture — gallery | Profile tab | Tap avatar → Choose From Gallery → pick an image | Photo uploads and replaces the avatar; old object is not left orphaned (spot check Storage bucket if accessible) | | | |
| D4 | Profile picture — permission denied | Profile tab, camera permission previously denied | Tap avatar → Take Photo → deny permission | Non-error notice ("Camera access is off...") shown; gallery option still available; no crash | | | |
| E1 | Discover — real catalog | Discover tab | Open tab | Real Supabase products load (not placeholders); images load or fall back to the WeGlow logo, never a blank box | | | |
| E2 | Discover — search | Discover tab loaded | Type a known product/brand keyword | List filters to matching products only | | | |
| E3 | Discover — category | Discover tab loaded | Tap a category chip (e.g. "Serums") | List filters to that category only | | | |
| E4 | Discover — price filter | Discover tab loaded | Open price filter, enter a min/max, apply | Only products within range remain; "Price filter on" label shown; Reset clears it | | | |
| E5 | Discover — no network | Airplane mode before opening Discover | Open Discover tab | Graceful error with "Try again"; no crash, no infinite spinner | | | |
| F1 | Camera acne scan | Scan tab | Choose "Skin Scan" → grant camera → capture a real face photo → Analyze | Real detections (or "No concerns detected") appear in Scan Results within a reasonable time; no crash | | | |
| F2 | Camera scan — permission denied | Scan tab, camera permission denied | Choose "Skin Scan", deny permission | "Grant camera access" / "Choose from gallery" fallback screen shown; no crash | | | |
| G1 | Gallery acne scan | Scan tab | Choose "Skin Scan" → "Choose from gallery" (or gallery fallback) → pick a photo → Analyze | Real detections appear, drawn as boxes correctly positioned over the photo (check both portrait and landscape source photos) | | | |
| H1 | Scan → Recommendations | After F1/G1 succeeds | On Scan Results, tap "View Recommendations" | Recommendations load; summary text says "…and concerns detected in your recent scan" (or "no concerns" if none); listed reasons reference the actual detected labels | | | |
| I1 | Profile-only recommendations | Home tab, no scan performed this session (or after backgrounding past a scan) | Tap "Recommended for You" from Home | Recommendations load using only profile signals; summary text says "Based on your profile" — must NOT silently reuse a previous scan's concerns | | | |
| J1 | Routines — morning | Routines tab | View Morning toggle | Real catalog products shown for Cleanser/Moisturizer/Sunscreen (or an explicit "No suitable product found" for any missing step — never an invented product) | | | |
| J2 | Routines — evening | Routines tab | Switch to Evening toggle | Real products for Cleanser/Treatment/Moisturizer, same missing-step rule as J1 | | | |
| K1 | Logout | Signed in, on Profile tab | Tap "Log Out" | Returns to Login screen | | | |
| K2 | Logout — back stack | Immediately after K1 | Press system Back | App does NOT return to Home/Profile/any authenticated screen; either exits or stays on Login | | | |
| L1 | Login persistence | After K1 | Log back in with the same completed account | Goes directly to Home; onboarding is NOT repeated | | | |
| M1 | No internet — Discover | Airplane mode | Open Discover | Graceful error, retry available, no crash | | | |
| M2 | No internet — Profile | Airplane mode | Open Profile tab (cold, before any cache) | Graceful loading/error state, no crash, no fake name/image shown | | | |
| M3 | No internet — auth | Airplane mode, signed out | Attempt login | Clear error message (not a raw network stack trace); no fake "signed in" state | | | |
| M4 | Recover from no internet | After M1–M3 | Restore network, tap Retry / retry action | Real data loads normally | | | |
| N1 | Repeated scan — stale result | Scan tab | Analyze photo A, wait for a result, then select photo B without leaving the flow (Retry/replace path) | Photo A's result/detections are not shown as belonging to photo B; analyzing B produces a fresh, independent result | | | |
| O1 | Navigation stress | Signed in, Home | Repeatedly switch Home ↔ Discover ↔ Scan ↔ Routines ↔ Profile via bottom nav, 15+ times | No crash, no duplicate stacked screens, back button behaves predictably | | | |
| O2 | Background/foreground | Any authenticated screen | Press Home (background app), reopen from recents | App resumes on the same screen with the same state; no crash, no unexpected re-login | | | |
| P1 | Skin score card — no fabricated claim | Home tab, Profile tab | Inspect the "Skin Score" / "THIS WEEK" cards | Cards show neutral/pending copy ("--", "Complete a skin scan to see your results here") — never a specific invented number or "improving" claim | | | |
| P2 | Discover featured card wording | Discover tab, any account | Inspect the top featured-product section | Heading reads "Featured Products" (not tied to a specific skin type); badge reads "Featured" (no fabricated match percentage) | | | |
| P3 | Team 03 reconciliation — current build only | **Uninstall any existing WeGlow build, then install the current `phase-11-testing` build fresh** | Sign in (or complete signup) with a real name, open Routines and Profile | Routines greeting reads "Good Morning/Evening, `<real full name>`" (or the plain "Good Morning"/"Good Evening" with no name if none is set) — "Team 03" must NEVER appear anywhere in the app | | | |
| Q1 | Routines date selector — independent per-date completion | Routines tab, a plan has loaded | (a) Select a date (e.g. Tue), mark 1–2 steps done. (b) Select a different date (e.g. Wed) without marking anything. (c) Select the original date (Tue) again | After (a): steps show checked on Tue. After (b): Wed shows the SAME routine products (expected — product template doesn't change per day) but with **all steps unchecked** (this is the fix — completion must NOT carry over from Tue). After (c): Tue's previously-checked steps are checked again; toggle the Morning/Evening switch on any date and confirm Morning and Evening completion never affect each other | | | |

## Notes for the tester

- Tests F1/G1/H1/N1 require the real bundled ONNX model and a real or simulated face photo;
  results will vary by photo and are not deterministic, so judge PASS/FAIL by "no crash, output is
  plausible and internally consistent," not by an exact expected label.
- Tests M1–M4 assume the emulator/device network toggle actually cuts Supabase reachability
  (some emulators keep loopback/host networking alive under "airplane mode" — verify a real
  request fails before asserting the graceful-failure behavior).
- P1/P2 verify the Phase 11 fake-data fixes (see the Test Report) actually render as intended on
  a real device/emulator, since Compose UI text cannot be asserted by the existing JVM unit tests.
- P3 exists because a "Team 03" sighting was reported on an emulator during Phase 11, but current
  source has zero occurrences of that string — the working theory is a stale, pre-Phase-10 APK
  was still installed. P3 must be run against a freshly installed **current** build to close this
  out; do not reuse an already-installed app.
- Q1 verifies the Defect 5 fix (Routines date selector previously shared completion state across
  every date). Completion state is intentionally **session-only** — it is not expected to survive
  closing and reopening the app, or even leaving the Routines tab and coming back, so judge Q1
  strictly within one continuous visit to the Routines screen.
