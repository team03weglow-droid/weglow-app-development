const WEATHER_API_BASE_URL = "https://api.weatherapi.com/v1";
const REQUEST_TIMEOUT_MS = 10_000;

const jsonHeaders = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: jsonHeaders });
}

async function requireSupabaseUser(request: Request): Promise<Response | null> {
  const authorization = request.headers.get("authorization");
  const publishableKey = request.headers.get("apikey");
  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.replace(/\/$/, "");

  if (!authorization?.startsWith("Bearer ") || !publishableKey) {
    return jsonResponse({ error: "Sign in before requesting weather." }, 401);
  }
  if (!supabaseUrl) {
    console.error("SUPABASE_URL is unavailable in the Edge Function runtime.");
    return jsonResponse({ error: "Weather service is not configured." }, 503);
  }

  try {
    const response = await fetch(`${supabaseUrl}/auth/v1/user`, {
      headers: {
        authorization,
        apikey: publishableKey,
      },
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });

    if (!response.ok) {
      return jsonResponse({ error: "Your session is invalid or expired." }, 401);
    }
    return null;
  } catch (error) {
    console.error("Could not validate the Supabase session", error);
    return jsonResponse({ error: "Authentication is temporarily unavailable." }, 503);
  }
}

function parseCoordinate(
  url: URL,
  name: "lat" | "lon",
  minimum: number,
  maximum: number,
): number | null {
  const rawValue = url.searchParams.get(name);
  if (rawValue === null || rawValue.trim() === "") return null;

  const value = Number(rawValue);
  return Number.isFinite(value) && value >= minimum && value <= maximum
    ? value
    : null;
}

function isIsoDate(value: string | null): value is string {
  return value !== null && /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    !Number.isNaN(Date.parse(`${value}T00:00:00Z`));
}

Deno.serve(async (request: Request): Promise<Response> => {
  if (request.method !== "GET") {
    return jsonResponse({ error: "Use GET for this endpoint." }, 405);
  }

  const authenticationError = await requireSupabaseUser(request);
  if (authenticationError) return authenticationError;

  const weatherApiKey = Deno.env.get("WEATHER_API_KEY")?.trim();
  if (!weatherApiKey) {
    console.error("WEATHER_API_KEY has not been added to Supabase secrets.");
    return jsonResponse({ error: "Weather service is not configured." }, 503);
  }

  const requestUrl = new URL(request.url);
  const latitude = parseCoordinate(requestUrl, "lat", -90, 90);
  const longitude = parseCoordinate(requestUrl, "lon", -180, 180);
  if (latitude === null || longitude === null) {
    return jsonResponse(
      { error: "lat must be -90..90 and lon must be -180..180." },
      400,
    );
  }

  const endpoint = requestUrl.searchParams.get("endpoint") ?? "current";
  if (endpoint !== "current" && endpoint !== "history") {
    return jsonResponse({ error: "endpoint must be current or history." }, 400);
  }

  if (endpoint === "history") {
    const startDate = requestUrl.searchParams.get("dt");
    const endDate = requestUrl.searchParams.get("end_dt");
    if (!isIsoDate(startDate) || !isIsoDate(endDate)) {
      return jsonResponse({ error: "History requires valid dt and end_dt dates." }, 400);
    }

    const rangeDays = (
      Date.parse(`${endDate}T00:00:00Z`) - Date.parse(`${startDate}T00:00:00Z`)
    ) / 86_400_000;
    if (rangeDays < 0 || rangeDays > 30) {
      return jsonResponse({ error: "History range must be between 0 and 30 days." }, 400);
    }

    const historyUrl = new URL("https://api.open-meteo.com/v1/forecast");
    historyUrl.searchParams.set("latitude", String(latitude));
    historyUrl.searchParams.set("longitude", String(longitude));
    historyUrl.searchParams.set("daily", "uv_index_max");
    historyUrl.searchParams.set("start_date", startDate);
    historyUrl.searchParams.set("end_date", endDate);
    historyUrl.searchParams.set("timezone", "auto");

    try {
      const historyResponse = await fetch(historyUrl, {
        headers: { accept: "application/json" },
        signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
      });
      if (!historyResponse.ok) {
        console.error(`UV history provider returned HTTP ${historyResponse.status}`);
        return jsonResponse({ error: "UV history is temporarily unavailable." }, 502);
      }

      const payload = await historyResponse.json() as {
        daily?: { time?: unknown; uv_index_max?: unknown };
      };
      const dates = payload.daily?.time;
      const values = payload.daily?.uv_index_max;
      if (!Array.isArray(dates) || !Array.isArray(values) || dates.length !== values.length) {
        console.error("UV history provider returned an unexpected response.");
        return jsonResponse({ error: "UV history is temporarily unavailable." }, 502);
      }

      const forecastday = dates.map((date, index) => ({
        date,
        day: { uv: values[index] },
      })).filter((item) =>
        typeof item.date === "string" && typeof item.day.uv === "number"
      );
      return jsonResponse({ forecast: { forecastday } });
    } catch (error) {
      console.error("UV history request failed", error);
      return jsonResponse({ error: "UV history is temporarily unavailable." }, 503);
    }
  }

  const upstreamUrl = new URL(`${WEATHER_API_BASE_URL}/current.json`);
  upstreamUrl.searchParams.set("key", weatherApiKey);
  upstreamUrl.searchParams.set("q", `${latitude},${longitude}`);
  upstreamUrl.searchParams.set("aqi", "yes");

  try {
    const upstream = await fetch(upstreamUrl, {
      headers: { accept: "application/json" },
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });

    if (!upstream.ok) {
      // Do not return provider details that could disclose account information.
      console.error(`WeatherAPI returned HTTP ${upstream.status}`);
      const status = upstream.status === 400 ? 400 : 502;
      return jsonResponse({ error: "Weather data is temporarily unavailable." }, status);
    }

    return new Response(await upstream.text(), {
      status: 200,
      headers: {
        "content-type": "application/json; charset=utf-8",
        "cache-control": "private, max-age=600",
      },
    });
  } catch (error) {
    console.error("WeatherAPI request failed", error);
    return jsonResponse({ error: "Weather data is temporarily unavailable." }, 503);
  }
});
