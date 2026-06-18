import * as admin from "firebase-admin";
import { Request, Response } from "express";
import * as logger from "firebase-functions/logger";
import { PRO_ENTITLEMENT_ID } from "../config/constants";

/**
 * RevenueCat webhook handler.
 *
 * RevenueCat POSTs an event here whenever a subscription starts, renews,
 * cancels, expires, etc. We mirror the resulting pro state into the user's
 * Firestore doc so the rest of the backend can read it cheaply.
 *
 * Auth: RevenueCat sends a fixed value in the `Authorization` header (set under
 * Dashboard -> Integrations -> Webhooks -> "Authorization header value"). We
 * compare it against the REVENUECAT_WEBHOOK_SECRET secret. This is the only
 * thing standing between this endpoint and the public internet — keep it set.
 *
 * Logging: all lines are prefixed `rc-webhook:` and carry `userId` + `type`
 * (+ `environment` = SANDBOX/PRODUCTION). In Google Cloud Logging, search the
 * text "rc-webhook" to see every event, or filter `jsonPayload.userId="<uid>"`
 * to trace one user. `environment` makes it obvious when a test purchase isn't
 * showing up because it's a SANDBOX event.
 */

// Events that grant / keep access.
const GRANT_EVENTS = new Set([
  "INITIAL_PURCHASE",
  "RENEWAL",
  "UNCANCELLATION",
  "PRODUCT_CHANGE",
  "NON_RENEWING_PURCHASE",
  "SUBSCRIPTION_EXTENDED",
]);

// Events that revoke access immediately.
const REVOKE_EVENTS = new Set(["EXPIRATION", "SUBSCRIPTION_PAUSED"]);

export function revenueCatWebhook(getAuthSecret: () => string | undefined) {
  return async (req: Request, res: Response): Promise<void> => {
    const expected = getAuthSecret();
    const got = req.headers["authorization"];

    if (!expected) {
      // Misconfiguration: refuse rather than accept unauthenticated writes.
      logger.error("rc-webhook: REVENUECAT_WEBHOOK_SECRET is not set — refusing");
      res.status(500).json({ success: false, error: "Webhook not configured" });
      return;
    }
    if (got !== expected) {
      logger.warn("rc-webhook: rejected (bad/missing Authorization header)");
      res.status(401).json({ success: false, error: "Unauthorized" });
      return;
    }

    const event = req.body?.event;
    if (!event || !event.type) {
      logger.warn("rc-webhook: rejected (missing event/type in body)");
      res.status(400).json({ success: false, error: "Missing event" });
      return;
    }

    const type: string = event.type;
    const environment: string = event.environment || "UNKNOWN";
    const appUserId: string | undefined =
      event.app_user_id || event.original_app_user_id;
    const db = admin.firestore();

    logger.info("rc-webhook: received", {
      type,
      environment,
      userId: appUserId,
      entitlements: event.entitlement_ids || event.entitlement_id,
      expirationAtMs: event.expiration_at_ms,
    });

    try {
      // TRANSFER moves entitlements between app user IDs (no app_user_id field).
      if (type === "TRANSFER") {
        const to: string[] = event.transferred_to || [];
        const from: string[] = event.transferred_from || [];
        await Promise.all([
          ...to.map((id) => writeProState(db, id, true, event)),
          ...from.map((id) => writeProState(db, id, false, event)),
        ]);
        logger.info("rc-webhook: TRANSFER applied", { to, from, environment });
        res.json({ success: true });
        return;
      }

      if (!appUserId) {
        logger.warn("rc-webhook: rejected (missing app_user_id)", { type, environment });
        res.status(400).json({ success: false, error: "Missing app_user_id" });
        return;
      }

      // If the event names entitlements, only react to ours.
      const ents: string[] =
        event.entitlement_ids ||
        (event.entitlement_id ? [event.entitlement_id] : []);
      if (ents.length > 0 && !ents.includes(PRO_ENTITLEMENT_ID)) {
        logger.info("rc-webhook: ignored (entitlement not 'pro')", {
          userId: appUserId,
          type,
          entitlements: ents,
        });
        res.json({ success: true, ignored: `entitlement ${ents.join(",")}` });
        return;
      }

      let isPro: boolean;
      if (REVOKE_EVENTS.has(type)) {
        isPro = false;
      } else if (GRANT_EVENTS.has(type) || type === "CANCELLATION") {
        // CANCELLATION = auto-renew turned off (or refund); access lasts until
        // expiration_at_ms. A later EXPIRATION event flips it to false.
        const exp: number | undefined = event.expiration_at_ms;
        isPro = exp ? exp > Date.now() : true;
      } else {
        // BILLING_ISSUE, TEST, and anything new: don't change stored state.
        logger.info("rc-webhook: no-op (state unchanged)", {
          userId: appUserId,
          type,
          environment,
        });
        res.json({ success: true, ignored: type });
        return;
      }

      await writeProState(db, appUserId, isPro, event);
      logger.info("rc-webhook: applied", {
        userId: appUserId,
        type,
        isPro,
        environment,
      });
      res.json({ success: true });
    } catch (error) {
      logger.error("rc-webhook: processing failed (RevenueCat will retry)", {
        userId: appUserId,
        type,
        environment,
        error: error instanceof Error ? error.message : String(error),
      });
      // 5xx makes RevenueCat retry — desirable for a transient Firestore error.
      res.status(500).json({ success: false, error: "Processing failed" });
    }
  };
}

/**
 * Single, server-owned pro state on `users/{uid}`. We deliberately write a
 * minimal, namespaced field set (isPro / proExpiresAt / proUpdatedAt) and
 * nothing else — the app never writes these, so there are no competing writers
 * and no field sprawl. Event type / environment live in the logs, not the doc.
 */
async function writeProState(
  db: FirebaseFirestore.Firestore,
  userId: string,
  isPro: boolean,
  event: any
): Promise<void> {
  if (!userId) return;
  const expMs: number | undefined = event.expiration_at_ms;

  await db.collection("users").doc(userId).set(
    {
      isPro,
      proExpiresAt: expMs ? admin.firestore.Timestamp.fromMillis(expMs) : null,
      proUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true }
  );
}
