import Anthropic from "@anthropic-ai/sdk";

/**
 * Thin wrapper around the Anthropic SDK for the two user-switchable Claude
 * models. Chat and translation both run through here.
 */

export const HAIKU_MODEL = "claude-haiku-4-5";
export const SONNET_MODEL = "claude-sonnet-4-6";
export const DEFAULT_MODEL = HAIKU_MODEL;

const ALLOWED_MODELS = new Set<string>([HAIKU_MODEL, SONNET_MODEL]);

/** Map a client-supplied model id/alias to an allowed Claude model. */
export function resolveModel(requested?: string | null): string {
  if (!requested) return DEFAULT_MODEL;
  const value = requested.trim();
  if (ALLOWED_MODELS.has(value)) return value;
  if (value.toLowerCase() === "haiku") return HAIKU_MODEL;
  if (value.toLowerCase() === "sonnet") return SONNET_MODEL;
  return DEFAULT_MODEL;
}

let client: Anthropic | null = null;

function getClient(): Anthropic {
  if (!client) {
    const apiKey = process.env.ANTHROPIC_API_KEY;
    if (!apiKey) {
      throw new Error("ANTHROPIC_API_KEY is not configured");
    }
    client = new Anthropic({ apiKey });
  }
  return client;
}

export interface ClaudeMessage {
  role: "user" | "assistant";
  content: string;
}

/** Single Messages API call returning the concatenated text output. */
export async function createMessage(
  model: string,
  system: string,
  messages: ClaudeMessage[],
  maxTokens = 2048
): Promise<string> {
  const response = await getClient().messages.create({
    model,
    max_tokens: maxTokens,
    system,
    messages: messages.map((m) => ({ role: m.role, content: m.content })),
  });

  return response.content
    .map((block) => (block.type === "text" ? block.text : ""))
    .join("")
    .trim();
}

const LANGUAGE_NAMES: Record<string, string> = {
  en: "English",
  bo: "Tibetan",
  "zh-CN": "Chinese (Simplified)",
};

function languageName(code: string): string {
  return LANGUAGE_NAMES[code] || code;
}

/** Translate text between languages, returning only the translated text. */
export async function translateWithClaude(
  model: string,
  text: string,
  sourceLang: string,
  targetLang: string
): Promise<string> {
  const from =
    sourceLang === "auto"
      ? "its source language (auto-detect it)"
      : languageName(sourceLang);
  const to = languageName(targetLang);

  const system =
    `You are an expert translator. Translate the user's text from ${from} ` +
    `into ${to}. Preserve meaning, tone and cultural nuance. ` +
    `Respond with ONLY the translated text — no preamble, no quotes, ` +
    `no explanations, no transliteration.`;

  return createMessage(model, system, [{ role: "user", content: text }], 2048);
}
