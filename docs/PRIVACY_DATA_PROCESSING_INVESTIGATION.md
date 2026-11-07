# WeGlow Privacy/Data-Processing Investigation

Read-only investigation to support drafting a real Privacy Policy and Terms
& Conditions for the WeGlow Android application. This report describes
**only what the current codebase actually does** — nothing was implemented,
no files were modified, no database was touched, and nothing was staged,
committed, or pushed.

## 1. Account Data

- **Signup**: full name, email, password (`SignUpScreen.kt`). Password
  requires ≥8 characters and at least one digit
  (`AuthViewModel.signUp`). A checkbox "I agree to WEGLOW's Terms and
  Privacy Policy" **is required** to enable the Create Account button
  (`formValid` includes `agreed`) — but currently the text is plain,
  unlinked `Text` with no document behind it.
- **Login**: email + password, or **Google OAuth**
  (`SupabaseAuthRepository.signInWithGoogle`, via Supabase Auth's `Google`
  provider and a `weglow://login-callback` deep link declared in
  `AndroidManifest.xml`).
- **Auth backend**: Supabase Auth exclusively
  (`io.github.jan.supabase:auth`). Password hashing/storage is handled
  entirely by Supabase Auth — never touched or stored in the Android app.
- **Identifier used everywhere**: `auth.users.id` (a UUID), exposed as
  `AuthRepository.currentUserId()`. This is the same id as `profiles.id`
  (FK) and is the app-wide account identifier.
- **Display name**: read from the Google/identity-provider's own user
  metadata (`currentUserDisplayName()`), never entered by the app itself
  for Google sign-in.

## 2. Profile Data

Table `public.profiles` (confirmed via
`supabase/migrations/20260909000000_create_profiles.sql` + 3 follow-on
migrations), read/written by `SupabaseProfileRepository`/`ProfileRow.kt`:

| Column | Collected via | Notes |
|---|---|---|
| `id` | Auth | = `auth.users.id` |
| `full_name` | Signup | |
| `age_range` | Onboarding (`AgeSelectionScreen`) | One of: Under 14, 14–24, 25–35, 36–45, 46–59, 60+ |
| `skin_type` | Onboarding (`SkinTypeScreen`) | Dry & Tight / Combination / Oily / Sensitive / Normal-Balanced; skippable |
| `gender` | Onboarding (`GenderSelectionScreen`), editable later in Account Settings | Female / Male / Other/Prefer not to say |
| `is_skin_sensitive` | Onboarding (`SkinSensitivityScreen`) | Boolean yes/no |
| `face_shape` | Written automatically after a hairstyle scan | Text label (e.g. "Oval") |
| `profile_image_url` | Profile photo upload | Storage object **path**, not the image itself (see §3) |
| `onboarding_completed` | Internal flag | Boolean |
| `created_at`/`updated_at` | Automatic | |
| `date_of_birth` | **Column exists in the live schema but is never read or written anywhere in the current Android code.** | Vestigial — flag as a gap (§Q) |

No allergy field, no address, no phone number field exists anywhere in the
schema or Android code.

## 3. Camera / Gallery

- **Camera permission**: `android.permission.CAMERA`, requested at the
  point of use in `ProfileScreen.kt` (profile photo) and `ScanScreen.kt`
  (skin/hairstyle scan) via `ActivityResultContracts.RequestPermission`.
- **Gallery**: uses the modern Android **Photo Picker**
  (`ActivityResultContracts.PickVisualMedia`) exclusively — **no**
  `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE` permission is declared or
  requested (confirmed: absent from `AndroidManifest.xml`).
- **Profile photos**: uploaded to **Supabase Storage**, private bucket
  `profile-images`, path `<userId>/<timestamp>.<ext>`
  (`SupabaseProfileImageRepository.kt`). RLS confines each user to their
  own folder. Replacing a photo deletes the previous storage object. EXIF
  orientation is read and normalized before upload; the corrected bytes
  are what's uploaded (not the raw original file).
- **Scan photos (acne / hairstyle)**: **never uploaded anywhere.** A
  camera capture is written to a private app-cache file
  (`weglow_scan_<timestamp>.jpg` in `context.cacheDir`); a gallery pick is
  read directly from the OS-provided `content://` URI without the app
  making its own copy. Both paths only exist to be decoded into an
  in-memory `Bitmap` for on-device inference (`ScanPhotoDecoder`), and the
  bitmap is recycled immediately after.
