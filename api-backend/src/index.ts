import { onRequest } from "firebase-functions/v2/https";
import { setGlobalOptions } from "firebase-functions/v2";
import * as admin from "firebase-admin";
import express from "express";
import cors from "cors";
import helmet from "helmet";
import rateLimit from "express-rate-limit";
import advancedGrammarService from "./services/advancedGrammarService";
import transliterationService from "./services/transliterationService";
import {
  validateTranslateRequest,
  validateChatRequest,
  enforceGlobalCharLimit,
} from "./middleware/validation";
import { errorHandler } from "./middleware/errorHandler";
import {
  ApiResponse,
  TranslateRequest,
  GeminiChatRequest,
  GeminiChatResponse,
} from "./types";
import { log } from "console";
import {
  ChatSessionManager,
  generateSessionId,
} from "./manager/ChatSessionManager";
import { resolveModel } from "./services/anthropicService";
import { resolveEngine, translate } from "./services/translateProvider";
import { cachedCompute, makeCacheKey } from "./services/cacheService";
import { attachProStatus, enforceFreeLimit } from "./middleware/proStatus";
import { enforceGlobalDailyBudget } from "./middleware/globalBudget";
import { sendFailure, USER_MESSAGES } from "./middleware/responses";
import { RATE } from "./config/constants";
import { revenueCatWebhook } from "./webhooks/revenueCatWebhook";
import { defineSecret } from "firebase-functions/params";
import * as logger from "firebase-functions/logger";

// Set global options for all functions
setGlobalOptions({
  region: "asia-south1", // Mumbai region
  maxInstances: 10,
  timeoutSeconds: 60,
  memory: "256MiB",
});

// Initialize Firebase Admin
admin.initializeApp();

// Anthropic API key — set with: firebase functions:secrets:set ANTHROPIC_API_KEY
const anthropicApiKey = defineSecret("ANTHROPIC_API_KEY");

// Azure Translator subscription key — powers the "azure" translation engine.
// Set with: firebase functions:secrets:set AZURE_TRANSLATOR_KEY
// Region/endpoint are non-secret and read from env (see .env), defaulting to
// eastus + the global text endpoint when unset.
const azureTranslatorKey = defineSecret("AZURE_TRANSLATOR_KEY");

// RevenueCat secrets:
// - REVENUECAT_WEBHOOK_SECRET: the "Authorization header value" you set in the
//   RevenueCat dashboard webhook config; we reject any webhook call that doesn't
//   send exactly this value in its Authorization header.
// - REVENUECAT_API_KEY: a RevenueCat *secret* REST key (sk_...) used only as a
//   live fallback when the Firestore pro mirror says "not pro".
// Set both with: firebase functions:secrets:set REVENUECAT_WEBHOOK_SECRET
//                firebase functions:secrets:set REVENUECAT_API_KEY
const revenueCatWebhookSecret = defineSecret("REVENUECAT_WEBHOOK_SECRET");
const revenueCatApiKey = defineSecret("REVENUECAT_API_KEY");

// Reusable middleware that sets req.isPro (Firestore mirror + RC REST fallback).
const attachPro = attachProStatus(() => {
  try {
    return revenueCatApiKey.value();
  } catch {
    return undefined;
  }
});

const app = express();

// Behind the Cloud Functions / Hosting proxy, trust the first forwarded hop so
// express-rate-limit sees the real client IP.
app.set("trust proxy", 1);

// Security middleware
app.use(helmet());
app.use(
  cors({
    origin: process.env.ALLOWED_ORIGINS?.split(",") || [
      "http://localhost:3000",
    ],
    credentials: true,
  })
);

// RevenueCat webhook — registered BEFORE the global IP rate limiter (it's
// authenticated by a shared secret, and renewal spikes shouldn't be throttled)
// and with its own JSON parser (RevenueCat payloads can exceed the 10kb cap we
// apply to the AI endpoints).
app.post(
  "/revenuecat-webhook",
  express.json({ limit: "64kb" }),
  revenueCatWebhook(() => {
    try {
      return revenueCatWebhookSecret.value();
    } catch {
      return undefined;
    }
  })
);

// Rate limiting
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100, // Limit each IP to 100 requests per windowMs
  message: "Too many requests from this IP, please try again later.",
  standardHeaders: true,
  legacyHeaders: false,
});

app.use(limiter);
app.use(express.json({ limit: "10kb" }));

