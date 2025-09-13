import { Translate } from "@google-cloud/translate/build/src/v2";

const translate = new Translate({
  projectId: process.env.GOOGLE_CLOUD_PROJECT_ID,
  keyFilename: process.env.GOOGLE_APPLICATION_CREDENTIALS,
});

export async function translateText(
  text: string,
  from: string,
  to: string
): Promise<string> {
  try {
    const [translation] = await translate.translate(text, {
      from: from === "auto" ? undefined : from,
      to,
    });

    return Array.isArray(translation) ? translation[0] : translation;
  } catch (error) {
    console.error("Google Translate API error:", error);
    throw new Error("Translation service unavailable");
  }
}
