export const LIMITS = {
  DAILY_TRANSLATION_CHARS: 5000,
  DAILY_GRAMMAR_CHARS: 3000,
  MAX_TEXT_LENGTH: 1000,
  MIN_TEXT_LENGTH: 1,
  // Free-tier daily message cap for /chat (pro users are unlimited).
  FREE_DAILY_CHAT_MESSAGES: 100,
};

export const SUPPORTED_LANGUAGES = ["en", "bo", "zh-CN"];

// The RevenueCat entitlement that grants pro access. Must match the app
// (RevenueCatManager.PREMIUM_ENTITLEMENT_ID) and the RevenueCat dashboard.
export const PRO_ENTITLEMENT_ID = "pro";
