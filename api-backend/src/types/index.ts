export interface ApiResponse<T = any> {
  success: boolean;
  data?: T;
  error?: string;
  message?: string;
  usage?: {
    charactersUsed: number;
    remainingCharacters: number;
  };
}

export interface TranslateRequest {
  text: string;
  sourceLang: string;
  targetLang: string;
}

export interface GrammarRequest {
  text: string;
}

export interface GrammarResponse {
  correctedText: string;
  corrections: Array<{
    original: string;
    corrected: string;
    type: string;
    position: number;
  }>;
  confidence: number;
}

export interface UserLimits {
  translationLimit: number;
  grammarLimit: number;
  translationUsed: number;
  grammarUsed: number;
  resetDate: string;
}

export interface GeminiChatRequest {
  message: string;
  sessionId?: string;
  resetChat?: boolean;
}

export interface GeminiChatResponse {
  success: boolean;
  data?: {
    response: string;
    sessionId: string;
  };
  error?: string;
  message?: string;
  usage?: {
    charactersUsed: number;
    remainingCharacters: number;
  };
}