- **Retention of the local scan-cache file**: deleted when the user
  replaces it with a new capture, or on sign-out
  (`ScanViewModel.clear()`). There is **no explicit "delete immediately
  after analysis" step** — the cached file can persist in the app's
  private cache between those events (subject also to normal Android OS
  cache eviction, which the app does not control).
- **Face detection for validation** (ML Kit, on-device): explicitly
  documented in code as "No image or face data leaves the device" — used
  only to confirm exactly one clear face is present before analysis;
  produces no persisted output.

## 4. AI / Scan Data

- **Acne/skin scan**: `LocalAcneScanRepository` — a bundled ONNX model,
  run 100% on-device via ONNX Runtime. Code comment: *"Runs the bundled
  model entirely on the device. No HTTP client, upload, or server
  fallback."* Input: the decoded scan photo. Output: bounding-box
  detections per concern label + confidence.
- **Hairstyle/face-shape scan**: `LocalHairstyleRepository` — a bundled
  TensorFlow Lite (LiteRT) model, also fully on-device. Output: a
  face-shape label + confidence.
- **What's persisted from acne scans**: table `Scan-profiles` (not created
  by a migration in this repo — provisioned directly on the hosted
  project, per code comments), written by `SupabaseScanProfileRepository`.
  Columns actually written: `date_time`, `user_profile_id`, and **integer
  counts** for `dark_spots`, `open_pores`, `black_heads`, `acne_scars`,
  `papules`, `whiteheads`, `nodules`, `cysts`, `pustules`, `freckles`.
  **No image, no image URL, no bounding-box coordinates are persisted** —
  only aggregate counts per concern.
- **What's persisted from hairstyle scans**: the detected `face_shape`
  string is written back to `profiles.face_shape` (overwriting the
  previous value — no history is kept). Hairstyle *recommendations*
  themselves come from a separate `hairstyles` catalog table (read-only to
  the app), filtered server-side by the saved face shape and the user's
  **current** profile gender; nothing scan-specific is written there.
- Recommendation-engine computation (skincare routine/product matching) is
  pure local Kotlin logic (`RecommendationEngine`/`RoutineBuilder`) — no
  network calls, confirmed by absence of any HTTP client in that file.

## 5. Health/Skin-Related Information

Actually supported by the current implementation:
- `skin_type`, `is_skin_sensitive` (profile/onboarding, self-reported)
- `face_shape` (profile, derived from on-device scan)
- Per-scan detected-concern **counts**: dark spots, open pores, black
  heads, acne scars, papules, whiteheads, nodules, cysts, pustules,
  freckles (from `Scan-profiles`)
- **No allergy field exists anywhere.** No medication, no
  skin-condition-diagnosis field, no dermatologist-record field.

## 6. Location

- Permissions: `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`
  (foreground only — comment in the manifest explicitly notes background
  location is **not** requested).
- Obtained via `AndroidLocationProvider` (Google Play Services
  `FusedLocationProviderClient`, `PRIORITY_BALANCED_POWER_ACCURACY`,
  falling back to a last-known location only if ≤10 minutes old).
- **Not stored** in Supabase or any database — used only in-memory to
  fetch environment data, and cached in-memory in
  `WeatherApiEnvironmentRepository` for up to 20 minutes or until the
  device moves ~7 km, to avoid redundant requests. No location table
  exists.
- **Weather/UV/humidity/air-quality path**: Android app → Supabase Edge
  Function `smart-endpoint` (authenticated via the user's Supabase session
  token) → the Edge Function calls **WeatherAPI.com** (server-side; the
  API key lives only in Supabase secrets, never in the app) for current
  conditions, and calls **Open-Meteo** (`api.open-meteo.com`) for UV
  history.
- **Open-Meteo is also called directly from the Android app itself**
  (`WeatherApiEnvironmentRepository.fetchUvHistory`) for the same
  UV-history data, sending only latitude/longitude — no auth token, no
  other user data.
- A local (on-device, not push) notification is shown when UV is high,
  gated by `POST_NOTIFICATIONS` permission (Android 13+).

## 7. Product / Shopping Data

