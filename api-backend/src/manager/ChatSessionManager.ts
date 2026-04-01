import { GoogleGenerativeAI } from "@google/generative-ai";

interface ChatSession {
  chat: any;
  mode: "general" | "tutoring" | "translation";
  tutoringLevel?: "beginner" | "intermediate" | "advanced";
  documentContext?: string;
  createdAt: Date;
}

interface TutoringCurriculum {
  currentLesson: string;
  nextLesson: string;
  progressPercentage: number;
  focusAreas: string[];
}

class ChatSessionManager {
  private static sessions = new Map<string, ChatSession>();
  private static genAI = new GoogleGenerativeAI(
    process.env.GEMINI_API_KEY || "AIzaSyCxUMaoBVH5SIII7Wa0uQYvjrjI9IjV9cg"
  );

  static async getOrCreateSession(
    sessionId: string,
    mode: "general" | "tutoring" | "translation" = "general",
    tutoringLevel?: "beginner" | "intermediate" | "advanced",
    documentContext?: string
  ) {
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

      // Generate system instructions based on mode
      const systemInstructions = this.getSystemInstructions(
        mode,
        tutoringLevel,
        documentContext
      );

      try {
        // Initialize with system instructions
        await chat.sendMessage(systemInstructions);
        this.sessions.set(sessionId, {
          chat,
          mode,
          tutoringLevel,
          documentContext,
          createdAt: new Date(),
        });
        console.log(`New chat session created: ${sessionId}, mode: ${mode}`);
      } catch (error) {
        console.error("Failed to initialize chat session:", error);
        throw new Error("Failed to initialize chat session");
      }
    }

    return this.sessions.get(sessionId)?.chat;
  }

  static async sendMessage(
    sessionId: string,
    message: string,
    mode?: "general" | "tutoring" | "translation"
  ): Promise<string> {
    try {
      const chat = await this.getOrCreateSession(sessionId, mode);
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

  static getSessionInfo(sessionId: string): ChatSession | undefined {
    return this.sessions.get(sessionId);
  }

  static updateSessionMode(
    sessionId: string,
    mode: "general" | "tutoring" | "translation",
    tutoringLevel?: "beginner" | "intermediate" | "advanced"
  ) {
    const session = this.sessions.get(sessionId);
    if (session) {
      session.mode = mode;
      session.tutoringLevel = tutoringLevel;
    }
  }

  static setDocumentContext(sessionId: string, context: string) {
    const session = this.sessions.get(sessionId);
    if (session) {
      session.documentContext = context;
    }
  }

  private static getSystemInstructions(
    mode: "general" | "tutoring" | "translation",
    tutoringLevel?: "beginner" | "intermediate" | "advanced",
    documentContext?: string
  ): string {
    let baseInstructions = `Instructions:
- You are Lundup, an expert in Tibetan language and culture.
- You must respond ONLY in Tibetan script (བོད་ཡིག་).
- Do not respond in any language other than Tibetan script.
- Be respectful and culturally sensitive.`;

    let modeSpecific = "";

    switch (mode) {
      case "tutoring":
        if (tutoringLevel === "beginner") {
          modeSpecific = `\n\nTutoring Mode (Beginner Level):
- Use simple, clear Tibetan script.
- Explain grammar concepts with basic examples.
- Provide encouragement and positive feedback.
- Build foundational vocabulary.
- Ask comprehension questions after explanations.
- Break down complex concepts into simple steps.`;
        } else if (tutoringLevel === "intermediate") {
          modeSpecific = `\n\nTutoring Mode (Intermediate Level):
- Introduce moderately complex grammar structures.
- Discuss cultural context and nuances.
- Encourage creative writing in Tibetan.
- Provide detailed explanations with examples.
- Introduce idioms and common expressions.
- Suggest improvements to their writing.`;
        } else {
          modeSpecific = `\n\nTutoring Mode (Advanced Level):
- Explore advanced grammar and syntax.
- Discuss literary works and classical Tibetan.
- Provide rigorous explanations.
- Encourage scholarly discussion.
- Support academic writing in Tibetan.
- Introduce regional variations and dialects.`;
        }
        break;

      case "translation":
        modeSpecific = `\n\nTranslation Mode:
- Act as an expert Tibetan-English translator.
- Provide accurate, nuanced translations.
- Offer multiple translation options when appropriate.
- Explain translation choices and cultural implications.
- Support translations in both directions.
- Maintain cultural and contextual accuracy.`;
        break;

      case "general":
      default:
        modeSpecific = `\n\nGeneral Chat Mode:
- Engage in helpful, friendly conversation.
- Answer questions about Tibetan language and culture.
- Provide information and guidance.
- Be conversational and natural.`;
    }

    let documentInfo = "";
    if (documentContext) {
      documentInfo = `\n\nDocument Context:
The user is discussing a document with the following content (first 1000 chars):
"${documentContext.substring(0, 1000)}..."
Reference this context when relevant to their questions.`;
    }

    return baseInstructions + modeSpecific + documentInfo;
  }

  static getTutoringCurriculum(level: "beginner" | "intermediate" | "advanced"): TutoringCurriculum {
    const curricula = {
      beginner: {
        currentLesson: "lesson_1_alphabet",
        nextLesson: "lesson_2_basic_verbs",
        progressPercentage: 15,
        focusAreas: ["alphabet", "basic_verbs", "pronouns", "simple_sentences"],
      },
      intermediate: {
        currentLesson: "lesson_5_complex_grammar",
        nextLesson: "lesson_6_idioms",
        progressPercentage: 50,
        focusAreas: ["complex_grammar", "verb_forms", "idiomatic_expressions", "writing_styles"],
      },
      advanced: {
        currentLesson: "lesson_10_classical_tibetan",
        nextLesson: "lesson_11_regional_dialects",
        progressPercentage: 85,
        focusAreas: ["classical_literature", "dialects", "academic_writing", "cultural_nuances"],
      },
    };

    return curricula[level];
  }
}

// Generate unique session ID
function generateSessionId(): string {
  return `chat_${Date.now()}_${Math.random().toString(36).substring(2, 15)}`;
}

export { ChatSessionManager, generateSessionId };
