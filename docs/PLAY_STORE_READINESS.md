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

- [ ] Select permanent application ID before initial publication.
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
