import { onRequest } from "firebase-functions/v2/https";
import { setGlobalOptions } from "firebase-functions/v2";
import * as admin from "firebase-admin";
import express from "express";
import cors from "cors";
import helmet from "helmet";
import rateLimit from "express-rate-limit";
import { checkGrammar } from "./services/grammarService";
import {
  validateApiKey,
  checkUserLimits,
  updateUserUsage,
} from "./middleware/auth";
import {
  validateTranslateRequest,
  validateGrammarRequest,
  validateChatRequest,
} from "./middleware/validation";
import { errorHandler } from "./middleware/errorHandler";
import {
  ApiResponse,
  TranslateRequest,
  GrammarRequest,
  GeminiChatRequest,
  GeminiChatResponse,
} from "./types";
import { translateText } from "./services/translationService";
import { freeTranslateText } from "./services/freeTranslationService";
import { log } from "console";
import {
  ChatSessionManager,
  generateSessionId,
} from "./manager/ChatSessionManager";
import { user } from "firebase-functions/v1/auth";

// Set global options for all functions
setGlobalOptions({
  region: "asia-south1", // Mumbai region
  maxInstances: 10,
  timeoutSeconds: 60,
  memory: "256MiB",
});

// Initialize Firebase Admin
admin.initializeApp();

const app = express();

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

// Health check endpoint
app.get("/health", (req, res) => {
  res.json({ status: "healthy", timestamp: new Date().toISOString() });
});

// Translation endpoint
app.post(
  "/translate",
  //validateApiKey,
  validateTranslateRequest,
  //checkUserLimits("translation"),
  async (req, res) => {
    try {
      console.log("Received translation request:", req.body);

      const { text, sourceLang, targetLang }: TranslateRequest = req.body;
      //const userId = (req as any).userId;
      const userId = req.headers["userid"] as string;

      console.log(
        `Translating text for user ${userId} from ${sourceLang} to ${targetLang}`
      );

      //const translatedText = await translateText(text, from, to);
      const translatedText = await freeTranslateText(
        text,
        sourceLang,
        targetLang
      );
      console.log("Translation successful:", translatedText);

      // Update user usage
      //await updateUserUsage(userId, "translation", text.length);

      const response: ApiResponse<{ translatedText: string }> = {
        success: true,
        data: { translatedText },
        usage: {
          charactersUsed: text.length,
          remainingCharacters: (req as any).remainingCredits - text.length,
        },
      };

      res.json(response);
    } catch (error) {
      console.error("Translation error:", error);
      res.status(500).json({
        success: false,
        error: "Translation failed",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  }
);

// Chat with gemini

app.post(
  "/chat",
  //validateApiKey,
  validateChatRequest,
  //checkUserLimits("translation"),
  async (req, res) => {
    try {
      console.log("Received chat request:", req.body);

      const { message, sessionId, resetChat }: GeminiChatRequest = req.body;
      const userId = req.headers["userid"] as string;

      // Generate or use provided session ID
      //const currentSessionId = sessionId || generateSessionId();
      const currentSessionId = userId;
      console.log(
        `Processing chat for user ${userId}, session: ${currentSessionId}`
      );

      // Reset chat session if requested
      if (resetChat) {
        ChatSessionManager.resetSession(currentSessionId);
      }

      // Send message to Gemini and get Tibetan response
      const tibetanResponse = await ChatSessionManager.sendMessage(
        currentSessionId,
        message
      );

      console.log("Chat response generated successfully");

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
      console.error("Chat error:", error);

      res.status(500).json({
        success: false,
        error: "Chat failed",
        message:
          error instanceof Error ? error.message : "Unknown error occurred",
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

// Grammar check endpoint
app.post(
  "/grammar",
  //validateApiKey,
  validateGrammarRequest,
  checkUserLimits("grammar"),
  async (req, res) => {
    try {
      const { text }: GrammarRequest = req.body;
      const userId = (req as any).userId;

      const grammarResult = await checkGrammar(text);

      // Update user usage
      //await updateUserUsage(userId, "grammar", text.length);

      const response: ApiResponse<typeof grammarResult> = {
        success: true,
        data: grammarResult,
        usage: {
          charactersUsed: text.length,
          remainingCharacters: (req as any).remainingCredits - text.length,
        },
      };

      res.json(response);
    } catch (error) {
      console.error("Grammar check error:", error);
      res.status(500).json({
        success: false,
        error: "Grammar check failed",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  }
);

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
    // Add additional options if needed
    // invoker: 'public', // Makes function publicly accessible
    // secrets: [], // Add secrets if needed
    // serviceAccount: '', // Custom service account if needed
  },
  app
);
