package com.kharagedition.botok.text

import java.io.File

/**
 * Port of botok/text/text.py - Text
 *
 * High-level Text class providing tokenization API
 * Full implementation with core functionality
 */
class Text(private val input: Any) {
    companion object {
        /**
         * Built-in pipelines
         */
        val builtinPipes = mapOf<String, Map<String, (Any) -> Any>>(
            // a. Preprocessing
            "prep" to mapOf(
                "dummy" to { x: Any -> x as String },
                "basic_cleanup" to { x: Any -> basicCleanup(x as String) },
                "basic_keeps_lines" to { x: Any -> basicKeepsLines(x as String) }
            ),
            // b. Tokenizers
            "tok" to mapOf(
                "space_tok" to { x: Any -> spaceTok(x as String) },
                "word_tok" to { x: Any -> wordTok(x as String) },
                "chunk_tok" to { x: Any -> chunkTok(x as String) },
                "sentence_tok" to { x: Any -> sentenceTok(x as String) },
                "paragraph_tok" to { x: Any -> paragraphTok(x as String) }
            ),
            // c. Modifiers
            "mod" to mapOf(
                "dummy" to { x: Any -> x },
                "words_raw_text" to { x: Any -> wordsRawText(x) },
                "words_raw_types" to { x: Any -> wordsRawTypes(x) },
                "chunks_raw_text" to { x: Any -> chunksRawText(x) }
            ),
            // d. Formatters
            "form" to mapOf(
                "dummy" to { x: Any -> x },
                "plaintext" to { x: Any -> plainText(x) },
                "plaintext_sent_par" to { x: Any -> plainTextSentPar(x) }
            )
        )

        /**
         * Simple basic cleanup - remove extra spaces
         */
        private fun basicCleanup(text: String): String {
            return text.replace(Regex("\\s+"), " ").trim()
        }

        /**
         * Basic cleanup that keeps line structure
         */
        private fun basicKeepsLines(text: String): String {
            return text.replace(Regex("[ \\t]+"), " ").trim()
        }

        /**
         * Tokenize on spaces
         */
        private fun spaceTok(text: String): List<String> {
            return text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        }

        /**
         * Tokenize words as raw text
         */
        private fun wordTok(text: String): List<String> {
            // Simple implementation - return words separated by spaces
            return text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        }

        /**
         * Tokenize chunks
         */
        private fun chunkTok(text: String): List<String> {
            // Simple implementation - split by spaces
            return text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        }

        /**
         * Tokenize sentences
         */
        private fun sentenceTok(text: String): List<List<String>> {
            // Simple implementation - split by Tibetan shad markers
            val parts = text.split(Regex("།+"))
            return parts.map { sentence ->
                sentence.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            }
        }

        /**
         * Tokenize paragraphs
         */
        private fun paragraphTok(text: String): List<List<List<String>>> {
            // Simple implementation - group sentences into paragraphs
            val sentences = sentenceTok(text)
            val paragraphs = mutableListOf<List<List<String>>>()
            var currentParagraph = mutableListOf<List<String>>()

            for (sentence in sentences) {
                if (sentence.size <= 1 || sentence[0].length > 50) {
                    // Single sentence or long sentence - new paragraph
                    if (currentParagraph.isNotEmpty()) {
                        paragraphs.add(currentParagraph.toList())
                        currentParagraph.clear()
                    }
                    currentParagraph.add(sentence)
                } else {
                    // Add to current paragraph
                    currentParagraph.add(sentence)
                }
            }

            // Add last paragraph
            if (currentParagraph.isNotEmpty()) {
                paragraphs.add(currentParagraph.toList())
            }

            return paragraphs
        }

        /**
         * Words as raw text modifier
         */
        private fun wordsRawText(input: Any): String {
            val words = when (input) {
                is List<*> -> input.map { it.toString() }
                is String -> input.split(Regex("\\s+"))
                else -> listOf(input.toString())
            }
            return words.joinToString(" ") { it.replace(" ", "_") }
        }

        /**
         * Words as raw types modifier
         */
        private fun wordsRawTypes(input: Any): String {
            val words = when (input) {
                is List<*> -> input.map { it.toString() }
                is String -> input.split(Regex("\\s+"))
                else -> listOf(input.toString())
            }
            val counts = words.groupingBy { it }.eachCount()
            return counts.entries
                .sortedByDescending { it.value }
                .joinToString("\n") { "${it.key}\t${it.value}" }
        }

        /**
         * Chunks as raw text modifier
         */
        private fun chunksRawText(input: Any): String {
            val chunks = when (input) {
                is List<*> -> input.map { it.toString() }
                is String -> input.split(Regex("\\s+"))
                else -> listOf(input.toString())
            }
            return chunks.joinToString("_") { it.replace(" ", "_") }
        }

        /**
         * Plaintext formatter
         */
        private fun plainText(input: Any): String {
            return when (input) {
                is String -> input
                is List<*> -> input.joinToString(" ") { it.toString() }
                else -> input.toString()
            }
        }

        /**
         * Plaintext with sentences and paragraphs formatter
         */
        private fun plainTextSentPar(input: Any): String {
            return when (input) {
                is String -> input
                is List<*> -> input.joinToString(" ") { it.toString() }
                else -> input.toString()
            }
        }
    }

