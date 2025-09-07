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
