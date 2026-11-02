import { corsHeaders } from "../_shared/cors.ts"

// Retirement response for old clients. Do not read bodies or call providers.
Deno.serve(async (request: Request): Promise<Response> => {
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders })
  }
  return new Response(JSON.stringify({ error: "Chatbot coming soon" }), {
    status: 410,
    headers: { ...corsHeaders, "Content-Type": "application/json", "Cache-Control": "no-store" },
  })
})
