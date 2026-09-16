# Weather Edge Function (`smart-endpoint`)

This function proxies WeatherAPI without exposing `WEATHER_API_KEY` to the
Android application or Git. It requires an authenticated Supabase user.

## Deploy

1. In the Supabase dashboard, open **Edge Functions > Secrets** and add a
   secret named `WEATHER_API_KEY` containing the real WeatherAPI key.
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
existing Android JSON parsing can be reused. The graph's history request uses
`endpoint=history&dt=YYYY-MM-DD&end_dt=YYYY-MM-DD`. The function retrieves real
daily maximum UV history from Open-Meteo and translates it to the existing
WeatherAPI-compatible graph response without changing the graph UI.
