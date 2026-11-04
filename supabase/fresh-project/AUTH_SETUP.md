# Authentication for the fresh WeGlow project

Email-signup regression found during manual acceptance: the app emitted SignedUp
and opened onboarding even when signup returned an unconfirmed user without a
session. The final profile save correctly rejected that unauthenticated state.
The client now returns to Login with a confirmation notice until a real session
exists. New signups send full_name in Auth metadata for later onboarding, and
confirmation callbacks are handled while Signup is visible. Confirmation and RLS
settings remain enabled. Existing unconfirmed test users should confirm their
original email, sign in, and repeat onboarding instead of signing up again.

Fix verification: all 279 JVM tests passed, including five new confirmation/name
regressions and the real SDK request-contract test with a scripted HTTP engine.
The no-session signup response never authorizes onboarding; confirmed sign-in
allows a successful final profile save. Debug build, architecture check, lint
and Android-test compilation passed. Updated WeGlow Dev APK installed on the
emulator without clearing data. Live confirmation/onboarding retest is pending.

Latest owner checkpoint: Google sign-in in the newly configured WeGlow Dev app
is reported working. This supersedes earlier pending-Google notes below. Email
confirmation/delivery, emailed-code recovery, profile persistence, private
Storage and release-build authentication remain unverified.

Destination: `nkrxygqtptvarntzxawk`.
The owner reported that setup/hairstyle import completed without SQL errors.
The owner supplied readiness results: 342 hairstyles, 0 products, 0 incomplete
hairstyle rows and all five readiness flags true (bucket, both routine RPCs,
deletion RPC and app-table RLS). SQL setup readiness is owner-verified; Auth,
image delivery and Storage API behavior remain unverified.
Android development/production configuration now targets this project using the
owner-supplied publishable key in ignored local properties. The development APK
was rebuilt and installed. Public Auth settings are reachable with that key and
confirm Google/email enabled and email confirmation required. Anonymous catalog
and profile reads are denied. Actual login, recovery and private Storage tests
remain pending; add `weglow-dev://login-callback` to test the development build.

Owner checkpoint: Google provider credentials and Supabase Site URL/redirect
allowlist were reported saved. This records configuration only; Google login
has not yet been tested with an Android build pointing at the new project.
Email/password settings, recovery template, SMTP and function deployment remain
pending verification.

Owner checkpoint: Gmail custom SMTP saved, template editing unlocked, and the
Reset Password recovery-code template reported saved. Actual delivery, recovery,
and sign-in against the new backend remain untested. Publishable API key and
destination Edge Function deployments were still needed at that checkpoint.
The key has since been configured locally; hosted function deployment remains
pending.

The owner reports that the email template page is entirely uneditable. Check
the new-project Free-plan/default-SMTP restriction below before asking them to
retry editing. SMTP configuration and project plan have not been confirmed.

The owner has only a Gmail address, without a custom domain. For initial
testing, Gmail SMTP can be configured using an eligible Google account and an
App Password after enabling 2-Step Verification. Use `smtp.gmail.com`, port 587
(STARTTLS), the full Gmail address for both username and From address, and the
generated App Password only in Supabase's SMTP password field. Never request or
store that password in chat/repository files. Sender name: `WeGlow`.
Verify actual delivery before treating setup as complete. Personal Gmail has
sending limits; reassess a dedicated transactional sender before public launch.
[Google App Passwords](https://support.google.com/accounts/answer/185833?hl=en),
[Gmail SMTP](https://developers.google.com/workspace/gmail/imap/imap-smtp).

## Verify the imported database

Run `verify-setup.sql` in the new project's SQL Editor. Expected:

- `hairstyle_count` matches the old export count and is greater than zero.
- `product_count` is zero, as requested.
- `incomplete_hairstyle_rows` is ideally zero; otherwise inspect real source data
  instead of inventing missing shape/gender values.
- All `*_ready` values are true.

This verifies database structure/counts, not provider configuration or image
delivery. Existing image URLs may still depend on the old project.

## Email/password and redirects

In the new project's Authentication settings, enable the Email provider and
review email confirmation/password settings. Keep confirmation enabled for
production. In URL Configuration add this exact app redirect to the allowed
redirect URLs:

```text
weglow://login-callback
```

The production manifest, Supabase client and MainActivity agree on this callback
and use PKCE. Development now uses `weglow-dev://login-callback` and staging uses
`weglow-staging://login-callback`; configure the relevant backend's allowlist
before testing those builds. See `docs/ENVIRONMENTS.md`. Avoid wildcard redirects.
Check the Site URL/fallback used by
confirmation emails; a leftover localhost URL must not be the production
confirmation landing page. Use the intended app deep link or an owned deployed
confirmation page, then verify the actual email confirmation flow on Android.
[Supabase redirect configuration](https://supabase.com/docs/guides/auth/redirect-urls).

## Google login

The current Android code uses browser-based `signInWith(Google)` through
Supabase, so configure the Web application OAuth client for that flow.

In the Google Auth Platform client, add this authorized redirect URI:

```text
https://nkrxygqtptvarntzxawk.supabase.co/auth/v1/callback
```

In Supabase's Google provider settings, enable Google and enter the matching
Client ID and Client Secret directly in that dashboard. Configure the Google
consent screen/audience and any test users appropriate to its publishing state.
The Google HTTPS callback goes in Google; `weglow://login-callback` goes in
Supabase's app redirect allowlist. Test that the browser returns to WeGlow and
onboarding stores the profile under that authenticated user's UUID.
[Google provider setup](https://supabase.com/docs/guides/auth/social-sign-in/auth-google?platform=android).

## Password recovery and email delivery

Configure email delivery **before attempting template edits** on a new Free-plan
project. Since 3 June 2026, new Free projects using Supabase's default email
provider have read-only Auth templates. Setting up custom SMTP unlocks template
customization without requiring a paid Supabase plan. This restriction does not
apply to paid plans or older Free projects. Verify the plan/SMTP state if the
page remains read-only after SMTP is configured.
[Supabase restriction announcement](https://supabase.com/changelog/46599-changes-to-email-template-customisation-on-free-tier).

In Authentication / Email Templates / Reset Password, use the contents of
`reset-password-email.html`. It includes `{{ .Token }}` because the existing app
asks for an emailed recovery code, verifies the recovery OTP and updates the
password. A link-only template does not match that UI.
[Template variables](https://supabase.com/docs/guides/auth/auth-email-templates).

Configure custom SMTP for real users and verify sender-domain ownership and
delivery. Supabase's default mail service restricts recipients to the project's
team; it is not sufficient for a public signup/recovery launch. Enter SMTP
credentials only in the provider/dashboard's credential fields.
[SMTP setup](https://supabase.com/docs/guides/auth/auth-smtp).

## Before application cutover

Verify fresh signup/confirmation, email login, Google login, recovery with a real
delivered code, logout and two-account ownership with the destination build.
The public/publishable key will be needed to point Android at this project.
Do not substitute a secret/service-role key. Weather/account deletion functions
still need deployment before their end-to-end checks can pass.
