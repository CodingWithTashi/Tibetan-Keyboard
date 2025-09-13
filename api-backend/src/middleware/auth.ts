import * as admin from "firebase-admin";
import { Request, Response, NextFunction } from "express";
import { LIMITS } from "../config/constants";

export async function validateApiKey(
  req: Request,
  res: Response,
  next: NextFunction
): Promise<void> {
  try {
    const apiKey = req.headers["x-api-key"] as string;

    if (!apiKey) {
      res.status(401).json({
        success: false,
        error: "API key required",
        message: "Please provide a valid API key in the x-api-key header",
      });
      return;
    }

    // Verify API key against your authentication system
    // This could be JWT token validation, database lookup, etc.
    const userId = await verifyApiKey(apiKey);

    if (!userId) {
      res.status(401).json({
        success: false,
        error: "Invalid API key",
        message: "The provided API key is invalid or expired",
      });
      return;
    }

    (req as any).userId = userId;
    next();
  } catch (error) {
    console.error("Auth error:", error);
    res.status(500).json({
      success: false,
      error: "Authentication failed",
      message: "Internal authentication error",
    });
  }
}

export function checkUserLimits(serviceType: "translation" | "grammar") {
  return async (
    req: Request,
    res: Response,
    next: NextFunction
  ): Promise<void> => {
    try {
      const userId = (req as any).userId;
      const textLength = req.body.text?.length || 0;

      const userLimits = await getUserLimits(userId);
      const dailyLimit =
        serviceType === "translation"
          ? LIMITS.DAILY_TRANSLATION_CHARS
          : LIMITS.DAILY_GRAMMAR_CHARS;

      const usedToday =
        serviceType === "translation"
          ? userLimits.translationUsed
          : userLimits.grammarUsed;

      if (usedToday + textLength > dailyLimit) {
        res.status(429).json({
          success: false,
          error: "Daily limit exceeded",
          message: `Daily ${serviceType} limit of ${dailyLimit} characters exceeded`,
          usage: {
            used: usedToday,
            limit: dailyLimit,
            remaining: Math.max(0, dailyLimit - usedToday),
          },
        });
        return;
      }

      (req as any).remainingCredits = dailyLimit - usedToday;
      next();
    } catch (error) {
      console.error("Limit check error:", error);
      res.status(500).json({
        success: false,
        error: "Limit check failed",
        message: "Unable to verify usage limits",
      });
    }
  };
}

async function verifyApiKey(apiKey: string): Promise<string | null> {
  try {
    // Simple API key verification - replace with your preferred method
    // This could be JWT verification, database lookup, etc.

    const db = admin.firestore();
    const apiKeyDoc = await db.collection("api_keys").doc(apiKey).get();

    if (!apiKeyDoc.exists) {
      return null;
    }

    const keyData = apiKeyDoc.data();
    if (
      !keyData?.active ||
      (keyData.expiresAt && keyData.expiresAt.toDate() < new Date())
    ) {
      return null;
    }

    return keyData.userId;
  } catch (error) {
    console.error("API key verification error:", error);
    return null;
  }
}

async function getUserLimits(userId: string) {
  const db = admin.firestore();
  const today = new Date().toISOString().split("T")[0]; // YYYY-MM-DD format

  const userDoc = await db.collection("users").doc(userId).get();

  if (!userDoc.exists) {
    // Create new user with default limits
    const defaultLimits = {
      translationLimit: LIMITS.DAILY_TRANSLATION_CHARS,
      grammarLimit: LIMITS.DAILY_GRAMMAR_CHARS,
      translationUsed: 0,
      grammarUsed: 0,
      resetDate: today,
    };

    await db.collection("users").doc(userId).set(defaultLimits);
    return defaultLimits;
  }

  const userData = userDoc.data();

  // Reset limits if it's a new day
  if (userData?.resetDate !== today) {
    const resetLimits = {
      ...userData,
      translationUsed: 0,
      grammarUsed: 0,
      resetDate: today,
    };

    await db.collection("users").doc(userId).update(resetLimits);
    return resetLimits;
  }

  return userData;
}

export async function updateUserUsage(
  userId: string,
  serviceType: "translation" | "grammar",
  charactersUsed: number
) {
  const db = admin.firestore();
  const userRef = db.collection("users").doc(userId);

  const updateField =
    serviceType === "translation" ? "translationUsed" : "grammarUsed";

  await userRef.update({
    [updateField]: admin.firestore.FieldValue.increment(charactersUsed),
  });
}
