# Phase 10 — Security + Performance Hardening Report

## 1. Overall verdict

**PASS**

No secrets are exposed, no service_role key exists client-side, RLS/storage policies already
match the expected model exactly, and every build/test verification task passes. A concrete,
scoped set of findings was fixed: one fake-user-identity issue ("Team 03"), one fake-avatar
issue, one fake-routine-data issue on Home, repeated unnecessary Supabase catalog queries on
navigation, an ONNX session being rebuilt on every scan, an internal-diagnostic-message leak
into user-facing error text, unbounded scan-photo cache accumulation, and a cleartext-traffic
gap on API 24–27. Nothing architectural was rebuilt or redesigned.

---

## 2. Security audit findings

| Severity | Issue | File/Component | Remediation |
|---|---|---|---|
| Medium | Internal diagnostic exception text (mentions "Check the products SELECT policy in Supabase" and raw column names) propagated verbatim into user-facing `errorMessage` state | `data/repository/SupabaseCatalogRepository.kt` | Replaced both `check()` messages with a generic, safe user message; developer-facing intent kept as a code comment. |
| Low | `usesCleartextTraffic` not explicitly declared; defaults to *allowed* on API 24–27 (minSdk is 24) | `AndroidManifest.xml` | Added `android:usesCleartextTraffic="false"` explicitly. Supabase and Google OAuth are HTTPS-only, so this has no functional effect except closing the gap on older devices. |
| Low | Scan photos (privacy-sensitive) written to app cache (`weglow_scan_*.jpg`) were never deleted, accumulating indefinitely across sessions | `feature/scan/ScanViewModel.kt` | Added ownership-scoped cleanup: the previous scan-capture cache file is deleted when replaced by a new one or when the scan flow is cleared (e.g. on logout). Never touches a `content://` gallery-picked photo. |
| Informational | Release build type has `optimization { enable = false }` (R8/shrinking off) | `app/build.gradle.kts` | Not changed — no secret is embedded beyond the public/RLS-protected anon key, and this looks like a deliberate choice for demo/build stability (explicitly an audited concern in this phase). Flagged only; changing it risks the exact "release/demo stability" this phase is meant to protect, with no verified security benefit given RLS is the real boundary. |

No injection, path traversal, exported-component, or privilege-escalation issues were found.

---

## 3. Secrets/configuration audit

