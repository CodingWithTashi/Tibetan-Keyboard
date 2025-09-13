import { Request, Response, NextFunction } from "express";
import Joi from "joi";
import { SUPPORTED_LANGUAGES, LIMITS } from "../config/constants";

const translateSchema = Joi.object({
  text: Joi.string()
    .min(LIMITS.MIN_TEXT_LENGTH)
    .max(LIMITS.MAX_TEXT_LENGTH)
    .required()
    .messages({
      "string.min": `Text must be at least ${LIMITS.MIN_TEXT_LENGTH} character`,
      "string.max": `Text must not exceed ${LIMITS.MAX_TEXT_LENGTH} characters`,
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
});

const grammarSchema = Joi.object({
  text: Joi.string()
    .min(LIMITS.MIN_TEXT_LENGTH)
    .max(LIMITS.MAX_TEXT_LENGTH)
    .required()
    .messages({
      "string.min": `Text must be at least ${LIMITS.MIN_TEXT_LENGTH} character`,
      "string.max": `Text must not exceed ${LIMITS.MAX_TEXT_LENGTH} characters`,
      "any.required": "Text is required",
    }),
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

export function validateGrammarRequest(
  req: Request,
  res: Response,
  next: NextFunction
): void {
  const { error } = grammarSchema.validate(req.body);

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
  console.log("Header userId:", req.headers["userid"]);

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
  }

  if (message.length > 5000) {
    res.status(400).json({
      success: false,
      error: "Message too long",
      message: "Message must be less than 5000 characters",
    });
  }

  next();
}
