package com.kharagedition.botok

import com.kharagedition.botok.text.Text
import com.kharagedition.botok.tokenizers.WordTokenizer
import com.kharagedition.botok.config.Config
import java.io.File

/**
 * Top-level entry point for botok library
 *
 * Provides convenient access to botok functionality for keyboard app
 */
object Botok {
    private var defaultConfig: Config? = null

    /**
     * Initialize botok with default configuration
     */
    fun init() {
        if (defaultConfig == null) {
            defaultConfig = Config("")  // Empty path for default config
        }
    }

    /**
     * Get or create default configuration
     *
     * @return Default configuration
     */
    fun getDefaultConfig(): Config {
        if (defaultConfig == null) {
            defaultConfig = Config("")  // Empty path for default config
        }
        return defaultConfig!!
    }

    /**
     * Tokenize Tibetan text into words
     *
     * @param text Tibetan text to tokenize
     * @param config Optional custom configuration
     * @return List of word strings
     */
    fun tokenizeWords(text: String, config: Config? = null): List<String> {
        val tokenizer = WordTokenizer(config ?: getDefaultConfig())
        return tokenizer.tokenize(text).map { token -> token.text }
    }

    /**
     * Tokenize Tibetan text into syllables
     *
     * @param text Tibetan text to tokenize
     * @param config Optional custom configuration
     * @return List of syllable strings
     */
    fun tokenizeSyllables(text: String, config: Config? = null): List<String> {
        val tokenizer = WordTokenizer(config ?: getDefaultConfig())
        return tokenizer.tokenize(text).flatMap { token ->
            val sylList = token.syls
            if (sylList.isNotEmpty()) {
                sylList.map { syl -> syl.joinToString("") }
            } else {
                listOf(token.text)
            }
        }
    }

    /**
     * Tokenize Tibetan text with detailed token information
     *
     * @param text Tibetan text to tokenize
     * @param config Optional custom configuration
     * @return List of Token objects with full information
     */
    fun tokenizeDetailed(text: String, config: Config? = null): List<com.kharagedition.botok.tokenizers.Token> {
        val tokenizer = WordTokenizer(config ?: getDefaultConfig())
        return tokenizer.tokenize(text)
    }

    /**
     * Create a Text processor for advanced text processing
     *
     * @param input Input text or file
     * @return Text processor
     */
    fun createTextProcessor(input: Any): Text {
        return Text(input)
    }

    /**
     * Check if a syllable is standard Tibetan
     *
     * @param syllable Syllable to check (without tsheg)
     * @return true if syllable is standard Tibetan
     */
    fun isStandardTibetan(syllable: String): Boolean {
        return com.kharagedition.botok.utils.StandardTibetan.isStandardTibetan(syllable)
    }

    /**
     * Split a syllable into its constituent stacks
     *
     * @param syllable Tibetan syllable to split (without tsheg)
     * @return List of constituent stacks
     */
    fun splitIntoStacks(syllable: String): List<String> {
        return com.kharagedition.botok.utils.StandardTibetan.splitIntoStacks(syllable)
    }

    /**
     * Normalize Tibetan text for corpus processing
     *
     * @param text Text to normalize
     * @return Normalized text
     */
    fun normalizeCorpus(text: String): String {
        return com.kharagedition.botok.utils.CorpusNormalization.normalizeCorpus(text)
    }

    /**
     * Normalize Tibetan text for perplexity calculation
     *
     * @param text Text to normalize
     * @param spaceSskt Whether to space Sanskrit syllables
     * @param foldSskt Whether to fold Sanskrit syllables
     * @return Normalized text
     */
    fun normalizeForPerplexity(
        text: String,
        spaceSskt: Boolean = true,
        foldSskt: Boolean = false
    ): String {
        return com.kharagedition.botok.utils.CorpusNormalization.normalizeForPerplexity(
            text, spaceSskt, foldSskt
        )
    }

    /**
     * Remove affixes from a syllable
     *
     * @param syllable Syllable to process
     * @return Syllable with affixes removed
     */
    fun removeAffixes(syllable: String): String {
        return com.kharagedition.botok.utils.LenientNormalization.removeAffixes(syllable)
    }

    /**
     * Process a file through text processing pipeline
     *
     * @param inputFile Input file path
     * @param outputFile Output file path
     * @param processingType Type of processing to apply
     */
    fun processFile(
        inputFile: String,
        outputFile: String,
        processingType: ProcessingType = ProcessingType.WORDS_RAW_TEXT
    ) {
        val file = File(inputFile)
        val text = file.readText()
        val processor = createTextProcessor(text)

        val result = when (processingType) {
            ProcessingType.WORDS_RAW_TEXT -> processor.tokenizeWordsRawText()
            ProcessingType.CHUNKS_PLAINTEXT -> processor.tokenizeChunksPlaintext()
            ProcessingType.SENTENCES_PLAINTEXT -> processor.tokenizeSentencesPlaintext()
            ProcessingType.PARAGRAPHS_PLAINTEXT -> processor.tokenizeParagraphPlaintext()
            ProcessingType.SPACES -> processor.tokenizeOnSpaces()
        }

        File(outputFile).writeText(result.toString())
    }

    /**
     * Processing types for file processing
     */
    enum class ProcessingType {
        WORDS_RAW_TEXT,
        CHUNKS_PLAINTEXT,
        SENTENCES_PLAINTEXT,
        PARAGRAPHS_PLAINTEXT,
        SPACES
    }
}