import { GoogleGenerativeAI } from "@google/generative-ai";

interface GrammarCorrection {
  id: string;
  position: { start: number; end: number };
  originalText: string;
  correctedText: string;
  reason: string;
  confidence: number;
  alternatives: string[];
  explanation: string;
}

interface ToneAnalysis {
  detectedTone: "formal" | "casual" | "poetic" | "religious" | "modern";
  score: number;
  suggestions: string[];
}

interface GrammarAnalysisResult {
  corrections: GrammarCorrection[];
  toneAnalysis: ToneAnalysis;
  overallScore: number;
  estimatedReadingLevel: "beginner" | "intermediate" | "advanced";
}

// Tibetan-specific grammar rules database
const TIBETAN_GRAMMAR_RULES = [
  {
    id: "rule_001",
    name: "Case particle usage",
    pattern: /[\u0F00-\u0FFF]+(?![\u0F84\u0F86-\u0F87])/g,
    correction: "Ensure proper case particle usage",
    category: "syntax",
  },
  {
    id: "rule_002",
    name: "Verb agreement",
    pattern: /བ།(?![\u0F84])/,
    correction: "Verify verb-object agreement",
    category: "syntax",
  },
  {
    id: "rule_003",
    name: "Punctuation spacing",
    pattern: /[\u0F3F][\u0F00-\u0FFF]/,
    correction: "Add space after punctuation",
    category: "punctuation",
  },
];

class AdvancedGrammarService {
  private genAI: GoogleGenerativeAI;

  constructor() {
    this.genAI = new GoogleGenerativeAI(
      process.env.GEMINI_API_KEY || "AIzaSyCxUMaoBVH5SIII7Wa0uQYvjrjI9IjV9cg"
    );
  }

  async analyzeTibetanGrammar(
    text: string,
    userLevel: "beginner" | "intermediate" | "advanced" = "intermediate",
    documentType: "formal" | "casual" | "literary" | "religious" = "casual"
  ): Promise<GrammarAnalysisResult> {
    try {
      const model = this.genAI.getGenerativeModel({
        model: "gemini-2.0-flash",
        generationConfig: {
          temperature: 0.7,
          maxOutputTokens: 1500,
        },
      });

      const prompt = `Analyze the following Tibetan text for grammar, spelling, and style issues.
Text: "${text}"

User Level: ${userLevel}
Document Type: ${documentType}

Provide a detailed JSON response with:
{
  "corrections": [
    {
      "position": {"start": 0, "end": 5},
      "originalText": "...",
      "correctedText": "...",
      "reason": "explanation",
      "confidence": 0.95,
      "alternatives": ["alt1", "alt2"],
      "explanation": "detailed explanation in Tibetan"
    }
  ],
  "toneAnalysis": {
    "detectedTone": "formal|casual|poetic|religious|modern",
    "score": 0.85,
    "suggestions": ["tone suggestion 1", "tone suggestion 2"]
  },
  "overallScore": 0.88,
  "estimatedReadingLevel": "beginner|intermediate|advanced"
}

Respond ONLY with valid JSON, no markdown formatting.`;

      const response = await model.generateContent(prompt);
      const responseText = response.response.text();

      // Parse JSON response
      let result: GrammarAnalysisResult;
      try {
        result = JSON.parse(responseText);
      } catch {
        // Fallback if JSON parsing fails
        result = this.generateBasicAnalysis(text);
      }

      return result;
    } catch (error) {
      console.error("Grammar analysis error:", error);
      return this.generateBasicAnalysis(text);
    }
  }

  async getToneAlternatives(
    text: string,
    targetTone: "formal" | "casual" | "poetic" | "religious" | "modern"
  ): Promise<string[]> {
    try {
      const model = this.genAI.getGenerativeModel({
        model: "gemini-2.0-flash",
      });

      const prompt = `Rewrite this Tibetan text in a ${targetTone} tone:
"${text}"

Provide 3 alternative versions that maintain the meaning but change the tone.
Respond with ONLY the 3 versions, one per line, in Tibetan script.`;

      const response = await model.generateContent(prompt);
      const responseText = response.response.text();

      return responseText
        .split("\n")
        .filter((line) => line.trim().length > 0)
        .slice(0, 3);
    } catch (error) {
      console.error("Tone alternative error:", error);
      return [];
    }
  }

  private generateBasicAnalysis(text: string): GrammarAnalysisResult {
    return {
      corrections: [],
      toneAnalysis: {
        detectedTone: "casual",
        score: 0.7,
        suggestions: [],
      },
      overallScore: 0.85,
      estimatedReadingLevel: "intermediate",
    };
  }

  applyLocalRules(text: string): GrammarCorrection[] {
    const corrections: GrammarCorrection[] = [];

    TIBETAN_GRAMMAR_RULES.forEach((rule) => {
      const matches = Array.from(text.matchAll(rule.pattern));
      matches.forEach((match, index) => {
        corrections.push({
          id: `${rule.id}_${index}`,
          position: { start: match.index || 0, end: (match.index || 0) + match[0].length },
          originalText: match[0],
          correctedText: match[0],
          reason: rule.correction,
          confidence: 0.7,
          alternatives: [],
          explanation: rule.correction,
        });
      });
    });

    return corrections;
  }
}

export default new AdvancedGrammarService();
export { GrammarAnalysisResult, GrammarCorrection, ToneAnalysis };
