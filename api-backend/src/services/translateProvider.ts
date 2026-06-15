import {
  HAIKU_MODEL,
  SONNET_MODEL,
  translateWithClaude,
} from "./anthropicService";
import { isAzureConfigured, translateWithAzure } from "./azureTranslateService";
import * as logger from "firebase-functions/logger";

/**
 * Translation engine orchestration. The client picks one of three engines; the
 * Azure engine transparently falls back to Claude on any failure (or when no
 * Azure key is configured yet), so translation keeps working end-to-end.
 */

export const ENGINE_AZURE = "azure";
export type TranslateEngine =
  | typeof ENGINE_AZURE
  | typeof HAIKU_MODEL
  | typeof SONNET_MODEL;

/** Default engine — Azure first, Claude as the safety net. */
export const DEFAULT_ENGINE: TranslateEngine = ENGINE_AZURE;

/** Claude model used when Azure is selected but fails/unconfigured. */
export const AZURE_FALLBACK_MODEL = HAIKU_MODEL;

const ALLOWED_ENGINES = new Set<string>([
  ENGINE_AZURE,
  HAIKU_MODEL,
  SONNET_MODEL,
]);

/** Map a client-supplied engine id/alias to an allowed engine. */
export function resolveEngine(requested?: string | null): TranslateEngine {
  if (!requested) return DEFAULT_ENGINE;
  const value = requested.trim();
  if (ALLOWED_ENGINES.has(value)) return value as TranslateEngine;
  const lower = value.toLowerCase();
  if (lower === "azure" || lower === "microsoft") return ENGINE_AZURE;
  if (lower === "haiku") return HAIKU_MODEL;
  if (lower === "sonnet") return SONNET_MODEL;
  return DEFAULT_ENGINE;
}

export interface TranslateOutcome {
  /** The translated text. */
  text: string;
  /** Engine that actually produced the text (differs from the request on fallback). */
  engineUsed: string;
  /** True when an Azure request fell back to Claude. */
  fellBack: boolean;
}

/**
 * Translate `text` using the requested engine.
 *  - "azure": Azure Translator, falling back to Claude on any error.
 *  - a Claude model id: Claude directly.
 */
export async function translate(
  engine: TranslateEngine,
  text: string,
  sourceLang: string,
  targetLang: string
): Promise<TranslateOutcome> {
  if (engine === ENGINE_AZURE) {
    if (isAzureConfigured()) {
      try {
        const azureText = await translateWithAzure(text, sourceLang, targetLang);
        return { text: azureText, engineUsed: ENGINE_AZURE, fellBack: false };
      } catch (error) {
        logger.warn("Azure translate failed, falling back to Claude", {
          message: error instanceof Error ? error.message : String(error),
        });
      }
    } else {
      logger.info("Azure not configured, using Claude for translation");
    }
    const claudeText = await translateWithClaude(
      AZURE_FALLBACK_MODEL,
      text,
      sourceLang,
      targetLang
    );
    return { text: claudeText, engineUsed: AZURE_FALLBACK_MODEL, fellBack: true };
  }

  // A Claude model was explicitly requested.
  const claudeText = await translateWithClaude(
    engine,
    text,
    sourceLang,
    targetLang
  );
  return { text: claudeText, engineUsed: engine, fellBack: false };
}