// Global hard cap on user-typed input (text / message / query / documentContext)
// for EVERY endpoint — the front-line guard against pasting a whole document
// into one call to burn paid AI tokens. Runs after JSON parsing, before routes.
app.use(enforceGlobalCharLimit);

// Stricter per-IP limiter for the AI (Claude/Azure) endpoints — protects spend
// and blocks burst abuse on the paid provider calls.
const aiLimiter = rateLimit({
  windowMs: 60 * 1000, // 1 minute
  max: RATE.AI_PER_MINUTE,
  message: {
    success: false,
    error: "rate_limited",
    message:
      "You're sending requests too quickly. Please slow down and try again in a moment.",
  },
  standardHeaders: true,
  legacyHeaders: false,
});

// Per-IP DAILY ceiling on the AI endpoints. The per-minute limiter stops bursts;
// this bounds a single IP that drips requests all day (e.g. rotating the
// `userid` header to dodge the per-user free caps).
const aiDailyLimiter = rateLimit({
  windowMs: 24 * 60 * 60 * 1000, // 24 hours
  max: RATE.AI_PER_DAY_PER_IP,
  message: {
    success: false,
    error: "rate_limited",
    message:
      "You've reached the daily request limit for this device. Please try again tomorrow.",
  },
  standardHeaders: true,
  legacyHeaders: false,
});

// Health check endpoint
app.get("/health", (req, res) => {
  res.json({ status: "healthy", timestamp: new Date().toISOString() });
});

// Translation endpoint — Claude-powered, cached (memory -> Firestore -> Claude).
app.post(
  "/translate",
  aiLimiter,
  aiDailyLimiter,
  validateTranslateRequest,
  attachPro,
  enforceFreeLimit("translation"),
  enforceGlobalDailyBudget,
  async (req, res) => {
    try {
      const { text, sourceLang, targetLang }: TranslateRequest = req.body;
      const userId = (req.headers["userid"] as string) || "anonymous";
      // The client sends the chosen translation engine in `model` (azure /
      // claude-haiku-4-5 / claude-sonnet-4-6); x-model header is a fallback.
      const engine = resolveEngine(
        (req.body.model as string) || (req.headers["x-model"] as string)
      );

      logger.info("Translate request", {
        userId,
        isPro: (req as any).isPro === true,
        engine,
        sourceLang,
        targetLang,
        chars: text.length,
      });

      const cacheKey = makeCacheKey("translate", {
        engine,
        text,
        sourceLang,
        targetLang,
      });

      const { value: translatedText, source } = await cachedCompute(
        cacheKey,
        async () =>
          (await translate(engine, text, sourceLang, targetLang)).text,
        { type: "translate", engine, sourceLang, targetLang }
      );

      logger.info("Translate success", { userId, engine, source });

      // `remainingCredits` is only set for tracked (non-pro, identified/anon)
      // users; pro users are unmetered. Report it only when we actually have it.
      const remainingCredits = (req as any).remainingCredits;
      const usage =
        typeof remainingCredits === "number"
          ? {
              charactersUsed: text.length,
              remainingCharacters: Math.max(0, remainingCredits),
            }
          : { charactersUsed: text.length };

      const response: ApiResponse<{ translatedText: string }> = {
        success: true,
        data: { translatedText },
        usage,
      };

      res.json(response);
    } catch (error) {
      // Log the real error; return only a clean, user-facing message.
      sendFailure(res, 500, {
        error: "translation_failed",
        message: USER_MESSAGES.TRANSLATE_FAILED,
        cause: error,
        logContext: { path: "/translate" },
      });
    }
  }
);

