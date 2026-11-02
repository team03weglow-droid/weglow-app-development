# WeGlow Play Store readiness

Working branch: `Making-the-app-ready-to-launch-in-playstore`

This checklist tracks release work. An implementation in the repository is not
proof that the production service is deployed, configured, or verified.

## 1. Chatbot removed for launch

Status: Android chat removed; retirement handler prepared. Live deployment pending.

- Home chat icon opens a coming-soon dialog on the same page.
- Chat screen, age prompt, voice controls, repository, API client, ViewModel,
  chat URL build configuration and chat-only Android dependencies removed.
- In-app privacy policy updated for this release.
- The chat Edge Function returns HTTP 410 without reading messages, accessing
  profiles or contacting Gemini. Deploy the stub to disable existing hosted code.
- Legacy local Python tools are outside the Android app and must not be exposed
  as a chat service for this release.

Remaining deployment and verification:
- [ ] Deploy the retired chat function to the correct Supabase project.
- [ ] Verify live requests cannot invoke Gemini; review legacy exposed services.
- [ ] Remove the Gemini secret if no other deployed service needs it.
- [ ] Test Home chat icon; OK, outside tap and Android Back dismiss the message
  without changing the page.
- [ ] Align store listing, public privacy policy and Data safety with this release.
- [ ] Review historical chat/provider retention if older builds were used.

Local verification:
- `assembleDebug --offline --no-daemon`: passed, including Kotlin compilation.
- Node Edge Function and shared security tests: 4 passed.
- `git diff --check`: passed.
- Android unit tests remain unverified: the online attempt stalled during
  preparation; offline retry could not resolve the uncached JVM ONNX runtime JAR.
- Dialog interaction/accessibility checks on a device remain pending.

## 2. Verify backend security and deletion

Access check: repository identifies `weglow-backend` (`edmlbdkvrtmpcwigrqtr`).
No connected Supabase tool or browser session was available. CLI access could not
be established: the npm launcher lacked its Windows binary and the explicit
binary download stalled. Production inspection and deployment remain unverified;
no live migrations, deletions, or authentication-setting changes were performed.

Prepared `supabase/tests/live-security-audit.sql` for a read-only metadata review.
It checks table existence/RLS, policies, grants, privileged RPC access, private
photo bucket configuration, columns and constraints without selecting user rows.
It does not replace two-account staging tests or real Auth/Storage deletion tests.

User-supplied constraint evidence (2 November 2026) confirms the visible
profile-to-Auth, scan-to-profile and product/cart relationships. The latest
screenshot is partial; it cannot establish whether unseen constraints exist or
whether the security migration is fully applied. Validated constraints do not
establish that live RLS policies or permissions are safe. Earlier conclusions
about missing constraints must remain unconfirmed until a complete audit.
The audit now returns all seven metadata groups in one JSON result to avoid
SQL Editor showing only the last group's result.

- [ ] Follow SECURITY_HARDENING.md deployment prerequisites in staging.
- [ ] Test two-account database and storage isolation.
- [ ] Test recovery, expired sessions, logout and failed requests.
- [ ] Test deletion of Auth, database and profile-photo records.
- [ ] Confirm actual retention, logs and backup expiration with providers.
- [ ] Test local-journal handling on multiple devices.

## 3. Future chatbot work (outside this release)

Resolve provider audience and data terms, reporting, safety, privacy, backend
access controls and testing before restoring chat. No release date is promised.
Existing on-device scan features remain separate.

## 4. Publish privacy and deletion resources

Owner inputs required: legal operator, launch countries, support contact, hosting
regions, provider plans, retention periods and public website destination.

- [ ] Public privacy webpage, consistent with in-app policy and production behaviour.
- [ ] Public account-deletion request page usable without reinstalling the app.
- [ ] Identity verification and a working deletion request process.
- [ ] Disclose actual deletion timing, justified retention and backup handling.
- [ ] Confirm age, on-device analysis and health disclosures for launch.

## 5. Clear content and software rights

- [ ] Product image and description permission/source register.
- [ ] Brand/logo usage and any affiliate or sponsorship disclosures.
- [ ] Model-weight, training-data and redistribution rights review.
- [ ] Dependency, font, icon and other asset licences and required notices.
- [ ] Weather/API plan permissions and attribution requirements.

