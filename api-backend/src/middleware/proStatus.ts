import * as admin from "firebase-admin";
import { Request, Response, NextFunction } from "express";
import * as logger from "firebase-functions/logger";
import { getProFromRevenueCat } from "../services/revenueCatService";
import { LIMITS, RATE } from "../config/constants";

/**
 * Backend pro-status checks.
 *
 * RevenueCat is the source of truth. The ONLY writer of pro state into Firestore
 * is this backend (via the Admin SDK): the RevenueCat webhook on every event,
 * plus the REST fallback below. The app never writes these fields, so there's a
 * single, clean, server-owned namespace on `users/{uid}`:
 *
 *   isPro:        boolean
 *   proExpiresAt: Timestamp | null   (null = lifetime / non-expiring)
 *   proUpdatedAt: Timestamp          (audit only)
 *
 * As a safety net we hit the RevenueCat REST API live when the mirror says
 * "not pro" — this covers the brief window between a purchase completing and the
 * webhook arriving, so we never depend on the client telling us it paid.
 *
 * Logging: every line carries `userId`, so in Google Cloud Logging you can
 * filter `jsonPayload.userId="<uid>"` and read the whole story for one user.
 * All messages are prefixed `pro-status:` for easy text search.
 */

type Doc = FirebaseFirestore.DocumentData | undefined;

/** How a user's pro status was decided — handy when tracing in the logs. */
export type ProSource = "mirror" | "rc_fallback" | "none";

export interface ProResolution {
  isPro: boolean;
  source: ProSource;
}

function toMillis(value: unknown): number {
  if (value == null) return Number.POSITIVE_INFINITY; // no expiry => never expires
  if (typeof (value as any)?.toMillis === "function") return (value as any).toMillis();
  if (typeof value === "number") return value;
  const parsed = new Date(value as string).getTime();
  return Number.isFinite(parsed) ? parsed : Number.POSITIVE_INFINITY;
}

/** Is the Firestore mirror currently flagging this user as pro (and unexpired)? */
function isProFromMirror(data: Doc): boolean {
  if (data?.isPro !== true) return false;
  // proExpiresAt null/absent => non-expiring (lifetime) => +Infinity => true.
  return toMillis(data.proExpiresAt) > Date.now();
}

/**
 * Resolve pro status for a user: fast Firestore mirror first, then a live
 * RevenueCat REST fallback (and backfill the mirror on a fallback hit so the
 * next request is a cheap read). Returns both the answer and how it was
 * reached, so callers can log a single, traceable line.
 */
