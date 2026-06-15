import { Request, Response, NextFunction } from "express";
import Joi from "joi";
import { SUPPORTED_LANGUAGES, LIMITS } from "../config/constants";

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
  const { error } = translateSchema.validate(req.body);

  if (error) {
    res.status(400).json({
      success: false,
      error: "Validation error",
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
