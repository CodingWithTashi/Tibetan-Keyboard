import { Response } from "express";
import * as logger from "firebase-functions/logger";

/**
 * Standard, user-facing error envelope.
 *
 * Two rules, enforced in one place so every endpoint behaves the same:
 *   1. The client only ever sees a clean, professional `message` we wrote — the
 *      raw provider/internal error (e.g. "ANTHROPIC_API_KEY is not configured",
 *      an Axios stack, a Firestore code) is logged server-side and NEVER sent
 *      back. This avoids leaking internals and gives users a readable message.
 *   2. The shape is always `{ success: false, error, message }` so the app can
 *      parse it uniformly.
 */
export interface FailureOptions {
  /** Short machine-ish code for the app to branch on (e.g. "rate_limited"). */
  error: string;
  /** Clean, user-facing sentence. */
  message: string;
  /** The real error to log (never returned to the client). */
  cause?: unknown;
  /** Context for the log line (userId, engine, path, …). */
  logContext?: Record<string, unknown>;
}

export function sendFailure(
  res: Response,
  status: number,
  { error, message, cause, logContext }: FailureOptions
): void {
  if (cause !== undefined) {
    logger.error(`response: ${error}`, {
      status,
      ...logContext,
      cause: cause instanceof Error ? cause.message : String(cause),
    });
  }
  res.status(status).json({ success: false, error, message });
}

/** Reusable user-facing copy. Keep these clean, calm and non-technical. */
export const USER_MESSAGES = {
  TRANSLATE_FAILED:
    "We couldn't complete the translation right now. Please try again in a moment.",
  CHAT_FAILED:
    "We couldn't get a response right now. Please try again in a moment.",
  SERVER_BUSY:
    "Our service is experiencing high demand right now. Please try again shortly.",
  UNEXPECTED:
    "Something went wrong on our end. Please try again in a moment.",
} as const;
