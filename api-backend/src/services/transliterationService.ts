interface TransliterationResult {
  result: string;
  alternatives: Array<{
    text: string;
    frequency: number;
    context: string;
  }>;
  confidence: number;
  pronunciation?: string;
}

// Comprehensive Wylie to Tibetan mapping
const WYLIE_TO_TIBETAN: { [key: string]: string } = {
  // Consonants
  ka: "ཀ",
  kha: "ཁ",
  ga: "ག",
  nga: "ང",
  ca: "ཅ",
  cha: "ཆ",
  ja: "ཇ",
  nya: "ཉ",
  ta: "ཏ",
  tha: "ཐ",
  da: "ད",
  na: "ན",
  pa: "པ",
  pha: "ཕ",
  ba: "བ",
  ma: "མ",
  tsa: "ཙ",
  tsha: "ཚ",
  dza: "ཛ",
  wa: "ཝ",
  zha: "ཞ",
  za: "ཟ",
  "a": "ཨ",
  ya: "ཡ",
  ra: "ར",
  la: "ལ",
  sha: "ཤ",
  sa: "ས",
  ha: "ཧ",

  // Vowels
  i: "ི",
  u: "ུ",
  e: "ེ",
  o: "ོ",

  // Special marks
  "aa": "ཱ",
  "n": "ྙ",
};

// Tibetan to Wylie mapping (reverse)
const TIBETAN_TO_WYLIE: { [key: string]: string } = {
  ཀ: "ka",
  ཁ: "kha",
  ག: "ga",
  ང: "nga",
  ཅ: "ca",
  ཆ: "cha",
  ཇ: "ja",
  ཉ: "nya",
  ཏ: "ta",
  ཐ: "tha",
  ད: "da",
  ན: "na",
  པ: "pa",
  ཕ: "pha",
  བ: "ba",
  མ: "ma",
  ཙ: "tsa",
  ཚ: "tsha",
  ཛ: "dza",
  ཝ: "wa",
  ཞ: "zha",
  ཟ: "za",
  ཨ: "a",
  ཡ: "ya",
  ར: "ra",
  ལ: "la",
  ཤ: "sha",
  ས: "sa",
  ཧ: "ha",
};

// Popular Tibetan names database
const TIBETAN_NAMES: { [key: string]: { tibetan: string; wylie: string; meaning: string } } =
  {
    dpal_ldan: {
      tibetan: "དཔལ་ལྡན།",
      wylie: "dpal ldan",
      meaning: "Glorious, Auspicious",
    },
    tenzin: {
      tibetan: "བསྟན་འཛིན།",
      wylie: "bstan 'dzin",
      meaning: "Holder of the teachings",
    },
    kunchok: {
      tibetan: "དགུན་ཆོག།",
      wylie: "dgun chog",
      meaning: "Three precious ones",
    },
    sonam: {
      tibetan: "བསོད་ནམས།",
      wylie: "bsod nams",
      meaning: "Merit, Virtue",
    },
    pema: {
      tibetan: "པདྨ།",
      wylie: "padma",
      meaning: "Lotus",
    },
    dorje: {
      tibetan: "རྡོ་རྗེ།",
      wylie: "rdo rje",
      meaning: "Thunderbolt, Adamantine",
    },
  };

class TransliterationService {
  convertWylieToTibetan(text: string): TransliterationResult {
    try {
      let result = text.toLowerCase();
      let confidence = 0.9;

      // Sort by length (longest first) to handle multi-character combinations first
      const sortedKeys = Object.keys(WYLIE_TO_TIBETAN).sort(
        (a, b) => b.length - a.length
      );

      for (const wylie of sortedKeys) {
        const regex = new RegExp(wylie, "gi");
        result = result.replace(regex, WYLIE_TO_TIBETAN[wylie]);
      }

      // Check if it's a known name
      const nameKey = text.toLowerCase().replace(/\s+/g, "_");
      const nameEntry = TIBETAN_NAMES[nameKey];

      return {
        result,
        alternatives: nameEntry
          ? [
              {
                text: nameEntry.tibetan,
                frequency: 0.95,
                context: "historical_name",
              },
            ]
          : [],
        confidence,
        pronunciation: this.generatePronunciation(text),
      };
    } catch (error) {
      console.error("Wylie to Tibetan conversion error:", error);
      return {
        result: text,
        alternatives: [],
        confidence: 0,
      };
    }
  }

  convertTibetanToWylie(text: string): TransliterationResult {
    try {
      let result = "";
      let confidence = 0.85;

      for (const char of text) {
        result += TIBETAN_TO_WYLIE[char] || char;
      }

      return {
        result,
        alternatives: [],
        confidence,
      };
    } catch (error) {
      console.error("Tibetan to Wylie conversion error:", error);
      return {
        result: text,
        alternatives: [],
        confidence: 0,
      };
    }
  }

  convertToPhonetics(tibetanText: string): TransliterationResult {
    try {
      // Convert to Wylie first, then to phonetics
      const wylieResult = this.convertTibetanToWylie(tibetanText);

      // Simple phonetic rules (can be enhanced)
      let phonetic = wylieResult.result
        .replace(/kh/g, "KH")
        .replace(/ch/g, "CH")
        .replace(/sh/g, "SH")
        .replace(/th/g, "TH")
        .replace(/ts/g, "TS");

      return {
        result: phonetic,
        alternatives: [],
        confidence: 0.7,
      };
    } catch (error) {
      console.error("Phonetic conversion error:", error);
      return {
        result: tibetanText,
        alternatives: [],
        confidence: 0,
      };
    }
  }

  searchNameDatabase(query: string): Array<{ tibetan: string; wylie: string; meaning: string }> {
    const results = [];
    const queryLower = query.toLowerCase();

    for (const [key, value] of Object.entries(TIBETAN_NAMES)) {
      if (
        key.includes(queryLower) ||
        value.wylie.includes(query) ||
        value.meaning.toLowerCase().includes(queryLower)
      ) {
        results.push(value);
      }
    }

    return results;
  }

  private generatePronunciation(text: string): string {
    // Generate basic pronunciation guide
    return text
      .replace(/kh/gi, "KH")
      .replace(/ch/gi, "CH")
      .replace(/sh/gi, "SH")
      .replace(/zh/gi, "ZH")
      .toUpperCase();
  }

  validateTibetanText(text: string): boolean {
    // Check if text contains Tibetan Unicode characters
    const tibetanRegex = /[\u0F00-\u0FFF]/;
    return tibetanRegex.test(text);
  }

  validateWylieText(text: string): boolean {
    // Check if text is valid Wylie (letters and spaces)
    const wylieRegex = /^[a-zA-Z\s'-]+$/;
    return wylieRegex.test(text);
  }
}

export default new TransliterationService();
export { TransliterationResult };
