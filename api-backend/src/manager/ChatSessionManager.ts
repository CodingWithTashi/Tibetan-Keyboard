import {
  ClaudeMessage,
  createMessage,
  resolveModel,
  DEFAULT_MODEL,
} from "../services/anthropicService";
import { cachedCompute, makeCacheKey } from "../services/cacheService";

type ChatMode = "general" | "tutoring" | "translation";
type TutoringLevel = "beginner" | "intermediate" | "advanced";

interface ChatSession {
  history: ClaudeMessage[];
  mode: ChatMode;
  tutoringLevel?: TutoringLevel;
  documentContext?: string;
  createdAt: Date;
}

interface TutoringCurriculum {
  currentLesson: string;
  nextLesson: string;
  progressPercentage: number;
  focusAreas: string[];
}

interface SendOptions {
  model?: string;
  mode?: ChatMode;
}

// Keep conversation context bounded to control token usage.
const MAX_HISTORY_MESSAGES = 20;

class ChatSessionManager {
  private static sessions = new Map<string, ChatSession>();

  static getOrCreateSession(
    sessionId: string,
    mode: ChatMode = "general",
    tutoringLevel?: TutoringLevel,
    documentContext?: string
  ): ChatSession {
    let session = this.sessions.get(sessionId);
    if (!session) {
      session = {
        history: [],
        mode,
        tutoringLevel,
        documentContext,
        createdAt: new Date(),
      };
      this.sessions.set(sessionId, session);
      console.log(`New chat session created: ${sessionId}, mode: ${mode}`);
    }
    return session;
  }

  static async sendMessage(
    sessionId: string,
    message: string,
    options: SendOptions = {}
  ): Promise<string> {
    const session = this.getOrCreateSession(sessionId, options.mode);
    if (options.mode) session.mode = options.mode;

    const model = resolveModel(options.model);
    const system = this.getSystemInstructions(
      session.mode,
      session.tutoringLevel,
      session.documentContext
    );

    const userTurn: ClaudeMessage = { role: "user", content: message };
    const requestMessages = [...session.history, userTurn].slice(
      -MAX_HISTORY_MESSAGES
    );

    try {
      // Cache keyed by model + system + the exact message sequence, so an
      // identical conversation turn is served from cache instead of re-billing.
      const key = makeCacheKey("chat", { model, system, messages: requestMessages });
      const { value, source } = await cachedCompute(
        key,
        () => createMessage(model, system, requestMessages, 1024),
        { type: "chat", model }
      );

      let response = value;
      if (!response || response.length === 0) {
        response =
          "དགོངས་དག། ལན་འདེབས་དཀའ་ངལ་འཕྲད་སོང་། ཡང་བསྐྱར་འབད་བརྩོན་གནང་རོགས།";
      }
      console.log(`Chat (${model}) served from ${source}`);

      // Persist the turn for conversation continuity.
      const assistantTurn: ClaudeMessage = { role: "assistant", content: response };
      session.history = [...requestMessages, assistantTurn].slice(
        -MAX_HISTORY_MESSAGES
      );

      return response;
    } catch (error) {
      console.error("Error sending message to Claude:", error);
      return "དགོངས་དག། ཕྱི་ཕྱོགས་དང་འབྲེལ་བའི་དཀའ་ངལ་ཞིག་འཕྲད་སོང་། ཡང་བསྐྱར་འབད་བརྩོན་གནང་རོགས།";
    }
  }

  static resetSession(sessionId: string): void {
    this.sessions.delete(sessionId);
    console.log(`Chat session reset: ${sessionId}`);
  }

  static removeSession(sessionId: string): void {
    this.sessions.delete(sessionId);
  }

  static getSessionInfo(sessionId: string): ChatSession | undefined {
    return this.sessions.get(sessionId);
  }

  static updateSessionMode(
    sessionId: string,
    mode: ChatMode,
    tutoringLevel?: TutoringLevel
  ): void {
    const session = this.getOrCreateSession(sessionId);
    session.mode = mode;
    session.tutoringLevel = tutoringLevel;
  }

  static setDocumentContext(sessionId: string, context: string): void {
    const session = this.getOrCreateSession(sessionId);
    session.documentContext = context;
  }

  private static getSystemInstructions(
    mode: ChatMode,
    tutoringLevel?: TutoringLevel,
    documentContext?: string
  ): string {
    const baseInstructions = `Instructions:
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

  static getTutoringCurriculum(level: TutoringLevel): TutoringCurriculum {
    const curricula: Record<TutoringLevel, TutoringCurriculum> = {
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
        focusAreas: [
          "complex_grammar",
          "verb_forms",
          "idiomatic_expressions",
          "writing_styles",
        ],
      },
      advanced: {
        currentLesson: "lesson_10_classical_tibetan",
        nextLesson: "lesson_11_regional_dialects",
        progressPercentage: 85,
        focusAreas: [
          "classical_literature",
          "dialects",
          "academic_writing",
          "cultural_nuances",
        ],
      },
    };

    return curricula[level];
  }
}

// Generate unique session ID
function generateSessionId(): string {
  return `chat_${Date.now()}_${Math.random().toString(36).substring(2, 15)}`;
}

export { ChatSessionManager, generateSessionId, DEFAULT_MODEL };