// Chat with Claude (Tibetan assistant). Model is user-switchable per request.
app.post(
  "/chat",
  aiLimiter,
  aiDailyLimiter,
  validateChatRequest,
  attachPro,
  enforceFreeLimit("chat"),
  enforceGlobalDailyBudget,
  async (req, res) => {
    try {
      const { message, sessionId, resetChat }: GeminiChatRequest = req.body;
      const userId = req.headers["userid"] as string;
      const model = resolveModel(
        (req.body.model as string) || (req.headers["x-model"] as string)
      );

      // Generate or use provided session ID
      //const currentSessionId = sessionId || generateSessionId();
      const currentSessionId = userId;
      logger.info("Chat request", {
        userId,
        isPro: (req as any).isPro === true,
        sessionId: currentSessionId,
        model,
        chars: message.length,
        resetChat: !!resetChat,
      });

      // Reset chat session if requested
      if (resetChat) {
        ChatSessionManager.resetSession(currentSessionId);
      }

      // Send message to Claude and get a Tibetan response
      const tibetanResponse = await ChatSessionManager.sendMessage(
        currentSessionId,
        message,
        { model }
      );

      logger.info("Chat success", { userId, model });

      // Calculate usage
      const charactersUsed = message.length + tibetanResponse.length;
      const remainingCharacters = Math.max(
        0,
        ((req as any).remainingCredits || 2000) - charactersUsed
      );

      const response: GeminiChatResponse = {
        success: true,
        data: {
          response: tibetanResponse,
          sessionId: currentSessionId,
        },
        usage: {
          charactersUsed,
          remainingCharacters,
        },
      };

      res.json(response);
    } catch (error) {
      // Log the real error; return only a clean, user-facing message.
      sendFailure(res, 500, {
        error: "chat_failed",
        message: USER_MESSAGES.CHAT_FAILED,
        cause: error,
        logContext: { path: "/chat" },
      });
    }
  }
);

// Additional endpoint to reset specific chat session
app.post("/chat/reset", async (req, res) => {
  try {
    const { sessionId } = req.body;

    if (!sessionId) {
      return res.status(400).json({
        success: false,
        error: "Session ID required",
      });
    }

    ChatSessionManager.resetSession(sessionId);

    return res.json({
      success: true,
      message: "Chat session reset successfully",
    });
  } catch (error) {
    console.error("Reset session error:", error);
    return res.status(500).json({
      success: false,
      error: "Failed to reset session",
    });
  }
});

// ============================================
// NEW PREMIUM FEATURE ENDPOINTS
// ============================================

// Advanced Tibetan Grammar Analysis Endpoint
app.post("/api/grammar/analyze", aiLimiter, async (req, res) => {
  try {
    const { text, mode = "realtime", style = "formal", contextualInfo } = req.body;
    const userId = req.headers["userid"] as string;

    if (!text || text.trim().length === 0) {
      return res.status(400).json({
        success: false,
        error: "Text is required",
      });
    }

    const userLevel = contextualInfo?.userLevel || "intermediate";
    const documentType = contextualInfo?.documentType || "casual";

    const result = await advancedGrammarService.analyzeTibetanGrammar(
      text,
      userLevel,
      documentType
    );

    // Save to Firestore history
    try {
      const db = admin.firestore();
      const userRef = db.collection("users").doc(userId);
      const historyRef = userRef.collection("grammar_history").doc();

      await historyRef.set({
        text,
        corrections: result.corrections,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        documentType,
        savedByUser: false,
        mode,
      });
    } catch (dbError) {
      console.error("Error saving to Firestore:", dbError);
      // Don't fail the request if Firestore fails
    }

    return res.json({
      success: true,
      data: result,
      usage: {
        charactersUsed: text.length,
      },
    });
  } catch (error) {
    console.error("Grammar analysis error:", error);
    return res.status(500).json({
      success: false,
      error: "Grammar analysis failed",
      message: error instanceof Error ? error.message : "Unknown error",
    });
  }
});

// Tone Alternatives Endpoint
app.post("/api/grammar/suggestions", aiLimiter, async (req, res) => {
  try {
    const { text, correctionId, type } = req.body;

    if (!text) {
      return res.status(400).json({
        success: false,
        error: "Text is required",
      });
    }

    let result: any = {
      alternatives: [],
      examples: [],
      culturalNotes: "",
    };

    if (type === "alternatives") {
      const tones: Array<"formal" | "casual" | "poetic" | "religious" | "modern"> = [
        "formal",
        "casual",
        "poetic",
      ];
      for (const tone of tones) {
        const alternatives = await advancedGrammarService.getToneAlternatives(text, tone);
        result.alternatives.push({
          tone,
          suggestions: alternatives,
        });
      }
    }

    return res.json({
      success: true,
      data: result,
    });
  } catch (error) {
    console.error("Grammar suggestions error:", error);
    return res.status(500).json({
      success: false,
      error: "Failed to get suggestions",
    });
  }
});