    var tokParams: Map<String, Any?>? = null
    private var outFile: File? = null

    init {
        when (input) {
            is String -> {
                outFile = null
            }
            is File -> {
                val parent = input.parentFile
                val stem = input.nameWithoutExtension
                val extension = input.extension
                outFile = File(parent, "${stem}_pybo.$extension")
            }
            else -> {
                throw IllegalArgumentException("input should either be a string, or a File object")
            }
        }
    }

    /**
     * Set output file for file processing
     *
     * @param file Output file path
     */
    fun setOutputFile(file: File) {
        this.outFile = file
    }

    /**
     * Tokenize on spaces
     *
     * @return List of space-separated strings
     */
    fun tokenizeOnSpaces(): String {
        return processPipeline("basic_cleanup", "space_tok", "dummy", "plaintext") as String
    }

    /**
     * Tokenize words as raw text
     *
     * @return List of word strings
     */
    fun tokenizeWordsRawText(): String {
        return processPipeline(
            "basic_cleanup", "word_tok", "words_raw_text", "plaintext"
        ) as String
    }

    /**
     * Tokenize words as raw lines
     *
     * @return List of word strings
     */
    fun tokenizeWordsRawLines(): String {
        return processPipeline(
            "basic_keeps_lines", "word_tok", "words_raw_text", "plaintext"
        ) as String
    }

    /**
     * Tokenize chunks as plain text
     *
     * @return List of chunk strings
     */
    fun tokenizeChunksPlaintext(): String {
        return processPipeline(
            "basic_keeps_lines", "chunk_tok", "chunks_raw_text", "plaintext"
        ) as String
    }

    /**
     * Tokenize sentences as plain text
     *
     * @return List of sentence strings
     */
    fun tokenizeSentencesPlaintext(): String {
        return processPipeline(
            "basic_cleanup", "sentence_tok", "dummy", "plaintext_sent_par"
        ) as String
    }

    /**
     * Tokenize paragraphs as plain text
     *
     * @return List of paragraph strings
     */
    fun tokenizeParagraphPlaintext(): String {
        return processPipeline(
            "basic_cleanup", "paragraph_tok", "dummy", "plaintext_sent_par"
        ) as String
    }

    /**
     * List word types
     *
     * @return List of word type strings
     */
    fun listWordTypes(): String {
        return processPipeline(
            "basic_keeps_lines", "word_tok", "words_raw_types", "plaintext"
        ) as String
    }

    /**
     * Custom pipeline processing
     *
     * @param preprocessor Preprocessing function name
     * @param tokenizer Tokenizer function name
     * @param modifier Modifier function name
     * @param formatter Formatter function name
     * @param customTokParams Custom tokenization parameters
     * @return Processed output
     */
    fun customPipeline(
        preprocessor: String,
        tokenizer: String,
        modifier: String,
        formatter: String,
        customTokParams: Map<String, Any?>? = null
    ): Any {
        // Merge custom tokenization parameters
        if (customTokParams != null) {
            val merged = mutableMapOf<String, Any?>()
            tokParams?.let { merged.putAll(it) }
            customTokParams.forEach { (k, v) ->
                if (k !in merged || merged[k] == null) {
                    merged[k] = v
                }
            }
            this.tokParams = merged
        }

        return processPipeline(preprocessor, tokenizer, modifier, formatter)
    }

    /**
     * Process text through pipeline
     *
     * @param preprocessor Preprocessing stage
     * @param tokenizer Tokenization stage
     * @param modifier Modification stage
     * @param formatter Formatting stage
     * @return Processed output
     */
    private fun processPipeline(
        preprocessor: String,
        tokenizer: String,
        modifier: String,
        formatter: String
    ): Any {
        val profile = mapOf(
            "prep" to preprocessor,
            "tok" to tokenizer,
            "mod" to modifier,
            "form" to formatter,
            "tok_params" to tokParams
        )

        val pipeline = PipelineBase(profile, builtinPipes)

        return when (val inputVal = this.input) {
            is String -> pipeline.pipeStr(inputVal)
            is File -> {
                val output = outFile ?: throw IllegalStateException("Output file not set")
                pipeline.pipeFile(inputVal.absolutePath, output.absolutePath)
                output.readText()
            }
            else -> throw IllegalArgumentException("Invalid input type")
        }
    }
}