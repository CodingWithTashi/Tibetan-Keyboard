import { translate } from "google-translate-api-x";

export async function freeTranslateText(
  text: string,
  from: string,
  to: string
): Promise<string> {
  try {
    const res = await translate(text, { to: to, from: from });
    const translation = res.text;
    return Array.isArray(translation) ? translation[0] : translation;
  } catch (error) {
    console.error("Free Translate API error:", error);
    throw new Error("Translation service unavailable");
  }
}
