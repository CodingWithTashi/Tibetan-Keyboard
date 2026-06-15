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

export const SUPPORTED_LANGUAGES = ["en", "bo", "zh-CN"];

// The RevenueCat entitlement that grants pro access. Must match the app
// (RevenueCatManager.PREMIUM_ENTITLEMENT_ID) and the RevenueCat dashboard.
export const PRO_ENTITLEMENT_ID = "pro";
