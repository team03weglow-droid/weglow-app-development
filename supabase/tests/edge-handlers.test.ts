import test from "node:test";
import assert from "node:assert/strict";

let handler: (request: Request) => Promise<Response>;
const values: Record<string, string> = {
  SUPABASE_URL: "https://project.test", SUPABASE_ANON_KEY: "public",
  SUPABASE_SERVICE_ROLE_KEY: "server-secret", WEATHER_API_KEY: "weather-secret",
};
(globalThis as any).Deno = { env: { get: (key: string) => values[key] }, serve: (callback: typeof handler) => { handler = callback; } };
const response = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status });
const request = (body: unknown, authorized = true) => new Request("https://project.test/functions/v1/chat/message", {
  method: "POST", headers: { "content-type": "application/json", ...(authorized ? { Authorization: "Bearer user-token" } : {}) }, body: JSON.stringify(body),
});

test("retired chat rejects requests without reading messages or calling services", async () => {
  const originalFetch = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = async () => { calls++; throw new Error("Retired chat must not call services"); };
  try {
    await import("../functions/chat/index.ts");
    for (const authorized of [true, false]) {
      const result = await handler(request({ message: "private message" }, authorized));
      assert.equal(result.status, 410);
      assert.deepEqual(await result.json(), { error: "Chatbot coming soon" });
    }
    const malformed = new Request("https://project.test/functions/v1/chat/message", { method: "POST", body: "{" });
    assert.equal((await handler(malformed)).status, 410);
    assert.equal(malformed.bodyUsed, false);
    assert.equal((await handler(new Request("https://project.test", { method: "OPTIONS" }))).status, 204);
    assert.equal(calls, 0);
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