// Transliteration Endpoints
app.post("/api/transliterate/convert", async (req, res) => {
  try {
    const { text, sourceSystem = "wylie", targetSystem = "tibetan", context = "common" } =
      req.body;
    const userId = req.headers["userid"] as string;

    if (!text) {
      return res.status(400).json({
        success: false,
        error: "Text is required",
      });
    }

    let result: any;

    // Perform conversion based on source and target systems
    if (sourceSystem === "wylie" && targetSystem === "tibetan") {
      result = transliterationService.convertWylieToTibetan(text);
    } else if (sourceSystem === "tibetan" && targetSystem === "wylie") {
      result = transliterationService.convertTibetanToWylie(text);
    } else if (targetSystem === "phonetic") {
      result = transliterationService.convertToPhonetics(text);
    } else {
      result = {
        result: text,
        alternatives: [],
        confidence: 0,
      };
    }

    // Save to Firestore history
    try {
      const db = admin.firestore();
      const userRef = db.collection("users").doc(userId);
      const historyRef = userRef.collection("transliteration_history").doc();

      await historyRef.set({
        sourceText: text,
        sourceSystem,
        targetSystem,
        result: result.result,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        savedByUser: false,
      });
    } catch (dbError) {
      console.error("Error saving transliteration to Firestore:", dbError);
    }

    return res.json({
      success: true,
      data: result,
    });
  } catch (error) {
    console.error("Transliteration error:", error);
    return res.status(500).json({
      success: false,
      error: "Transliteration failed",
      message: error instanceof Error ? error.message : "Unknown error",
    });
  }
});

// Transliteration Database Lookup
app.post("/api/transliterate/database/lookup", async (req, res) => {
  try {
    const { query, type = "common", limit = 10 } = req.body;

    if (!query) {
      return res.status(400).json({
        success: false,
        error: "Query is required",
      });
    }

    const results = transliterationService.searchNameDatabase(query);

    return res.json({
      success: true,
      data: {
        results: results.slice(0, limit),
      },
    });
  } catch (error) {
    console.error("Transliteration lookup error:", error);
    return res.status(500).json({
      success: false,
      error: "Lookup failed",
    });
  }
});

