const TIMEOUT_MS = 15_000;

export class HttpError extends Error {
  status: number;
  constructor(status: number, message: string) { super(message); this.status = status; }
}

export function timedFetch(input: string | URL, init: RequestInit = {}): Promise<Response> {
  return fetch(input, { ...init, signal: AbortSignal.timeout(TIMEOUT_MS) });
}

export async function readJson(request: Request, limit = 64 * 1024): Promise<unknown> {
  const length = request.headers.get("content-length");
  if (length && Number(length) > limit) throw new HttpError(413, "Request is too large.");
  if (!request.headers.get("content-type")?.toLowerCase().startsWith("application/json")) {
    throw new HttpError(415, "Use application/json.");
  }
  const reader = request.body?.getReader();
  if (!reader) throw new HttpError(400, "A JSON body is required.");
  const chunks: Uint8Array[] = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.length;
      if (total > limit) {
        await reader.cancel();
        throw new HttpError(413, "Request is too large.");
      }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  try { return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes)); }
  catch { throw new HttpError(400, "Invalid JSON body."); }
}

export async function requireUser(request: Request): Promise<{ id: string; authorization: string; lastSignInAt: string | null }> {
  const authorization = request.headers.get("authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) throw new HttpError(401, "Sign in to continue.");
  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_ANON_KEY");
  if (!url?.startsWith("https://") || !key) throw new HttpError(503, "Service is not configured.");
  const response = await timedFetch(`${url}/auth/v1/user`, {
    headers: { Authorization: authorization, apikey: key },
  });
  if (!response.ok) throw new HttpError(response.status >= 500 ? 503 : 401, "Unable to verify your session.");
  const user = await response.json();
  if (typeof user?.id !== "string") throw new HttpError(401, "Unable to verify your session.");
  return { id: user.id, authorization, lastSignInAt: typeof user.last_sign_in_at === "string" ? user.last_sign_in_at : null };
}

export async function adminFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url?.startsWith("https://") || !key) throw new HttpError(503, "Service is not configured.");
  return timedFetch(`${url}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", apikey: key, Authorization: `Bearer ${key}`, ...init.headers },
  });
}

export async function withQuota<T>(userId: string, service: string, operation: () => Promise<T>): Promise<T> {
  const response = await adminFetch("/rest/v1/rpc/reserve_api_request", {
    method: "POST", body: JSON.stringify({ p_user_id: userId, p_service: service }),
  });
  if (!response.ok) throw new HttpError(503, "Service is temporarily unavailable.");
  const reservation = await response.json();
  if (reservation === null) throw new HttpError(429, "Usage limit reached. Please try again later.");
  if (typeof reservation !== "string") throw new HttpError(503, "Service is temporarily unavailable.");
  try { return await operation(); }
  finally {
    // A failed release expires automatically after 90 seconds. Never hide the original result.
    try {
      const released = await adminFetch("/rest/v1/rpc/release_api_request", {
        method: "POST", body: JSON.stringify({ p_request_id: reservation }),
      });
      if (!released.ok) console.error("Quota release failed", released.status);
    } catch { console.error("Quota release unavailable"); }
  }
}
