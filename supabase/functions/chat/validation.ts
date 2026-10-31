import { HttpError } from "../_shared/security.ts";

export function validateChatPayload(payload: unknown): {
  message: string; history: { role: "user" | "model"; text: string }[];
} {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) throw new HttpError(400, "Invalid request.");
  const body = payload as Record<string, unknown>;
  if (typeof body.message !== "string" || !body.message.trim() || body.message.length > 4000) {
    throw new HttpError(400, "Message must contain 1 to 4000 characters.");
  }
  const history = body.history ?? [];
  if (!Array.isArray(history) || history.length > 20) throw new HttpError(400, "History is too long.");
  let total = body.message.length;
  const turns = history.map((turn: unknown) => {
    if (!turn || typeof turn !== "object") throw new HttpError(400, "Invalid history.");
    const t = turn as Record<string, unknown>;
    if ((t.role !== "user" && t.role !== "model") || typeof t.text !== "string" || t.text.length > 8000) {
      throw new HttpError(400, "Invalid history entry.");
    }
    total += t.text.length;
    return { role: t.role, text: t.text } as { role: "user" | "model"; text: string };
  });
  if (total > 32000) throw new HttpError(400, "Conversation context is too long.");
  return { message: body.message.trim(), history: turns };
}