export async function isUserPro(
  userId: string,
  rcApiKey?: string
): Promise<ProResolution> {
  if (!userId || userId === "anonymous") {
    return { isPro: false, source: "none" };
  }
  const db = admin.firestore();

  try {
    const doc = await db.collection("users").doc(userId).get();
    if (isProFromMirror(doc.data())) {
      return { isPro: true, source: "mirror" };
    }
  } catch (error) {
    logger.warn("pro-status: Firestore mirror read failed", {
      userId,
      error: error instanceof Error ? error.message : String(error),
    });
  }

  if (rcApiKey) {
    try {
      const rc = await getProFromRevenueCat(userId, rcApiKey);
      if (rc.isPro) {
        // Webhook hasn't landed yet — backfill so the next read is cheap.
        await db.collection("users").doc(userId).set(
          {
            isPro: true,
            proExpiresAt: rc.expiresAtMs
              ? admin.firestore.Timestamp.fromMillis(rc.expiresAtMs)
              : null,
            proUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
          },
          { merge: true }
        );
        logger.info("pro-status: granted via RevenueCat REST fallback (mirror was stale)", {
          userId,
        });
        return { isPro: true, source: "rc_fallback" };
      }
    } catch (error) {
      logger.warn("pro-status: RevenueCat REST fallback failed", {
        userId,
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }

  return { isPro: false, source: "none" };
}

/**
 * Express middleware that sets `req.isPro` and `req.userId`.
 * Pass a getter so the secret key is read lazily at request time.
 */
export function attachProStatus(getRcApiKey: () => string | undefined) {
  return async (
    req: Request,
    _res: Response,
    next: NextFunction
  ): Promise<void> => {
    const userId =
      ((req as any).userId as string) ||
      (req.headers["userid"] as string) ||
      "anonymous";
    (req as any).userId = userId;

    try {
      const { isPro, source } = await isUserPro(userId, getRcApiKey());
      (req as any).isPro = isPro;
      logger.info("pro-status: resolved", {
        userId,
        path: req.path,
        isPro,
        source,
      });
    } catch (error) {
      (req as any).isPro = false;
      logger.error("pro-status: check threw, treating user as free", {
        userId,
        path: req.path,
        error: error instanceof Error ? error.message : String(error),
      });
    }
    next();
  };
}

/** Firestore-safe doc id from a client IP (no "/" allowed in doc ids). */
function ipDocId(ip: string | undefined): string {
  return (ip || "unknown").replace(/[/]/g, "_");
}

/**
 * Enforce free-tier daily limits. Pro users (req.isPro) are skipped entirely.
 * Requires `attachProStatus` to have run first.
 *
 * - "chat": counts 1 per message, cap = FREE_DAILY_CHAT_MESSAGES.
 * - "translation": counts text.length chars, cap = DAILY_TRANSLATION_CHARS.
 *
 * Identified users are tracked on `users/{uid}`. Anonymous/unidentified callers
 * are NOT skipped — they'd otherwise get unlimited paid access by simply omitting
 * the `userid` header — they're tracked per IP in `anon_usage/{ip}` with the
 * (lower) anonymous daily caps. Counters reset daily via `resetDate` (UTC date).
 * Fails OPEN — a Firestore blip won't block users.
 */
export function enforceFreeLimit(service: "chat" | "translation") {
  return async (
    req: Request,
    res: Response,
    next: NextFunction
  ): Promise<void> => {
    const userId = (req as any).userId as string;

    if ((req as any).isPro) {
      next();
      return;
    }

    const isAnon = !userId || userId === "anonymous";
    const usedField = service === "chat" ? "chatUsed" : "translationUsed";
    const cost = service === "chat" ? 1 : req.body?.text?.length || 0;
    const limit = isAnon
      ? service === "chat"
        ? RATE.ANON_DAILY_CHAT_MESSAGES
        : RATE.ANON_DAILY_TRANSLATION_CHARS
      : service === "chat"
      ? LIMITS.FREE_DAILY_CHAT_MESSAGES
      : LIMITS.DAILY_TRANSLATION_CHARS;

    const db = admin.firestore();
    const today = new Date().toISOString().split("T")[0]; // YYYY-MM-DD (UTC)
    const userRef = isAnon
      ? db.collection("anon_usage").doc(ipDocId(req.ip))
      : db.collection("users").doc(userId);

    try {
      const snap = await userRef.get();
      const data = snap.data() || {};
      const isNewDay = data.resetDate !== today;
      const used = isNewDay ? 0 : data[usedField] || 0;

      if (used + cost > limit) {
        logger.warn("free-limit: BLOCKED (daily cap reached)", {
          userId,
          service,
          used,
          cost,
          limit,
        });
        res.status(429).json({
          success: false,
          error: "Daily free limit reached",
          message: `You've reached today's free ${service} limit. Upgrade to Pro for unlimited access.`,
          usage: { used, limit, remaining: Math.max(0, limit - used) },
        });
        return;
      }

      if (isNewDay) {
        await userRef.set(
          {
            resetDate: today,
            chatUsed: service === "chat" ? cost : 0,
            translationUsed: service === "translation" ? cost : 0,
            grammarUsed: 0,
          },
          { merge: true }
        );
      } else {
        await userRef.set(
          { [usedField]: admin.firestore.FieldValue.increment(cost) },
          { merge: true }
        );
      }

      (req as any).remainingCredits = limit - used - cost;
      logger.debug("free-limit: allowed", {
        userId,
        service,
        used: used + cost,
        limit,
      });
      next();
    } catch (error) {
      // Fail open: a Firestore hiccup must not block a paying-adjacent flow.
      logger.warn("free-limit: check failed, allowing request (fail-open)", {
        userId,
        service,
        error: error instanceof Error ? error.message : String(error),
      });
      next();
    }
  };
}
