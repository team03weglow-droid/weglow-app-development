import { corsHeaders } from "../_shared/cors.ts";
import { adminFetch, HttpError, readJson, requireUser, withQuota } from "../_shared/security.ts";

const reply = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status, headers: { ...corsHeaders, "Content-Type": "application/json", "Cache-Control": "no-store" },
});

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders });
  if (request.method !== "POST") return reply(405, { error: "Use POST." });
  try {
    const user = await requireUser(request);
    const payload = await readJson(request, 1024) as { confirmation?: unknown } | null;
    if (payload?.confirmation !== "DELETE") throw new HttpError(400, "Account deletion requires confirmation.");
    if (!user.lastSignInAt || Date.now() - Date.parse(user.lastSignInAt) > 15 * 60_000 || !Number.isFinite(Date.parse(user.lastSignInAt))) {
      throw new HttpError(403, "Sign out and sign in again before deleting your account.");
    }
    return await withQuota(user.id, "delete-account", async () => {
      // Drain all files in the verified user's own folder. Never accept a caller-supplied ID/path.
      // The storage migration restricts new writes to flat image filenames, so nested uploads are denied.
      const deadline = Date.now() + 45_000;
      for (let batch = 0; batch < 100 && Date.now() < deadline; batch++) {
        const listed = await adminFetch("/storage/v1/object/list/profile-images", {
          method: "POST", body: JSON.stringify({ prefix: `${user.id}/`, limit: 100, offset: 0 }),
        });
        if (!listed.ok) throw new HttpError(503, "Could not remove account photos. Please retry.");
        const files = await listed.json() as { name: string; id: string | null }[];
        if (!Array.isArray(files) || files.some(f => !f.id || typeof f.name !== "string" || f.name.includes("/"))) {
          throw new HttpError(503, "Account photos need support assistance before deletion.");
        }
        if (files.length === 0) {
          const deleted = await adminFetch("/rest/v1/rpc/delete_account_data", {
            method: "POST", body: JSON.stringify({ p_user_id: user.id }),
          });
          if (!deleted.ok) throw new HttpError(503, "Could not finish account deletion. Please retry.");
          return reply(200, { deleted: true });
        }
        const removed = await adminFetch("/storage/v1/object/profile-images", {
          method: "DELETE", body: JSON.stringify({ prefixes: files.map(f => `${user.id}/${f.name}`) }),
        });
        if (!removed.ok) throw new HttpError(503, "Could not remove account photos. Please retry.");
      }
      throw new HttpError(503, "Account photos need support assistance before deletion.");
    });
  } catch (error) {
    if (error instanceof HttpError) return reply(error.status, { error: error.message });
    console.error("Account deletion failed");
    return reply(503, { error: "Account deletion is temporarily unavailable. Please retry." });
  }
});
