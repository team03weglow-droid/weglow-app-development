# WeGlow: Remaining Supabase Setup for Forgot Password

Verified against the implemented Kotlin recovery flow and implementation report.

**The correct redirect URL is `weglow://password-reset`. The email template below matches the app’s recovery-token handling.**

The Android screens, recovery callback, token verification, and password update are already implemented. Private Supabase Dashboard settings have not been inspected, so their current configuration and live email delivery remain unverified.

## Dashboard Configuration

**Step 1:** Open the [Supabase Dashboard](https://supabase.com/dashboard), sign in, and select the project your WeGlow app connects to.

**Step 2:** Check email authentication.

Go to **Authentication → Sign In / Providers → Email**.

- Ensure **Enable Email provider** is **ON**.
- If you change it, click **Save**.
- Leave **Confirm email** and other existing settings unchanged; this feature does not require changing them.

Email authentication must be enabled for email/password authentication. [Supabase documentation](https://supabase.com/docs/guides/auth/passwords)

**Step 3:** Add the recovery redirect URL.

Go to **Authentication → URL Configuration → Redirect URLs**.

Click **Add URL**, enter exactly:

```text
weglow://password-reset
```

Click **Save URLs** or **Save**, as displayed.

Use no trailing slash or query parameters. Keep existing redirect URLs, including the Google-login callback. The existing **Site URL** does not need changing for this flow because Kotlin supplies the recovery redirect explicitly. [Redirect configuration](https://supabase.com/docs/guides/auth/redirect-urls)

**Step 4:** Open the reset-password email template.

Go to **Authentication → Emails → Templates → Reset password**. Some Dashboard layouts show **Email Templates** directly under Authentication.

**Step 5:** Replace the template’s message body with this HTML, then click **Save**.

```html
<h2>Reset your WeGlow password</h2>

<p>Tap the link below on the Android device where WeGlow is installed.</p>

<p>
  <a href="{{ .RedirectTo }}?token_hash={{ .TokenHash }}&amp;type=recovery">
    Reset password
  </a>
</p>

<p>If you did not request this, you can ignore this email.</p>
```

The subject can remain unchanged.

This template is compatible with the implementation because:

- `{{ .RedirectTo }}` supplies `weglow://password-reset`.
- `{{ .TokenHash }}` supplies the token hash that Kotlin verifies through Supabase.
- `type=recovery` matches the app’s strict recovery-link parser.
- `&amp;` correctly represents `&` inside HTML.

**Use this link instead of `{{ .ConfirmationURL }}`.** The app expects the token hash directly and performs verification itself. Do not substitute `{{ .Token }}` or add extra URL parameters. [Supported template variables](https://supabase.com/docs/guides/auth/auth-email-templates)

**Step 6:** Check email delivery—custom SMTP is required for ordinary users.

Go to **Authentication → Emails → SMTP Settings**.

- If working custom SMTP is already configured, leave it unchanged.
- For delivery to ordinary users, enable **Custom SMTP** and enter your email provider’s:
  - Verified sender email address.
  - Sender name: `WeGlow`.
  - SMTP host, port, username, and password.
- Click **Save**.

The SMTP values must come from your email provider; there is no universal value to paste. Keep SMTP credentials only in the Dashboard.

Supabase’s default sender supports limited testing with organization-team email addresses. It does **not** deliver to arbitrary registered users and, according to the documentation checked for this report, allows only **two emails per hour**. Custom SMTP is necessary beyond that limited testing case. [SMTP requirements](https://supabase.com/docs/guides/auth/auth-smtp)

**Step 7:** Leave other settings unchanged unless a specific issue requires attention.

- **Password requirements:** No mandatory Dashboard change. WeGlow requires at least eight characters and a number; any stricter existing Supabase requirements also apply.
- **Rate limits:** No mandatory increase. Under **Authentication → Rate Limits**, existing limits can remain. By default, wait at least 60 seconds between reset requests for the same email. [Rate limits](https://supabase.com/docs/guides/auth/rate-limits)
- **Optional production recommendation:** Disable authentication-email click tracking in your SMTP provider if it rewrites links. [Production guidance](https://supabase.com/docs/guides/deployment/going-into-prod)
- **If CAPTCHA is already enabled:** This implementation does not submit a CAPTCHA token. That is a compatibility issue requiring separate review; do not disable existing security settings blindly.

No database migrations, RLS changes, Edge Functions, or Google-provider changes are required.

## Final Test

**Step 8:** Test using the installed Android app.

1. Tap **Forgot Password?** → the email-entry screen opens.
2. Enter a registered email and tap **Send Reset Link** → the neutral confirmation appears.
3. Receive the email → check spam if necessary.
4. Open its link on the Android device → WeGlow validates the link, then shows the new-password fields.
5. Enter matching passwords and tap **Save New Password** → success confirmation appears.
6. Tap **Back to login** and sign in with the new password → login should succeed; the previous password should fail.

Also reopen the used link: it should be rejected. Email delivery, current Dashboard settings, and this complete live workflow remain unverified.

## What I Must Do in Supabase Before Forgot Password Will Work.

- [ ] Confirm the Email provider is enabled.
- [ ] Add and save `weglow://password-reset` in Redirect URLs.
- [ ] Paste and save the Reset password email template above.
- [ ] Configure working custom SMTP for recipients outside the organization team.
- [ ] Test the complete recovery flow on Android.
