import * as admin from "firebase-admin";
import { Request, Response, NextFunction } from "express";
import * as logger from "firebase-functions/logger";
import { RATE } from "../config/constants";
import { sendFailure, USER_MESSAGES } from "./responses";

/**
 * Project-wide daily kill-switch for the paid AI endpoints.
 *
 * Per-IP limits don't stop a distributed (many-IP) attack — this does. We keep a
 * single counter per UTC day in `system_usage/{YYYY-MM-DD}` and refuse all AI
 * traffic once it crosses RATE.GLOBAL_DAILY_AI_CALLS, so the bill has a hard
 * ceiling no matter how the load is spread.
 *
 * Cost: one read + one increment per AI request. The counter is read BEFORE the
 * provider call (to block) and incremented in the same step. Fails OPEN — a
 * Firestore blip must not take the whole API down — because the per-IP limiter
 * and `maxInstances` still bound spend in that window.
 */
const COLLECTION = "system_usage";

function todayUtc(): string {
  return new Date().toISOString().split("T")[0]; // YYYY-MM-DD (UTC)
}

export async function enforceGlobalDailyBudget(
  _req: Request,
  res: Response,
  next: NextFunction
): Promise<void> {
  const db = admin.firestore();
  const ref = db.collection(COLLECTION).doc(todayUtc());

  try {
    const snap = await ref.get();
    const used = (snap.data()?.aiCalls as number) || 0;

    if (used >= RATE.GLOBAL_DAILY_AI_CALLS) {
      logger.error("global-budget: BLOCKED (daily AI ceiling reached)", {
        used,
        limit: RATE.GLOBAL_DAILY_AI_CALLS,
      });
      sendFailure(res, 503, {
        error: "service_unavailable",
        message: USER_MESSAGES.SERVER_BUSY,
      });
      return;
    }

    // Count this request. Best-effort — don't block the user on the write.
    ref
      .set(
        {
          aiCalls: admin.firestore.FieldValue.increment(1),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      )
      .catch((err) =>
        logger.warn("global-budget: counter increment failed (non-fatal)", {
          error: err instanceof Error ? err.message : String(err),
        })
      );

    next();
  } catch (error) {
    // Fail open: the per-IP limiter + maxInstances still cap spend.
    logger.warn("global-budget: read failed, allowing request (fail-open)", {
      error: error instanceof Error ? error.message : String(error),
    });
    next();
  }
}
