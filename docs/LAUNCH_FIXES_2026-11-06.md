# Launch fixes and owner handoff — 2026-11-06

Work remains on `refining-the-app-for-playstore`. Existing uncommitted
accessibility improvements were preserved. This work has not been committed or
pushed and does not publish an app to Google Play.

## Implemented

- Product-independent daily routines: three morning steps and two evening steps
  using the user's own products. Catalog absence or failure no longer blocks a
  usable checklist. No fake catalog rows are inserted.
- An explicit `trackable` field distinguishes real product-independent tasks
  from historical unmatched product slots. Existing schedules are not relabeled
  or rewritten. These tasks use the existing authenticated schedule/completion
  RPCs and contribute to real Combined Progress when scan intervals qualify.
- Morning/evening tasks remain due at 8 AM/8 PM in the saved time zone. Future
  tasks cannot receive completion credit. Schedule revisions preserve history.
  An empty legacy schedule can be replaced by a new version starting now,
  because that legacy schedule had no trackable tasks.
- Applied `20261106000000_product_independent_routines.sql` to production project
  `nkrxygqtptvarntzxawk`; regenerated the fresh-project setup with the migration.
- Staging/release builds disable demonstration data and commerce entry points.
  Debug retains explicit demo mode. Release Home/Profile/scan results hide the
  unfinished product-shopping routes; release navigation has Home, Scan,
  Routines and Profile until an Insights design is selected. Inactive chatbot,
  Skin sync, Log SPF, checkout/order-history and skin-score placeholders are
  hidden in those builds. Real empty states remain available.
- UV notifications identify the city and explain a country-default reading.
- Deployed `delete-account` and the retired `chat` compatibility handler.

## Verification

- 299 JVM tests passed, including product-free task generation, the 65/100
  calculation and empty/unavailable catalog behavior.
- Debug APK and Android-test APK builds, architecture guard and debug lint
  passed. Lint reports 0 errors and 91 warnings; warnings remain follow-up work.
- Eight emulator UI tests passed, including 200% text-size accessibility checks,
  the product-independent checkbox callback and Home large-text regressions.
  The original emulator disconnected; the first attempt on its replacement had
  an instrumentation startup ANR during cold boot. A focused retry and then all
  eight tests passed after boot settled. Signed-release cold-start/performance
  testing on representative physical devices is still required.
- Isolated database checks passed for product-free completion, duplicate and
  unchecked states, legacy exclusion, future schedule changes, preserved
  snapshots, RLS, fresh setup and hairstyle-only import.
- Live disposable-account tests passed for schedule/completion persistence,
  repeated/uncheck interactions, two-account isolation, private Storage,
  recovery-token verification, password update/new-password login, and complete
  account deletion while another account remained intact. No real users were
  deleted; temporary users and uploaded test images were cleaned up.
- All 16 tested 64-bit native libraries passed ELF 16 KB alignment; APK ZIP
  alignment also passed. The available emulator used 4096-byte pages, so these
  checks do not establish execution on a 16 KB device.
- Production backend configuration passed validation. Staging validation failed
  because its backend is unconfigured. Upload-signing validation failed because
  upload credentials are not configured; no new signed release AAB was produced.

The database migration was applied explicitly through the SQL query API.
Reconcile the fresh project's migration history before a broad CLI `db push`;
do not rerun legacy setup migrations blindly.

## Needs owner input or separate release work

1. Configure a separate staging project and the documented staging URL/key.
2. Supply upload signing locally through Android Studio's signing wizard or
   ignored signing configuration, back up the key, and configure Play App Signing.
   Never send signing passwords or secret/service-role keys in chat.
3. Apply/verify the recovery email template at
   `supabase/fresh-project/reset-password-email.html` and allow the appropriate
   `weglow-dev://password-reset`, `weglow-staging://password-reset` and
   `weglow://password-reset` redirects in their respective projects. Complete a
   real delivered-email reset on the intended build. Token verification was
   tested without sending email; inbox delivery and email-link routing were not.
4. Provide the legal operator name, support contact, retention/deletion timing
   and hosting choice; publish accurate public privacy and deletion-request
   pages. Their public URLs are needed for the store listing and Data safety.
5. Confirm intended audience/18+ positioning, Health apps/Data safety answers,
   image/model/provider licenses and commercial usage plans. Model inference
   tests are not clinical accuracy validation; diverse photo/device testing and
   appropriate claims/disclosures remain necessary.
6. Decide the Insights page's content and navigation. It was not redesigned in
   this task. Custom routine editing/reminder times, manual city selection,
   support/feedback and export features also remain product work.
7. Finish manual TalkBack/Switch Access testing, 16 KB runtime testing, signed
   release testing, Play pre-launch checks, reviewer access and required closed
   testing/production access for this developer account.

Earlier checklist entries may describe historical states; this dated handoff and
the deployment handoff capture the work completed in this session.
