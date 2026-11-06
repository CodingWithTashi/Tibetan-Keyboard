import { Request, Response, NextFunction } from "express";
import Joi from "joi";
import { randomUUID } from "crypto";
import { SUPPORTED_LANGUAGES, LIMITS } from "../config/constants";
import { checkSpeechAudio } from "../services/wavInfo";
import { STT_MESSAGES, sendFailure } from "./responses";

// Fields that carry user-typed text across the API. The global cap inspects
// every one of these on every request body, so no endpoint (including ones that
// skip the Joi schemas) can be fed an oversized payload.
const TEXT_FIELDS = ["text", "message", "query", "documentContext"] as const;

/**
 * Global hard cap on user input length. Mounted once for the whole app, BEFORE
 * any route handler, so a single oversized field (e.g. a pasted document) is
 * rejected up front instead of reaching a paid AI provider. Defence-in-depth on
 * top of the per-endpoint Joi schemas and the 10kb JSON body limit.
 */
export function enforceGlobalCharLimit(
  req: Request,
  res: Response,
  next: NextFunction
): void {
  const body = req.body;
  if (body && typeof body === "object") {
    for (const field of TEXT_FIELDS) {
      const value = (body as Record<string, unknown>)[field];
      if (typeof value === "string" && value.length > LIMITS.MAX_INPUT_CHARS) {
        res.status(400).json({
          success: false,
          error: "Input too long",
          message: `'${field}' must not exceed ${LIMITS.MAX_INPUT_CHARS} characters (received ${value.length}).`,
        });
        return;
      }
    }
  }
  next();
}

const translateSchema = Joi.object({
  text: Joi.string()
    .min(LIMITS.MIN_TEXT_LENGTH)
    .max(LIMITS.MAX_INPUT_CHARS)
    .required()
    .messages({
      "string.min": `Text must be at least ${LIMITS.MIN_TEXT_LENGTH} character`,
      "string.max": `Text must not exceed ${LIMITS.MAX_INPUT_CHARS} characters`,
      "any.required": "Text is required",
    }),
  sourceLang: Joi.string()
    .valid(...SUPPORTED_LANGUAGES, "auto")
    .required()
    .messages({
      "any.only": `Source language must be one of: ${SUPPORTED_LANGUAGES.join(
        ", "
      )}, auto`,
      "any.required": "Source language is required",
    }),
  targetLang: Joi.string()
    .valid(...SUPPORTED_LANGUAGES)
    .required()
    .messages({
      "any.only": `Target language must be one of: ${SUPPORTED_LANGUAGES.join(
        ", "
      )}`,
      "any.required": "Target language is required",
    }),
 model: Joi.string().optional(),
});

export function validateTranslateRequest(
  req: Request,
  res: Response,
  next: NextFunction
): void {
  // Require the userid header to be present (the app always sends it — the
  // Firebase UID, or "anonymous" when logged out). This rejects header-less
  // scripted calls; abuse from "anonymous" is still bounded per-IP downstream.
  const userId = req.headers["userid"];
  if (userId == null || userId === "" || userId === "undefined") {
    res.status(400).json({
      success: false,
      error: "invalid_request",
      message: "User ID is required in headers",
    });
    return;
  }

  const { error } = translateSchema.validate(req.body);

  if (error) {
    res.status(400).json({
      success: false,
      error: "validation_error",
      message: error.details[0].message,
    });
    return;
  }

  next();
}

export function validateChatRequest(
  req: Request,
  res: Response,
  next: NextFunction
): void {
  if (req.headers["userid"] == null || req.headers["userid"] === "undefined") {
    res.status(400).json({
      success: false,
      error: "Invalid request",
      message: "User ID is required in headers",
    });
    return;
  }
  const { message } = req.body;

  if (!message || typeof message !== "string" || message.trim().length === 0) {
    res.status(400).json({
      success: false,
      error: "Invalid request",
      message: "Message is required and must be a non-empty string",
    });
    return;
  }

  if (message.length > LIMITS.MAX_INPUT_CHARS) {
    res.status(400).json({
      success: false,
      error: "Message too long",
      message: `Message must not exceed ${LIMITS.MAX_INPUT_CHARS} characters.`,
    });
    return;
  }

  next();
}

/** Smallest body that can be a real recording; anything less is a stray request. */
const MIN_AUDIO_BYTES = 1024;

/** An idempotency key the app generated: a UUID-like token, never free text. */
const IDEMPOTENCY_KEY = /^[A-Za-z0-9_-]{8,64}$/;

/** The recording as the Functions framework delivers it, or null. */
function audioBody(req: Request): Buffer | null {
  const raw = (req as Request & { rawBody?: unknown }).rawBody;
  if (Buffer.isBuffer(raw)) return raw;
  return Buffer.isBuffer(req.body) ? req.body : null;
}

/**
 * Gate for `POST /speech-to-text`. The app sends the recording as the raw
 * request body (`Content-Type: audio/wav`). Everything is checked here, before
 * a byte reaches the paid Monlam API: the caller identifies itself, the audio
 * is the format our app records, and it is at most 60 s long.
 *
 * On success the route reads `res.locals.speech` instead of re-parsing.
 */
export function validateSpeechRequest(
  req: Request,
  res: Response,
  next: NextFunction
): void {
  if (req.headers["userid"] == null || req.headers["userid"] === "undefined") {
    res.status(400).json({
      success: false,
      error: "Invalid request",
      message: "User ID is required in headers",
    });
    return;
  }

  const wav = audioBody(req);
  const checked =
    wav && wav.length >= MIN_AUDIO_BYTES ? checkSpeechAudio(wav) : null;

  if (!wav || !checked || !checked.ok) {
    const reason = checked && !checked.ok ? checked.reason : "BAD_AUDIO";
    sendFailure(res, reason === "TOO_LONG" ? 413 : 400, {
      error: reason,
      message: STT_MESSAGES[reason],
    });
    return;
  }

  const supplied = req.headers["x-idempotency-key"];
  const idempotencyKey =
    typeof supplied === "string" && IDEMPOTENCY_KEY.test(supplied)
      ? supplied
      : randomUUID();

  res.locals.speech = { wav, durationMs: checked.durationMs, idempotencyKey };
  next();
}
