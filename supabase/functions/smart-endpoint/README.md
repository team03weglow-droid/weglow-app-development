# Weather Edge Function (`smart-endpoint`)

This function proxies WeatherAPI without exposing `WEATHER_API_KEY` to the
Android application or Git. It requires an authenticated Supabase user.

## Deploy

1. In the Supabase dashboard, open **Edge Functions > Secrets** and add a
   secret named `WEATHER_API_KEY` containing a WeatherAPI.com key.
   OpenWeatherMap keys belong to a different provider and will not work here.
2. Deploy `supabase/functions/smart-endpoint/index.ts` as a function named
   `smart-endpoint`.
   Keep JWT verification enabled.

With the Supabase CLI, after logging in and linking the project:

```powershell
supabase functions deploy smart-endpoint
```

Set the secret through the dashboard so the value does not enter terminal
history. Never put the real value in this directory.

## Request

```http
GET /functions/v1/smart-endpoint?lat=6.9271&lon=79.8612
apikey: <Supabase publishable key>
Authorization: Bearer <signed-in user's access token>
```

The response uses the same JSON shape as WeatherAPI's `current.json`, so the
existing Android JSON parsing can be reused. Android currently retrieves its
previous 30 daily maximum UV readings directly from Open-Meteo. Current UV is
provided by WeatherAPI.com and can legitimately be zero at night. A failed
history request does not discard the current reading.

The function also supports history for older clients through
`endpoint=history&dt=YYYY-MM-DD&end_dt=YYYY-MM-DD`, translating Open-Meteo daily
maximum UV values into the WeatherAPI-compatible response shape.

## Android weather location

Sign in to view weather. Home requests foreground location permission and uses
a current fix, with a bounded 12-second wait and a recent cached fix as backup.
If permission is denied, system Location is off, or no fix is available, a bundled
catalog selects the network country's default city, then the SIM country, then
the phone region. Sri Lanka uses Colombo; other entries use their first listed
capital. The city and fallback status are visible, with a Use current location
action. Both current weather and UV history use the same selected coordinates.
Unrecognized country signals produce an explanation instead of an unrelated city.
There is no background location tracking or runtime country-lookup API.