For each asset record its source, rights holder, licence or written permission,
commercial/app-display rights, attribution, expiry and supporting evidence.

## 6. Prepare release configuration and user experience

- [x] Set application ID to `com.team03weglow.app`; Play registration remains pending.
- [ ] Configure protected release signing and Play App Signing.
- [ ] Build release AAB; verify API compatibility and native 16 KB page support.
- [ ] Test permissions granted/denied, approximate location and offline behaviour.
- [ ] Test scans, profile uploads, the coming-soon dialog, reminders and account flows on devices.
- [ ] Review health claims and recommendation limitations in app and listing.
- [ ] Review unfinished cart/checkout behaviour and truthful feature descriptions.

## 7. Complete Console and testing

- [ ] Resolve health-app classification and appropriate developer account type.
- [ ] Complete developer identity/organization verification.
- [ ] Prepare listing assets, support details and reviewer credentials.
- [ ] Complete Data safety from a verified production data inventory including SDKs.
- [ ] Complete Health apps, ads, audience and content-rating declarations.
- [ ] Enter privacy and deletion URLs; register package as required by Console.
- [ ] Run internal testing and applicable closed-testing/production-access process.
- [ ] Review pre-launch results and verify the submitted release bundle.

## 8. Repository release audit - 2 November 2026

Submission is blocked pending the following work. This is a repository review,
not a live backend or Play Console certification.

### Build blocker found in this review

Resolution work has started:

- Corrected all 32 JPEG drawables that used `.png` extensions by renaming them
  to `.jpg`, preserving their bytes and Android resource identifiers. All 38
  JPEG/PNG drawable signatures now match their extensions; no resource-name
  collisions were found. The original AAPT failures below are resolved.
- Release APK assembly now succeeds. The unsigned universal APK passes SDK
  `zipalign -c -P 16 4`; all 16 arm64-v8a/x86_64 native libraries have ELF LOAD
  alignment of at least 16 KB. Device runtime checks and verification of the
  final signed AAB remain pending.
- Backup rules now exclude all credential-protected, device-protected and
  app-specific external data domains from cloud backup and device transfer.
  `allowBackup=false` remains set. This extends the earlier journal-only rules
  to cover SDK sessions and other private data. Real OEM transfer tests remain
  pending; reinstalling/transferring should require sign-in and will not restore
  the local journal through these backup paths.
- Signing keystores and conventional local signing-property files are now
  ignored by Git. A release signing key has not been created or configured.
- `assembleRelease lintRelease --no-configuration-cache --no-daemon` passed
  after correcting the image extensions. Lint reported 0 errors and 115
  warnings; dependency-update suggestions, unused resources and other warnings
  remain. This does not establish device behavior or backend readiness.
- `bundleRelease lintRelease --offline --no-configuration-cache --no-daemon`
  then passed with the updated backup rules (0 lint errors, 115 warnings).
  Output: `app/build/outputs/bundle/release/app-release.aab` (134,877,676 bytes).
  `jarsigner -verify` confirms this bundle is unsigned; configure signing before
  uploading. The file size is not a measurement of Play's per-device download.

Backup configuration reference:
https://developer.android.com/identity/data/autobackup

Original failed attempt (before the resource corrections):

- `assembleRelease lintRelease --no-configuration-cache --no-daemon` failed at
  `mergeReleaseResources`: AAPT could not compile `home_figma_avatar.png`,
  `chamomile_night_cream.png`, `home_scalp.png`, `white_jasmine_facial_serum.png`,
  `home_hydration.png`, `matcha_cleansing_foam.png`, and `lavender_sleeping_mist.png`.
  Inspect and normalize the image formats, then repeat the release build and lint.
  Release lint did not complete; the previous debug lint report is stale and
  cannot establish the current lint result.
- The offline attempt failed because the Supabase release AAR was not cached.
  The online retry resolved dependencies and exposed the resource errors above.
- Four backend handler/security tests passed again during this review.

### App and release configuration to change or verify

- `app/build.gradle.kts`: application ID is now `com.team03weglow.app`; the Kotlin
  namespace remains `com.example.weglow`. Debug/release merged manifests and
  FileProvider authority reflect the new identity. Check Play registration and
  any dependent OAuth/provider configuration before submission.
