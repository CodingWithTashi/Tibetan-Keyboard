import axios from "axios";
import { PRO_ENTITLEMENT_ID } from "../config/constants";

const RC_API_BASE = "https://api.revenuecat.com/v1";

export interface RevenueCatProStatus {
  isPro: boolean;
  /** Entitlement expiry in epoch ms, or null for a non-expiring (lifetime) one. */
  expiresAtMs: number | null;
}

/**
 * Live "is this user pro right now?" check against RevenueCat's REST API.
 *
 * This is the FALLBACK path: it's only used when the Firestore mirror says
 * "not pro" (e.g. a purchase that just completed but whose webhook hasn't
 * landed yet). It guarantees correctness on that edge without adding a network
 * call to every request.
 *
 * Requires a RevenueCat *secret* API key (starts with "sk_"), NOT the public
 * SDK key the app ships with. Create one in the RevenueCat dashboard and set:
 *   firebase functions:secrets:set REVENUECAT_API_KEY
 *
 * @param appUserId must equal the Firebase UID (the app sets this via
 *   Purchases.configure(...).appUserID(firebaseUid)).
 */
export async function getProFromRevenueCat(
  appUserId: string,
  apiKey: string
): Promise<RevenueCatProStatus> {
  if (!apiKey || !appUserId || appUserId === "anonymous") {
    return { isPro: false, expiresAtMs: null };
  }

  const url = `${RC_API_BASE}/subscribers/${encodeURIComponent(appUserId)}`;
  const { data } = await axios.get(url, {
    headers: { Authorization: `Bearer ${apiKey}` },
    timeout: 5000,
  });

  const entitlement = data?.subscriber?.entitlements?.[PRO_ENTITLEMENT_ID];
  if (!entitlement) return { isPro: false, expiresAtMs: null };

  // expires_date is an ISO string, or null/absent for a lifetime entitlement.
  const expires: string | null = entitlement.expires_date ?? null;
  if (!expires) return { isPro: true, expiresAtMs: null };

  const expiresAtMs = new Date(expires).getTime();
  return { isPro: expiresAtMs > Date.now(), expiresAtMs };
}
