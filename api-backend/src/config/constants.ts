export const LIMITS = {
  // Free-tier daily CHARACTER cap for /translate (pro users are unlimited).
  // Enforced per-user in enforceFreeLimit("translation").
  DAILY_TRANSLATION_CHARS: 5000,
  // Free-tier daily MESSAGE cap for /chat (pro users are unlimited).
  // Enforced per-user in enforceFreeLimit("chat").
  FREE_DAILY_CHAT_MESSAGES: 100,
  // Hard per-request input cap, applied GLOBALLY to every text-bearing field
  // (text / message / query / documentContext) on every endpoint. This is the
  // abuse guard against someone pasting a whole document into a single
  // chat/translate call and burning paid AI tokens. Single source of truth —
  // the Joi schema, the chat check and the global middleware all read it.
  MAX_INPUT_CHARS: 2000,
  MIN_TEXT_LENGTH: 1,
};

/**
 * Abuse / spend controls for the paid AI endpoints (/translate, /chat).
 * `userid` is a client-supplied header (not an authenticated identity), so the
 * per-user free caps only shape honest traffic — these IP- and project-level
 * limits are what actually bound the bill against a scripted/anonymous caller.
 * All values are env-overridable so you can tune without a code change.
 */
function envInt(name: string, fallback: number): number {
  const raw = process.env[name];
  const n = raw ? parseInt(raw, 10) : NaN;
  return Number.isFinite(n) && n > 0 ? n : fallback;
}

export const RATE = {
  // Per-IP, per-minute burst cap on the AI endpoints.
  AI_PER_MINUTE: envInt("AI_RATE_PER_MINUTE", 20),
  // Per-IP, per-DAY ceiling on the AI endpoints — bounds a single IP even if it
  // rotates the `userid` header to dodge the per-user free caps.
  AI_PER_DAY_PER_IP: envInt("AI_RATE_PER_DAY_PER_IP", 600),
  // Project-wide hard ceiling on total AI calls per UTC day. The kill-switch:
  // once hit, every AI request gets a clean 503 until the next day, so a
  // distributed (many-IP) attack can never run the bill past this point. Set
  // generously above real daily usage; tune via env GLOBAL_DAILY_AI_LIMIT.
  GLOBAL_DAILY_AI_CALLS: envInt("GLOBAL_DAILY_AI_LIMIT", 20000),
  // For anonymous / unidentified callers we still apply the free daily caps,
  // but keyed by IP (see enforceFreeLimit) so they can't get unlimited access.
  ANON_DAILY_TRANSLATION_CHARS: envInt("ANON_DAILY_TRANSLATION_CHARS", 2000),
  ANON_DAILY_CHAT_MESSAGES: envInt("ANON_DAILY_CHAT_MESSAGES", 20),
};

export const SUPPORTED_LANGUAGES = ["en", "bo", "zh-CN"];

// The RevenueCat entitlement that grants pro access. Must match the app
// (RevenueCatManager.PREMIUM_ENTITLEMENT_ID) and the RevenueCat dashboard.
export const PRO_ENTITLEMENT_ID = "pro";
