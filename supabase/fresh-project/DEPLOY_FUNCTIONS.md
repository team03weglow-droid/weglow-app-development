# Destination Edge Function handoff

Destination: `nkrxygqtptvarntzxawk`. SQL setup does not deploy Edge Functions.
Weather function `smart-endpoint` was deployed to this destination on 2026-11-04
and is ACTIVE (version 1). Its `WEATHER_API_KEY` secret is present. An
unauthenticated request returned HTTP 401. On 2026-11-05, an authenticated
temporary-user request returned HTTP 200 with current UV, humidity and location;
an invalid session returned HTTP 401 and the test user was deleted. The separate
Open-Meteo history request returned 30 dates with 30 UV values. Phone UI refresh
still needs verification. `delete-account` and `chat` remain undeployed.

Weather credential repair (2026-11-05): the supplied OpenWeatherMap credential
was rejected by WeatherAPI.com (HTTP 401, provider code 2006), causing the proxy
to return HTTP 502 and preventing both Home readings and its graph from loading.
Replaced the destination WEATHER_API_KEY secret with the existing, verified
WeatherAPI.com credential from ignored local configuration. This function uses
WeatherAPI.com; OpenWeatherMap credentials are not interchangeable. No keys were
added to tracked files or the Android APK. Current UV can legitimately be zero
at night; the graph uses daily maximum UV values from Open-Meteo.

The current `supabase/config.toml` project_id is the local stack identifier;
it is not proof that the CLI is linked to the new account. Any ignored old CLI
link metadata may still refer to the prior project. Use an explicit destination:

```powershell
supabase login
supabase functions deploy smart-endpoint --project-ref nkrxygqtptvarntzxawk
supabase functions deploy delete-account --project-ref nkrxygqtptvarntzxawk
supabase functions deploy chat --project-ref nkrxygqtptvarntzxawk
```

These commands require a separately installed/authenticated Supabase CLI.
Run from this repository root so the shared helper imports are included. Deploy
to the separate staging reference first when it is available. Do not blindly
run db push/config push: the fresh SQL setup and legacy migration timestamps need
intentional CLI-history reconciliation.
[Official deployment guide](https://supabase.com/docs/guides/functions/deploy).

In the destination's Edge Function secrets, configure `WEATHER_API_KEY` directly
with the provider credential. Verify its built-in `SUPABASE_URL`,
`SUPABASE_ANON_KEY` and `SUPABASE_SERVICE_ROLE_KEY` belong to the destination and
are usable by these handlers. No Gemini key is needed by the retired chat stub.
Do not place server credentials in Android, chat, Git or command-line arguments.

The handlers use authenticated-user verification through Supabase Auth and the
service-role-only quota/deletion RPCs. Gateway JWT configuration remains enabled
in the current file; verify it with the new project's actual signing keys. Do
not remove handler authentication to get a failing request through the gateway.

Acceptance checks: unauthenticated weather/deletion requests are rejected;
authenticated weather works; the chat stub returns its retirement response;
deleting a disposable recently authenticated account removes its owned photos,
database records and Auth user; a second account cannot access its data.
These live checks require actual test accounts and remain pending.
