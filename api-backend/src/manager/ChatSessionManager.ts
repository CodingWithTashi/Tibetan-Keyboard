import { GoogleGenerativeAI } from "@google/generative-ai";

class ChatSessionManager {
  private static sessions = new Map<string, any>();
  private static genAI = new GoogleGenerativeAI(
    process.env.GEMINI_API_KEY || "AIzaSyCxUMaoBVH5SIII7Wa0uQYvjrjI9IjV9cg"
  );

  static async getOrCreateSession(sessionId: string) {
    if (!this.sessions.has(sessionId)) {
      const model = this.genAI.getGenerativeModel({
        model: "gemini-2.0-flash",
        generationConfig: {
          temperature: 0.7,
          topK: 40,
          topP: 0.95,
          maxOutputTokens: 800,
        },
      });

      const chat = model.startChat();

      // System instructions for Tibetan responses
      const systemInstructions = `Instructions:
- You are master in Tibetan script and language.
- The user will may ask questions in english or tibetan.
- You must respond ONLY in Tibetan script (བོད་ཡིག་).
- Do not respond in any language other than Tibetan script.`;

      try {
        // Initialize with system instructions
        await chat.sendMessage(systemInstructions);
        this.sessions.set(sessionId, chat);
        console.log(`New chat session created: ${sessionId}`);
      } catch (error) {
        console.error("Failed to initialize chat session:", error);
        throw new Error("Failed to initialize chat session");
      }
    }

    return this.sessions.get(sessionId);
  }

  static async sendMessage(
    sessionId: string,
    message: string
  ): Promise<string> {
    try {
      const chat = await this.getOrCreateSession(sessionId);
      const result = await chat.sendMessage(message);
      let response = result.response.text().trim();

      // Fallback for empty responses
      if (!response || response.length === 0) {
        response =
          "དགོངས་དག། ལན་འདེབས་དཀའ་ངལ་འཕྲད་སོང་། ཡང་བསྐྱར་འབད་བརྩོན་གནང་རོགས།";
      }

      return response;
    } catch (error) {
      console.error("Error sending message to Gemini:", error);
      // Return Tibetan error message
      return "དགོངས་དག། ཕྱི་ཕྱོགས་དང་འབྲེལ་བའི་དཀའ་ངལ་ཞིག་འཕྲད་སོང་། ཡང་བསྐྱར་འབད་བརྩོན་གནང་རོགས།";
    }
  }

  static resetSession(sessionId: string) {
    this.sessions.delete(sessionId);
    console.log(`Chat session reset: ${sessionId}`);
  }

  static removeSession(sessionId: string) {
    this.sessions.delete(sessionId);
  }
}

// Generate unique session ID
function generateSessionId(): string {
  return `chat_${Date.now()}_${Math.random().toString(36).substring(2, 15)}`;
}

export { ChatSessionManager, generateSessionId };
