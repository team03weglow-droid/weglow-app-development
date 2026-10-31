import test from "node:test";
import assert from "node:assert/strict";

let handler: (request: Request) => Promise<Response>;
const values: Record<string, string> = {
  SUPABASE_URL: "https://project.test", SUPABASE_ANON_KEY: "public",
  SUPABASE_SERVICE_ROLE_KEY: "server-secret", GEMINI_API_KEY: "gemini-secret", WEATHER_API_KEY: "weather-secret",
};
(globalThis as any).Deno = { env: { get: (key: string) => values[key] }, serve: (callback: typeof handler) => { handler = callback; } };
const response = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status });
const request = (body: unknown, authorized = true) => new Request("https://project.test/functions/v1/chat/message", {
  method: "POST", headers: { "content-type": "application/json", ...(authorized ? { Authorization: "Bearer user-token" } : {}) }, body: JSON.stringify(body),
});

test("chat uses verified identity, minimizes context, denies invalid payloads, and hides upstream errors", async () => {
  const originalFetch = globalThis.fetch;
  let context = "";
  let profileUrl = "";
  let providerError = false;
  globalThis.fetch = async (input, init) => {
    const url = String(input);
    if (url.endsWith("/auth/v1/user")) return response({ id: "verified-user" });
    if (url.endsWith("reserve_api_request")) return response("reservation");
    if (url.endsWith("release_api_request")) return response(null);
    if (url.includes("/profiles?")) { profileUrl = url; return response([{ full_name: "PRIVATE NAME", date_of_birth: "PRIVATE DOB", location_name: "PRIVATE CITY", skin_type: "Dry", skin_concerns: "papules (2)" }]); }
    if (url.includes("/products?") || url.includes("/hairstyles?")) return response([]);
    if (url.includes("generativelanguage.googleapis.com")) {
      context = String(init?.body);
      return providerError ? response({ error: "secret upstream diagnostic" }, 500) : response({ candidates: [{ content: { parts: [{ text: "hello" }] } }] });
    }
    throw new Error(`Unexpected URL ${url}`);
  };
  try {
    await import("../functions/chat/index.ts");
    assert.equal((await handler(request({ message: "hi" }, false))).status, 401);
    assert.equal((await handler(request({ message: "x".repeat(70000) }))).status, 413);
    assert.equal((await handler(request(null))).status, 400);
    const result = await handler(request({ message: "hi", userId: "victim" }));
    assert.equal(result.status, 200);
    assert.match(profileUrl, /verified-user/);
    assert.ok(!profileUrl.includes("victim"));
    assert.ok(context.includes("Dry"));
    for (const secret of ["PRIVATE NAME", "PRIVATE DOB", "PRIVATE CITY"]) assert.ok(!context.includes(secret));
    providerError = true;
    const failed = await handler(request({ message: "hi" }));
    assert.equal(failed.status, 500);
    const body = await failed.json();
    assert.ok(!("detail" in body));
    assert.ok(!JSON.stringify(body).includes("secret upstream"));
    const preflight = await handler(new Request("https://project.test", { method: "OPTIONS" }));
    assert.equal(preflight.status, 204);
  } finally { globalThis.fetch = originalFetch; }
});

test("deletion requires recent verified identity, ignores supplied victim ID, and never reports partial failure as success", async () => {
  const originalFetch = globalThis.fetch;
  let recent = false;
  let failDatabase = false;
  let deletedId = "";
  globalThis.fetch = async (input, init) => {
    const url = String(input);
    if (url.endsWith("/auth/v1/user")) return response({ id: "verified-user", last_sign_in_at: new Date(Date.now() - (recent ? 1000 : 3600000)).toISOString() });
    if (url.endsWith("reserve_api_request")) return response("reservation");
    if (url.endsWith("release_api_request")) return response(null);
    if (url.includes("/storage/v1/object/list/")) { assert.match(String(init?.body), /verified-user/); return response([]); }
    if (url.endsWith("delete_account_data")) { deletedId = JSON.parse(String(init?.body)).p_user_id; return response(null, failDatabase ? 500 : 200); }
    throw new Error(`Unexpected URL ${url}`);
  };
  try {
    await import("../functions/delete-account/index.ts");
    assert.equal((await handler(request({ confirmation: "DELETE" }))).status, 403);
    recent = true;
    assert.equal((await handler(request({ confirmation: "no" }))).status, 400);
    assert.equal((await handler(request({ confirmation: "DELETE", userId: "victim" }))).status, 200);
    assert.equal(deletedId, "verified-user");
    failDatabase = true;
    const failed = await handler(request({ confirmation: "DELETE" }));
    assert.equal(failed.status, 503);
    assert.ok(!(await failed.json()).deleted);
  } finally { globalThis.fetch = originalFetch; }
});
