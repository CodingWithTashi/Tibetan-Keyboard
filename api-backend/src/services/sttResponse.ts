/**
 * Pure helpers that turn Monlam's speech-to-text reply into what the app needs, so the app never
 * depends on Monlam's field names or status codes.
 *
 * Confirmed against a real call (2026-11-05): `POST /speech-to-text/` and `/live` both answer
 * `{ text, cost, latency_ms, service_request_id, status: "success", ... }`.
 */

/** A letter or digit in any script: what separates words from stray punctuation. */
const HAS_WORDS = /[\p{L}\p{N}]/u;

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/**
 * The transcript in a Monlam reply: trimmed text, or an empty string when nothing was said, or
 * null when the reply holds no `text` at all (so the caller can tell "nothing was said" from "we
 * do not understand this reply").
 *
 * Monlam answers noise with a lone shad ("།"). Typed into the user's field that looks like a bug,
 * so text without a single letter or digit counts as nothing said.
 */
export function extractTranscript(json: unknown): string | null {
  if (!isObject(json) || typeof json.text !== "string") return null;
  const text = json.text.trim();
  return HAS_WORDS.test(text) ? text : "";
}

export interface Billing {
  cost?: number;
  latencyMs?: number;
  requestId?: string;
}

/** The billing metadata Monlam adds to a reply, for the `stt:` spend log. */
export function extractBilling(json: unknown): Billing {
  if (!isObject(json)) return {};
  const out: Billing = {};
  if (typeof json.cost === "number") out.cost = json.cost;
  if (typeof json.latency_ms === "number") out.latencyMs = json.latency_ms;
  if (typeof json.service_request_id === "string") out.requestId = json.service_request_id;
  return out;
}

export interface MappedFailure {
  /** HTTP status we return to the app. */
  status: number;
  /** Short code the app branches on. */
  error: string;
}

/** What we tell the app when Monlam answers with `status`, or times out. */
export function mapMonlamStatus(status: number | "timeout"): MappedFailure {
  if (status === "timeout") return { status: 504, error: "STT_TIMEOUT" };
  switch (status) {
    case 400:
    case 422:
      return { status: 400, error: "BAD_AUDIO" };
    case 401:
    case 403:
      // Our own key was refused: not the user's fault, and ours to fix.
      return { status: 502, error: "STT_CONFIG" };
    case 402:
      return { status: 503, error: "STT_UNAVAILABLE" };
    case 429:
      return { status: 429, error: "RATE_LIMITED" };
    default:
      return { status: 502, error: "STT_FAILED" };
  }
}