- **Products catalog** (`products` table): read-only to the app; not user
  data.
- **Saved Products** (`saved_products` table, manually provisioned,
  RLS-scoped to `profile_id = auth.uid()`): stores `profile_id` +
  `product_no` (the catalog's real `products."No"` key) + `created_at`. No
  product snapshot is stored — display data is re-joined live from
  `products`.
- **Cart** (`cart_items` table, same ownership pattern): stores
  `profile_id` + `product_no` + `quantity` (+ `updated_at` via trigger).
  Same live-join display approach; no price snapshot is stored, so
  displayed prices always reflect the current catalog.
- **Orders**: **do not exist.** No `orders`/`order_items` table, no
  checkout, no payment integration anywhere in the codebase. "Order
  History" is a static, non-functional "Coming soon" label in
  `ProfileScreen.kt`. Confirmed — do not describe order/payment data
  collection in the privacy policy.

## 8. Third-Party Services

| Service | What WeGlow sends it |
|---|---|
| **Supabase** (Auth, Postgrest, Storage) | Email/password or Google OAuth token (auth); all profile/scan-count/saved-product/cart rows (database); profile photo bytes (storage) |
| **Google Sign-In / OAuth** | Standard OAuth handshake for the account the user chooses to sign in with |
| **WeatherAPI.com** | Latitude/longitude only, via the Supabase Edge Function proxy — WeGlow's own users are never identified to WeatherAPI beyond coordinates for that one request |
| **Open-Meteo** (`api.open-meteo.com`) | Latitude/longitude only (called both from the Edge Function and directly from the Android app for UV history) |
| **Google ML Kit Face Detection** | Runs on-device; per its own on-device design, no image/face data is sent off-device by this library as used here |
| **Coil** (image loading) | Loads product/profile images over HTTPS from Supabase Storage/catalog URLs; also maintains its own local disk/memory image cache |

No analytics SDK, no crash-reporting SDK, no advertising SDK, and no
Firebase product of any kind appears in `build.gradle.kts` or
`gradle/libs.versions.toml` — confirmed by direct inspection of the
dependency list.

## 9. Device Permissions

(from `AndroidManifest.xml`)

| Permission | Feature |
|---|---|
| `INTERNET` | All Supabase/API network access |
| `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` | Environment/UV/weather feature (foreground only) |
| `CAMERA` | Profile photo capture, skin/hairstyle scan capture |
| `POST_NOTIFICATIONS` | Local high-UV alert |
| `android.hardware.camera` (`<uses-feature>`, `required=false`) | Declares camera as optional so cameraless devices can still install |

No storage/media permission is declared (Photo Picker is used instead).

## 10. Data Storage

**Supabase tables actually used by the app**: `profiles`, `products`
(read-only), `hairstyles` (read-only), `Scan-profiles`, `saved_products`,
`cart_items`.

**Supabase Storage**: one private bucket, `profile-images`.

**Locally on device**:
- Supabase Auth session/token, persisted by the Supabase Auth SDK's own
  built-in local session storage (standard SDK behavior; internal storage
  mechanism not further enumerable from this codebase without inspecting
  the SDK's own source).
- Scan-capture cache files (`weglow_scan_*.jpg`) in the app's private
  cache directory, per §3's retention behavior.
- A private cache subdirectory for in-flight camera-capture files used
  only to hand a photo to the system camera app (`profile-camera`),
  cleaned up immediately after each capture attempt.
- Coil's own on-device image cache for remotely loaded images.
- Standard Compose/Android saved-instance-state (e.g. in-progress form
  field values) — transient, not a persistent data store.

No local database (no Room, no SQLite file) exists in this project.

## 11. Data Deletion / Account Deletion

Confirmed present:
- **Logout** (`AuthViewModel.signOut`) — clears the session.
- **Saved-product removal** — `SavedProductRepository.removeSavedProduct`.
- **Cart-item removal** — `CartRepository.removeFromCart` (also automatic
  on decrementing quantity to 0).
- **Profile-photo replacement** — uploading a new photo deletes the
  previous storage object. There is **no explicit "remove photo, leave
  none" action** — only replace.

Confirmed **absent** (do not claim these exist):
- No account-deletion feature anywhere in the Android code (no call to
  Supabase's user-deletion API, no confirmatory UI).
- No standalone "delete my profile data" action distinct from account
  deletion.
- No scan-history deletion (there is also no scan-history *list* UI at all
  currently — scans write to `Scan-profiles` but nothing in the app reads
  that table back to display a history).

## 12. Children / Age

- `AgeSelectionScreen` offers **"Under 14"** as a selectable onboarding
  option, alongside 14–24, 25–35, 36–45, 46–59, 60+.
- There is **no minimum-age gate, no blocking logic, and no
  parental-consent flow** anywhere in onboarding, auth, or navigation
  code. Selecting "Under 14" proceeds through onboarding and account
  creation exactly like any other age band.
- This is a **significant compliance gap** to flag before publishing
  (COPPA/GDPR-K and Google Play Families Policy implications) — see §L/§Q.

## 13. Medical / Wellness Boundary

Exact existing wording found in the app:
- `RecommendationsScreen.kt`: *"Recommendations are for general skincare
  guidance and are not medical advice."*
- `ScanResultScreen.kt` (appears twice): *"Automated predictions can be
  incorrect. This is not a medical diagnosis."* / *"...These results are
  not a medical diagnosis."*

No mention of dermatologist involvement, no treatment claims, no
diagnostic claims anywhere else in the UI text found.

## 14. Marketing / Communication

- **No marketing email** sending exists (Supabase Auth may send its own
  transactional emails, e.g. confirmation — that is Supabase project
  configuration, not app code, and cannot be confirmed from this
  repository).
- **No push-notification service** (no FCM, no remote push) — the only
  notification (`UvAlertNotifier`) is generated and shown entirely
  on-device from data the app already fetched, never server-triggered.
- **No advertising SDK, no ad network, no data sharing with
  advertisers.**
- **No analytics/tracking SDK** of any kind (confirmed via dependency
  inspection).

## 15. Security

Visible, implemented protections:
- **Supabase Auth** for credential handling (passwords never touch app
  code in plaintext beyond the in-memory field before the SDK call).
- **Row Level Security (RLS)** enforced on every user-data table confirmed
  in migrations/prior investigation (`profiles`, `saved_products`,
  `cart_items`), scoped to `auth.uid()`; `products`/`hairstyles` are
  read-only to authenticated users.
- **Storage RLS**: `profile-images` bucket policies confine each user to
  their own folder path.
- **Secrets**: Supabase URL/publishable key loaded from
  `local.properties`/Gradle properties into `BuildConfig` (not hardcoded
  in source); the WeatherAPI key lives only in Supabase Edge Function
  secrets, never shipped in the app.
- **Edge Function** (`smart-endpoint`) requires a valid Supabase bearer
  token before proxying any weather request, and does not forward a
  caller-supplied API key.
- **Cleartext traffic disabled** (`android:usesCleartextTraffic="false"`).

This is a report of what is implemented, not a completeness or audit
claim.

## Final Structured Summary

**A. Personal data collected**: full name, email, password (via Supabase
Auth), gender, age range, Google identity metadata (if used), account
UUID.

**B. Sensitive/skin-related data**: skin type, skin sensitivity (boolean),
face shape, per-scan acne-concern counts (10 categories, listed in §5). No
allergy data exists.

**C. Images/biometric-like processing**: profile photos are uploaded and
stored (Supabase Storage, private bucket); scan photos (acne/hairstyle)
are processed only transiently, on-device, and never uploaded; ML Kit face
detection runs on-device for validation only, producing no persisted
output. Only aggregate concern *counts* and a face-shape *label* are ever
persisted — not the images themselves.

**D. Location processing**: foreground-only coarse/fine location, used
transiently for weather/UV lookups via a Supabase Edge Function
(WeatherAPI.com) and direct Open-Meteo calls; not stored in any database.

**E. Shopping data**: saved products and cart contents (both keyed by
catalog `product_no` and `profile_id`), no price/product snapshots,
live-joined for display. No orders/payments exist.

**F. Data stored in Supabase**: `profiles`, `Scan-profiles` (concern
counts only), `saved_products`, `cart_items`, plus read-only
`products`/`hairstyles`; Storage bucket `profile-images`.

**G. Data stored locally**: Supabase Auth session (via SDK's own
storage), transient scan-capture cache files, transient camera-capture
temp files, Coil's image cache, Compose saved-instance-state.

**H. Third-party services**: Supabase (auth/db/storage), Google Sign-In,
WeatherAPI.com (via proxy), Open-Meteo (direct + proxy), Google ML Kit
(on-device only). No analytics/ads/crash SDKs.

**I. Android permissions**: INTERNET, ACCESS_COARSE_LOCATION,
ACCESS_FINE_LOCATION, CAMERA, POST_NOTIFICATIONS.

**J. Data sharing/network transfers**: Supabase (all app data); WeatherAPI.com
and Open-Meteo (coordinates only, no identity); no advertiser sharing.

**K. Account/data deletion capabilities**: logout, saved-product removal,
cart-item removal, profile-photo replacement. **No account deletion, no
standalone data-deletion request flow, no scan-history deletion exist.**

**L. Children's/minor handling**: "Under 14" is a selectable onboarding
age band with **no age gate and no parental-consent mechanism** anywhere
in the code.

**M. Medical/wellness functionality**: two existing disclaimers (quoted
verbatim in §13) stating recommendations/results are not medical advice
or diagnosis. No dermatologist involvement, no treatment claims.

**N. Marketing/analytics/tracking**: none implemented — no ads, no
analytics SDK, no push notification service, no marketing email code.

**O. Security mechanisms**: Supabase Auth, RLS on all user-data tables,
Storage RLS, secrets kept out of the app bundle where possible,
authenticated Edge Function, cleartext traffic disabled. (Not a
completeness or audit claim.)

**P. Cannot be confirmed from the code**:
- Whether Supabase Auth email confirmation is enabled (a Supabase project
  setting, not visible in this repo).
- What transactional emails Supabase itself may send.
- Exact internal storage location/format the Supabase Auth SDK uses for
  session persistence.
- Whether `date_of_birth` is populated by any process outside this
  Android codebase (e.g., a manual/admin process) even though the app
  never writes it.
- Any server-side Supabase configuration (backups, log retention, staff
  access controls) — entirely outside this repository's visibility.

**Q. Privacy-policy risks/gaps to address before publishing**:
1. **No minimum age / parental consent for "Under 14."** This is the
   highest-priority legal gap given the app collects sensitive skin/
   health-adjacent data and photos.
2. **No account-deletion mechanism**, which many privacy regimes (GDPR
   right to erasure, CCPA, Play Store data-safety requirements) expect the
   app itself to provide or clearly explain how to request.
3. **Signup's "Terms and Privacy Policy" checkbox links to nothing** —
   it's unlinked text; there is currently no document for it to point to
   (this task is presumably step one toward fixing that).
4. **`date_of_birth` column exists but is unused** — decide whether to
   formally support/remove it, since an unused-but-present column can
   create confusing or inaccurate legal-document scope if not addressed.
5. **Scan-cache retention is not deterministic** — cached scan photos can
   persist locally longer than users might expect (until replaced or
   logout), which the policy should describe accurately rather than claim
   "deleted immediately after analysis."
6. **Face detection and skin-image processing both touch
   biometric-adjacent categories** (face geometry, skin condition) that
   some jurisdictions regulate more strictly — worth explicit legal review
   even though processing is on-device and not persisted as images.
7. **Google Sign-In metadata handling** should be described (what fields
   are read from the provider) even though minimal.

**R. Terms & Conditions topics required by actual functionality**:
- Account eligibility/age requirements (once decided — currently
  unenforced, see Q1).
- Description of AI-generated skin/hairstyle analysis being automated, not
  medical, and results not guaranteed accurate (mirroring §13's existing
  in-app language, formalized).
- Camera/photo usage terms (capture, on-device processing, non-retention
  of scan images beyond local cache).
- Profile photo storage and user responsibility for content uploaded.
- Saved Products/Cart functionality description, and explicit statement
  that checkout/ordering/payment is **not currently available**.
- Third-party weather-data attribution (WeatherAPI.com, Open-Meteo) if
  required by their own terms of use.
- Account termination/logout behavior, and current absence of
  self-service account deletion (with whatever manual process, if any,
  the business intends to offer).
- Acceptable use / no medical-diagnosis reliance disclaimer, consistent
  with existing in-app wording.

---

Investigation complete. No files were modified, no database was touched,
and nothing was staged, committed, or pushed.