// Enhanced Chat with Tutoring Support
app.post("/api/chat/message", aiLimiter, async (req, res) => {
  try {
    const {
      sessionId,
      message,
      conversationMode = "general",
      tutoringLevel = "intermediate",
      documentContext,
      includeExplanation = false,
    } = req.body;
    const userId = req.headers["userid"] as string;

    if (!message) {
      return res.status(400).json({
        success: false,
        error: "Message is required",
      });
    }

    const currentSessionId = sessionId || userId;

    // Create or update session with mode
    const chat = await ChatSessionManager.getOrCreateSession(
      currentSessionId,
      conversationMode as any,
      tutoringLevel as any,
      documentContext
    );

    // Send message to Claude
    const model = resolveModel(
      (req.body.model as string) || (req.headers["x-model"] as string)
    );
    const tibetanResponse = await ChatSessionManager.sendMessage(
      currentSessionId,
      message,
      { model, mode: conversationMode as any }
    );

    // Save message to Firestore
    try {
      const db = admin.firestore();
      const conversationRef = db
        .collection("users")
        .doc(userId)
        .collection("conversations")
        .doc(currentSessionId);

      // Create or update conversation document
      await conversationRef.set(
        {
          mode: conversationMode,
          tutoringLevel: conversationMode === "tutoring" ? tutoringLevel : null,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          messageCount: admin.firestore.FieldValue.increment(1),
          preview: message.substring(0, 100),
          lastMessageTime: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );

      // Add message to subcollection
      const messagesRef = conversationRef.collection("messages").doc();
      await messagesRef.set({
        sender: "user",
        content: message,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        mode: conversationMode,
      });

      // Add response
      const responseRef = conversationRef.collection("messages").doc();
      await responseRef.set({
        sender: "assistant",
        content: tibetanResponse,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
        mode: conversationMode,
        confidence: 0.92,
      });
    } catch (dbError) {
      console.error("Error saving chat to Firestore:", dbError);
    }

    const response: GeminiChatResponse = {
      success: true,
      data: {
        response: tibetanResponse,
        sessionId: currentSessionId,
        messageId: `msg_${Date.now()}`,
      },
      usage: {
        charactersUsed: message.length + tibetanResponse.length,
      },
    };

    return res.json(response);
  } catch (error) {
    console.error("Enhanced chat error:", error);
    return res.status(500).json({
      success: false,
      error: "Chat failed",
      message: error instanceof Error ? error.message : "Unknown error",
    });
  }
});

// Chat History Endpoint
app.get("/api/chat/history", async (req, res) => {
  try {
    const userId = req.headers["userid"] as string;
    const limit = parseInt(req.query.limit as string) || 20;
    const offset = parseInt(req.query.offset as string) || 0;

    const db = admin.firestore();
    const conversationsRef = db.collection("users").doc(userId).collection("conversations");

    let query: any = conversationsRef.orderBy("updatedAt", "desc").limit(limit);

    if (offset > 0) {
      query = query.offset(offset);
    }

    const snapshot = await query.get();
    const conversations = snapshot.docs.map((doc: any) => ({
      conversationId: doc.id,
      ...doc.data(),
    }));

    return res.json({
      success: true,
      data: {
        conversations,
        totalCount: snapshot.size,
      },
    });
  } catch (error) {
    console.error("Chat history error:", error);
    return res.status(500).json({
      success: false,
      error: "Failed to fetch history",
    });
  }
});

// Tutoring Mode Configuration
app.post("/api/chat/tutoring/mode", async (req, res) => {
  try {
    const { sessionId, enabled, level = "beginner", topic = "grammar" } = req.body;
    const userId = req.headers["userid"] as string;

    if (enabled) {
      ChatSessionManager.updateSessionMode(sessionId || userId, "tutoring", level as any);

      const curriculum = ChatSessionManager.getTutoringCurriculum(level as any);

      return res.json({
        success: true,
        data: {
          curriculum,
          systemPrompt: `Tutoring mode activated for ${level} level`,
        },
      });
    } else {
      ChatSessionManager.updateSessionMode(sessionId || userId, "general");
      return res.json({
        success: true,
        message: "Tutoring mode disabled",
      });
    }
  } catch (error) {
    console.error("Tutoring mode error:", error);
    return res.status(500).json({
      success: false,
      error: "Failed to configure tutoring mode",
    });
  }
});

// ============================================
// JOURNEY (typing streak) — community comparison
// ============================================

// Numbers-only, opt-in weekly sync for the app's typing-streak feature.
// PRIVACY: the request carries exactly two aggregate integers (words typed this
// week + streak length). No typed content ever reaches this endpoint, and the
// stored doc holds only those numbers. Percentile = share of other participants
// with a lower weekly word count, computed with cheap aggregate count queries.
app.post("/journey/weekly", async (req, res) => {
  try {
    const userId = req.headers["userid"] as string;
    if (!userId || typeof userId !== "string" || userId.length > 128) {
      return res.status(400).json({
        success: false,
        error: "userid header required",
      });
    }

    const wordsThisWeek = Number(req.body?.wordsThisWeek);
    const streakDays = Number(req.body?.streakDays);
    const validCount = (n: number, max: number) =>
      Number.isInteger(n) && n >= 0 && n <= max;
    if (!validCount(wordsThisWeek, 1_000_000) || !validCount(streakDays, 36_500)) {
      return res.status(400).json({
        success: false,
        error: "wordsThisWeek and streakDays must be non-negative integers",
      });
    }

    const db = admin.firestore();
    const col = db.collection("journey_stats");
    await col.doc(userId).set(
      {
        wordsThisWeek,
        streakDays,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    const [totalSnap, belowSnap] = await Promise.all([
      col.count().get(),
      col.where("wordsThisWeek", "<", wordsThisWeek).count().get(),
    ]);
    const totalUsers = totalSnap.data().count;
    const below = belowSnap.data().count;
    // Share of *other* participants this user out-typed. A lone first user beats 100%.
    const percentile =
      totalUsers <= 1 ? 100 : Math.round((below / (totalUsers - 1)) * 100);

    logger.info("Journey weekly sync", { userId, wordsThisWeek, streakDays, percentile });

    return res.json({
      success: true,
      data: { percentile, totalUsers },
    });
  } catch (error) {
    console.error("Journey weekly sync error:", error);
    return res.status(500).json({
      success: false,
      error: "Failed to record weekly stats",
    });
  }
});

// Error handling middleware
app.use(errorHandler);

// Export the v2 function with specific configuration
export const api = onRequest(
  {
    cors: true,
    region: "asia-south1", // Mumbai region
    maxInstances: 10,
    timeoutSeconds: 60,
    memory: "256MiB",
    secrets: [
      anthropicApiKey,
      azureTranslatorKey,
      revenueCatWebhookSecret,
      revenueCatApiKey,
    ],
    // Add additional options if needed
    // invoker: 'public', // Makes function publicly accessible
    // secrets: [], // Add secrets if needed
    // serviceAccount: '', // Custom service account if needed
  },
  app
);