- Optional upload-key signing now reads ignored root `keystore.properties`;
  `keystore.properties.example` contains no credentials. Android Studio can
  also supply credentials through its signing wizard. See `RELEASE_SIGNING.md`.
  Key creation, signed-output verification and Play App Signing remain pending.
  Build and validate a signed AAB
  with `bundleRelease` after fixing resources. `assembleRelease` alone is not
  evidence of a signed Play-ready bundle.
- Release minification is currently disabled. Consider R8/resource shrinking
  after verifying ONNX, LiteRT, serialization and Supabase in the optimized build.
  This is an optimization recommendation, not itself a submission requirement.
- Check the final native libraries and bundle for 16 KB page-size compatibility
  and test on a 16 KB device/emulator; dependency version numbers alone do not
  prove compatibility. ONNX Runtime, LiteRT and ML Kit bring native libraries.
- Current compile/target SDK is 37. Verify the installed SDK is a stable,
  Play-accepted platform at submission, and test its behavior changes.
- Verify release Supabase URL/publishable key are populated; current build
  configuration reads `local.properties` and otherwise emits empty values.
  Keep service-role and provider secrets server-side.
- Review Android 12+ device transfer of authentication/preferences. Manifest
  disables backup, but extraction rules explicitly exclude only the routine
  journal. Verify actual SDK storage and OEM behavior before claiming tokens
  cannot transfer.
- Review launcher icon rendering/adaptive and themed variants; the manifest
  currently uses `@drawable/weglow_logo` for both normal and round icons.
- Clean up lint findings and misleading image extensions; verify large-font,
  TalkBack, denied-permission, offline and small-screen behavior on devices.

### Operational and policy preparation, in priority order

1. Review the complete metadata audit; establish deployed RLS, storage and RPC
   permissions. Reconcile migration history, then stage necessary changes and
   run real two-account isolation, recovery and account-deletion tests.
2. Deploy and verify the retired chat endpoint and the required weather/deletion
   services. Check recovery email templates, OAuth configuration, billing limits
   and support procedures. A Git push does not deploy Supabase.
3. Publish a public HTML privacy policy and a functioning external account
   deletion request page. Confirm operator, contact, hosting regions, retention,
   backups and deletion timing; align those facts with the in-app policy.
4. Prepare Data safety from actual SDK/backend data flows: authentication,
   profile photos, saved scan results, location/environment requests and logs.
   Distinguish on-device scan images from uploaded profile photos.
5. Treat skin-condition analysis as requiring a health-policy assessment. Complete
   Health apps declarations, confirm developer account eligibility, and make
   store claims match the tested capabilities. Prepare the required non-medical
   disclaimer and professional-care advice for the applicable classification.
6. Align the current minimum age of 14 with launch-country requirements, actual
   onboarding and Console audience selections. Confirm asset/model/product
   commercial rights and required attribution before publishing.
7. Keep listing screenshots/descriptions consistent with unavailable chat,
   unfinished checkout and other coming-soon features. Prepare app icon, feature
   graphic, screenshots, support contact and reliable reviewer access.
8. Run internal testing and the applicable closed-test/production-access process,
   review Play pre-launch reports and re-test the actual signed release bundle.
   For personal accounts created after 13 November 2023, current guidance requires
   at least 12 opted-in testers continuously for 14 days before applying for
   production access; confirm which account requirements apply to this app.

Additional official references checked during this review:

- Health content: https://support.google.com/googleplay/android-developer/answer/16679511
- Health declaration: https://support.google.com/googleplay/android-developer/answer/14738291
- Account type: https://support.google.com/googleplay/android-developer/answer/13634885
- Personal account testing: https://support.google.com/googleplay/android-developer/answer/14151465

## Official references

- Gemini terms: https://ai.google.dev/gemini-api/terms
- User data: https://support.google.com/googleplay/android-developer/answer/10144311
- Account deletion: https://support.google.com/googleplay/android-developer/answer/13327111
- AI reporting: https://support.google.com/googleplay/android-developer/answer/14094294
- Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Developer accounts: https://support.google.com/googleplay/android-developer/answer/10788890
- Target API: https://support.google.com/googleplay/android-developer/answer/11926878
- Native page sizes: https://developer.android.com/guide/practices/page-sizes
- Intellectual property: https://support.google.com/googleplay/android-developer/answer/9888072

Recheck policies in Play Console before submission; requirements can change.