- **No secrets are exposed.** Repository-wide search for Supabase URLs, JWT-shaped strings, `service_role`, API keys, passwords, and tokens in `.kt`, `.xml`, `.gradle.kts`, `.json`, `.properties`, and `.md` files found only clearly-labeled placeholders (`local.properties.example`'s `YOUR_PROJECT.supabase.co` / `YOUR_SUPABASE_PUBLISHABLE_KEY`, and a Python backend test file using `https://example.supabase.co` / `test-key`).
- **No `service_role` usage exists anywhere in the Android client.** `SupabaseClientProvider` and `AppConfig` only ever reference `BuildConfig.SUPABASE_PUBLISHABLE_KEY` (the anon/publishable key, safe to ship in a client app whose real boundary is RLS).
- **Local config is correctly gitignored and was never committed.** `.gitignore` lists `local.properties`; `git log --all --full-history -- local.properties` returns nothing; the file exists on disk with real local values but has never entered Git history. The build pulls config via `local.properties` → Gradle property → `BuildConfig`, exactly the expected safe architecture (`app/build.gradle.kts`'s `phaseOneConfig()`).
- Values themselves were not printed anywhere in this audit or report, per instructions.

---

## 4. Android component security

- `MainActivity` is `exported="true"` — required (not a weakness) because it declares the `LAUNCHER` and OAuth `VIEW`/`BROWSABLE` intent filters; Android mandates an explicit `exported` value for any activity with intent filters on API 31+.
- The Google/Supabase OAuth deep link (`weglow://login-callback`) is scoped to an exact `scheme`+`host` pair, not a wildcard path — safe from arbitrary-routing abuse. `MainActivity.onCreate` hands the incoming intent straight to `SupabaseClientProvider.client.handleDeeplinks(intent)`, which is the Supabase Auth SDK's own validated handler; nothing in the app trusts unvalidated intent extras. Left untouched, as instructed.
- `FileProvider` is `exported="false"`, `grantUriPermissions="true"`, and scoped via `file_paths.xml` to exactly one cache sub-directory (`profile-camera/`) — no broad filesystem exposure. This predates Phase 10 (Phase 6 hardening) and was verified, not changed.
- `allowBackup="false"` — Auto Backup is fully disabled, so profile/session data cannot leak via `adb backup` or cloud backup.
- Permissions are minimal and justified: `INTERNET` (Supabase/OAuth) and `CAMERA` (skin scan), with the camera hardware feature marked `required="false"` so cameraless devices can still install. No storage permissions are requested; the system photo picker and a narrowly-scoped `FileProvider` are used instead, exactly as the phase brief prefers.
- Added `android:usesCleartextTraffic="false"` (see §2).

---

## 5. Authentication/session security

- `SupabaseAuthRepository` never logs, persists, or otherwise stores a plaintext password — it is passed directly into the Supabase Auth SDK call and dropped.
- No fake authentication path exists; every sign-in/sign-up/Google flow goes through `client.auth`.
- Logout (`signOut()`) triggers a real Supabase session termination; `WeGlowApp.kt`'s `SignedOut` handler pops the entire back stack to the login screen and calls `scanViewModel.clear()`, which (as of this phase) also deletes any leftover scan-capture cache file — session/state teardown is now also privacy-conscious.
- Protected flows are gated correctly: `AuthViewModel.determineDestination()` only routes to `HOME` when both an active session and `hasCompletedOnboarding` are true; every other case routes to `LOGIN` or `ONBOARDING`.
- `AuthViewModel` does surface `error.message` from the Supabase Auth SDK directly to the login/signup UI (e.g. "Invalid login credentials"). These are the SDK's own standard auth-error strings (not raw stack traces, internal URLs, or schema details), so this was judged acceptable and left unchanged — unlike the catalog repository's messages (§2), it does not reveal backend implementation details.

---

## 6. Supabase/RLS/storage assumptions

Verified against the actual migration files in `supabase/migrations/` (not assumed):

- **`profiles`**: RLS enabled; `create policy "Users can read/insert/update their own profile"` all constrain to the authenticated user's own row. No delete policy exists, so deletes are implicitly denied by default-deny RLS. Matches expectation exactly.
- **`profile-images` storage bucket**: `public = false`; four `storage.objects` policies (select/insert/update/delete) all require `(storage.foldername(name))[1] = auth.uid()::text` — a user can only touch objects inside their own folder. Matches expectation exactly.
- **`products`**: `20260911020000_harden_products_privileges.sql` already explicitly revokes all privileges from `anon`, revokes `INSERT/UPDATE/DELETE/TRUNCATE/REFERENCES/TRIGGER` from `authenticated`, and grants only `SELECT` to `authenticated`. Matches expectation exactly.
- **No `service_role` credential appears anywhere in the Android client** (§3).
- **No new migration was created.** Everything Part 4 asked to verify was already correctly implemented in prior phases; inventing a migration where none was needed would have violated "never invent the existing schema."

---

## 7. Skin scan privacy findings

**Acne scan photos never leave the device.** Traced the complete path: CameraX capture or system photo picker → a local `Uri` (either `content://` from the picker, or a `file://` cache file this app wrote) → `ScanPhotoDecoder.decode()` (pure in-process bitmap decoding, bounded to a 1600px max dimension, EXIF-corrected) → `LocalAcneScanRepository.analyze()` runs the bundled ONNX model **entirely on-device** → an `AcneScanResult` with only numeric detections (label, confidence, normalized box coordinates). No HTTP client, upload call, or server endpoint exists anywhere in this path. No analytics/tracking was added or found.

Findings and fixes:
- Camera-captured scan photos (`weglow_scan_<timestamp>.jpg`) were written to the app's private cache and **never deleted**, accumulating indefinitely (a real "unnecessary persistence" finding). **Fixed**: `ScanViewModel` now deletes its own previous capture file when replaced by a new one or when the scan flow is cleared, without ever touching a gallery-picked (`content://`) photo.
- No image bytes, file paths, or scan results are logged anywhere (confirmed by a repo-wide search — see §8).
- `AcneScanResult` contains no unnecessary sensitive data beyond what the UI needs (bounding boxes + labels + the model version/confidence threshold used).

---

## 8. Logging/error-handling findings

- **Zero `Log.d/i/w/e/v`, `println`, or `printStackTrace` calls exist anywhere in `app/src/main/java`** — confirmed by a repository-wide search. There is no logging channel that could leak emails, tokens, user IDs, image paths, or profile data, because there is no logging at all. No new logging was added (consistent with the codebase's existing zero-logging convention, and adding a first `Log` call would itself be a new pattern not otherwise used).
- **Fixed**: `SupabaseCatalogRepository.products()` had two `check()` failure messages that would reach the end user verbatim and explicitly named a Supabase RLS policy and dumped raw column names from the products table — a genuine "backend internals in the UI" leak (§2). Replaced with a generic "Products are temporarily unavailable." message.
- Reviewed but left unchanged: `AuthViewModel`/`ProfileViewModel`/`OnboardingViewModel` forward `error.message` from Supabase Auth SDK failures directly to the UI. These are the SDK's own standard, user-appropriate auth error strings, not internal diagnostics — judged safe.

---

## 9. Performance findings

**Catalog (Discover/Routines/Recommendations):**
- `DiscoverViewModel.loadProducts()` and `RoutineViewModel.load()` were re-triggered by a `LaunchedEffect(Unit)` every time their tab was revisited (Compose Navigation disposes and recreates the destination's composition on each visit), re-querying Supabase for the full 500+ product catalog every time. **Fixed**: both now skip a redundant fetch once a successful result is already held, while a failed load still retries via the existing "Try again" action. `RecommendationViewModel.load(scanConcerns)` was deliberately **left unchanged** — its input legitimately varies per call (profile-only vs. scan-based), so caching would have caused stale/incorrect results, a correctness regression the phase brief explicitly guards against (Part 14).
- `RecommendationEngine`/`RoutineBuilder` are pure, allocation-light functions over already-loaded lists — no repeated expensive work found beyond the fetch itself.
- Discover's client-side filtering is already correctly memoized with `remember(products, searchQuery, selectedCategory, minimumPrice, maximumPrice)`; filtering/search behavior is unchanged.
- `LazyColumn`/`LazyRow` item keys were already stable (real product/recommendation IDs) in Discover and Recommendations; no change needed.

**Coil/images:** `WeGlowProductImage` (already introduced for the prior blank-image fix) always calls `AsyncImage` with `placeholder`/`error`/`fallback` wired to the same WeGlow logo and sanitizes blank URLs — verified this remains the single, consistent path for Discover, Recommendations, and Routines. Coil compares the (stable) URL string between recompositions, so requests are not unnecessarily re-issued.

**CameraX:** `CameraCaptureScreen`'s `DisposableEffect(Unit) { onDispose { cameraProvider?.unbindAll() } }` (Phase 8 hardening) was verified intact — the camera is released when leaving the capture screen, with no leak of the `ProcessCameraProvider`.

**Bitmap decoding:** `ScanPhotoDecoder` was verified intact — both the API 28+ (`ImageDecoder`) and legacy (`BitmapFactory` + manual `inSampleSize`) paths bound the decoded dimension to ~1600px, preventing OOM on huge gallery images; EXIF orientation is corrected; intermediate bitmaps are recycled.

**ONNX Runtime:** the acne-detection `OrtSession` was being **fully recreated on every single scan** (mmap the model asset, rebuild and re-optimize the inference graph), even though the model is immutable for the app's lifetime. **Fixed**: the session (and its validated input shape) is now built once, lazily, and reused for every subsequent scan in the same app session; only the per-scan tensor is created and closed per call. Preprocessing, thresholds, labels, NMS, and the model graph itself are byte-for-byte unchanged — verified by keeping the exact same shape-check, tensor creation, and `YoloPostProcessor.decode()` call. If session construction ever fails (e.g. a corrupted install), `lazy` retries on the next call, preserving prior retry behavior. Trade-off (documented in code): the session's native memory now stays resident for the app's session lifetime instead of being freed after each scan — the standard, intended trade for materially faster repeat scans.

**Compose:** reviewed `collectAsState()` usage app-wide; it is not lifecycle-aware (`collectAsStateWithLifecycle` would require adding a new Gradle dependency not currently present). Given this app's simple single-Activity structure and that no measurable problem was observed, this was **not changed** — flagged as a non-blocking, low-value-for-risk item rather than added speculatively.

---

## 10. Hardcoded/fake production data findings

**Yes, "Team 03" existed** as a hardcoded greeting name on the production Routines screen ("Good Morning, Team 03" / "Good Evening, Team 03"), displayed as if it were the authenticated user's real name. **Fixed**: `RoutinesScreen` now accepts a real `displayName: String?` sourced from `ProfileViewModel` (the same repository/ViewModel architecture already used by Home and Profile), and falls back to neutral copy ("Good Morning" / "Good Evening", no name) when none is available — never a placeholder person's name.

Two further fake-production-data issues were found and fixed in the same pass:
- **Fake avatar image**: `DiscoverScreen` and `RoutinesScreen` both rendered a static drawable named `avatar_rukman` in their header as if it were the current user's real profile picture, instead of the real photo already available via `ProfileViewModel` (Home and Profile screens already did this correctly). **Fixed**: both now use the existing shared `ProfileAvatar`/`rememberDecodedBitmap` components with the real profile image bytes, matching Home/Profile.
- **Fake "Morning Routine" card on Home**: a hardcoded list of four specific product names ("Gentle Foam Cleanser", "Niacinamide 10% + Zinc", "Moisture Surge 100H", "SPF Daily Moisturizer") with fabricated "done" checkmarks was shown as the user's real routine. **Fixed**: the card now sources its steps from the real `RoutineViewModel`/`RoutineBuilder` plan (the same Phase 9 architecture the dedicated Routines screen uses), showing real matched product names and category labels, with a local (non-persisted) "done today" toggle — never inventing a product.

**Knowingly left unfixed and flagged (see §14):** Home's "skin score" card (a specific `74`, `+3`, and Hydration/Clarity/Texture percentages of `82%`/`68%`/`71%`) has no real underlying data source anywhere in the app. Building a genuine scoring algorithm would be a new feature requiring a product decision on what real signal to use — explicitly out of this phase's authorized scope ("Do NOT introduce fake functionality," "Do NOT redesign the UI," "Do NOT change product behavior unless required to fix a verified issue"). Removing or restyling this card would itself be a UI redesign. It is called out here rather than silently left in place.

No other hardcoded user-identity or fabricated personal data was found. The "AI Insights" cards (generic tips like "Hydration Low" / "SPF Reminder") and the "For You" article card are static educational content, not personal data presented as the user's own — judged not misleading in the same way.

---

## 11. Files changed

| File | Why |
|---|---|
| `AndroidManifest.xml` | Explicit `usesCleartextTraffic="false"` (§2, §4). |
| `data/repository/SupabaseCatalogRepository.kt` | Removed internal RLS/column-name detail from user-facing error messages (§2, §8). |
| `data/repository/LocalAcneScanRepository.kt` | ONNX session built once and reused instead of rebuilt per scan; no change to preprocessing/thresholds/labels/NMS (§9). |
| `feature/discover/DiscoverViewModel.kt` | Skip redundant catalog re-fetch when already loaded; retry still works after failure (§9). |
| `feature/routine/RoutineViewModel.kt` | Same re-fetch guard as Discover (§9). |
| `feature/scan/ScanViewModel.kt` | Deletes its own leftover scan-capture cache file on replace/clear; added a small pure, testable ownership check (§7). |
| `ui/screens/RoutinesScreen.kt` | Real `displayName` greeting (removes "Team 03"); real profile avatar (removes `avatar_rukman`) (§10). |
| `ui/screens/DiscoverScreen.kt` | Real profile avatar in the header (removes `avatar_rukman`) (§10). |
| `ui/screens/HomeScreen.kt` | "Morning Routine" card now renders the real `RoutineViewModel` plan instead of four hardcoded product names (§10). |
| `navigation/WeGlowApp.kt` | Wires `profileViewModel.refresh()` + real profile image/name into Discover and Routines; wires `routineViewModel.load()` + real plan into Home. |
| `app/src/test/.../DiscoverViewModelTest.kt` | Tests for the new no-redundant-refetch / retry-after-failure behavior. |
| `app/src/test/.../RoutineViewModelTest.kt` | Same, for `RoutineViewModel`. |
| `app/src/test/.../ScanViewModelTest.kt` | Tests for the new scan-cache-file ownership check. |

No changes were made to `AppContainer`, domain models/interfaces, `RecommendationEngine`, `RoutineBuilder`, `RecommendationViewModel`, navigation destinations, or any Supabase migration.

---

## 12. Tests added/updated

- `DiscoverViewModelTest`: `loadProducts_whenAlreadyLoadedSuccessfully_doesNotRefetch`, `loadProducts_afterAFailure_retriesOnNextCall` (`FakeCatalogRepository` now counts calls).
- `RoutineViewModelTest`: `load_whenPlanAlreadyBuilt_doesNotRefetchCatalog` (same call-counting fake).
- `ScanViewModelTest`: new `ScanCacheFileOwnershipTest` class — `ownScanCaptureFileName_isRecognized`, `unrelatedFileName_isNotTreatedAsOwned` (proves cleanup can only ever target this app's own `weglow_scan_*` files, never a gallery photo).

No UI/Compose tests were added for the avatar/greeting/routine-card changes, consistent with the rest of the codebase (no screen in this project has a Compose UI test); these were verified by code review and a full rebuild instead.

---

## 13. Verification results

| Task | Result |
|---|---|
| `./gradlew architectureCheck` | **PASS** |
| `./gradlew compileDebugKotlin` | **PASS** |
| `./gradlew testDebugUnitTest` | **PASS** — 107/107 tests, 0 failures |
| `./gradlew lintDebug` | **PASS** — no new findings; all findings are pre-existing (unused drawable resources, missing icon densities, outdated Gradle dependency versions, one pre-existing `AutoboxingStateCreation` and one pre-existing `RedundantLabel`, none touched by this phase) |
| `./gradlew assembleDebug` | **PASS** |

---

## 14. Remaining non-blocking issues

- Home's skin-score card (`74`, `+3`, Hydration/Clarity/Texture percentages) is fabricated with no real data source and was intentionally left in place rather than either inventing a scoring algorithm (out of scope) or redesigning the card (explicitly disallowed). Recommend a dedicated future phase to decide a real signal (e.g. derived from scan history) or to reframe the copy.
- `collectAsState()` is used app-wide instead of a lifecycle-aware collector; not changed because it would require a new Gradle dependency and no measurable problem was observed in this app's structure.
- Release build type has R8/shrinking disabled (`optimization { enable = false }`); flagged as informational only, not changed (§2).
- Pre-existing lint informational items (unused drawable resources, missing icon density variants, outdated Gradle dependency versions, one `RedundantLabel` on the activity's `android:label`) were not touched — cosmetic, unrelated to this phase's scope.

---

## 15. Teammate-owned/in-progress areas intentionally untouched

- The face-shape/hairstyle flow (`ScanMode.HAIRSTYLE`, `InMemoryHairstyleRepository`, `HairstyleViewModel`, `HairstyleResultsScreen`) remains exactly as found — still an explicitly simulated placeholder pending the teammate's real model. No fake face-shape inference was added or altered.
- The `backend/` Python project (a separate face-shape/export pipeline) was inspected only far enough to confirm it contains no real secrets and is unrelated to the Android app's runtime; nothing in it was modified.

---

## 16. Is Phase 10 safe to commit and merge?

**YES.**
