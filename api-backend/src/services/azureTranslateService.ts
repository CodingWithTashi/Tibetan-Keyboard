import axios from "axios";

/**
 * Azure Translator (Cognitive Services) Text Translation v3.0.
 *
 * Configured via env (injected from the AZURE_TRANSLATOR_KEY Firebase secret +
 * plain env for the non-secret region/endpoint):
 *   AZURE_TRANSLATOR_KEY       — subscription key (secret)
 *   AZURE_TRANSLATOR_REGION    — resource region, e.g. "eastus"
 *   AZURE_TRANSLATOR_ENDPOINT  — global text endpoint
 *
 * Tibetan (`bo`) is supported by Azure NMT text translation incl. auto-detect,
 * so this can serve the app's primary bo<->en use case; on any failure the
 * caller (translateProvider) falls back to Claude.
 */

const DEFAULT_ENDPOINT = "https://api.cognitive.microsofttranslator.com";
const DEFAULT_REGION = "eastus";

/** Map the app's language codes to Azure Translator codes. */
function toAzureLang(code: string): string | undefined {
  switch (code) {
    case "auto":
      return undefined; // omit `from` → Azure auto-detects
    case "zh-CN":
      return "zh-Hans"; // Azure uses zh-Hans for Simplified Chinese
    default:
      return code; // en, bo, … pass through
  }
}

export function isAzureConfigured(): boolean {
  return !!process.env.AZURE_TRANSLATOR_KEY;
}

interface AzureTranslationItem {
  translations: Array<{ text: string; to: string }>;
}

/**
 * Translate `text` with Azure Translator. Throws on any error (missing config,
 * HTTP failure, unexpected shape) so the provider layer can fall back to Claude.
 */
export async function translateWithAzure(
  text: string,
  sourceLang: string,
  targetLang: string
): Promise<string> {
  const key = process.env.AZURE_TRANSLATOR_KEY;
  if (!key) throw new Error("AZURE_TRANSLATOR_KEY is not configured");

  const region = process.env.AZURE_TRANSLATOR_REGION || DEFAULT_REGION;
  const endpoint = (
    process.env.AZURE_TRANSLATOR_ENDPOINT || DEFAULT_ENDPOINT
  ).replace(/\/+$/, "");

  const to = toAzureLang(targetLang);
  if (!to) throw new Error(`Unsupported Azure target language: ${targetLang}`);
  const from = toAzureLang(sourceLang);

  const params: Record<string, string> = { "api-version": "3.0", to };
  if (from) params.from = from;

  const response = await axios.post<AzureTranslationItem[]>(
    `${endpoint}/translate`,
    [{ Text: text }],
    {
      params,
      headers: {
        "Ocp-Apim-Subscription-Key": key,
        "Ocp-Apim-Subscription-Region": region,
        "Content-Type": "application/json",
      },
      timeout: 15000,
    }
  );

  const translated = response.data?.[0]?.translations?.[0]?.text;
  if (typeof translated !== "string" || translated.length === 0) {
    throw new Error("Azure Translator returned an empty result");
  }
  return translated;
}
