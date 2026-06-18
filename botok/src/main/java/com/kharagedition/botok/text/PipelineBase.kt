package com.kharagedition.botok.text

import java.io.File

/**
 * Port of botok/text/pipelinebase.py - Pipeline Base
 *
 * Configurable preprocessing → tokenization → modification → formatting pipeline
 */
class PipelineBase(
    private val profile: Map<String, Any?>,
    private val pipes: Map<String, Map<String, (Any) -> Any>>
) {
    var prep: String? = null
    var tok: String? = null
    var mod: String? = null
    var form: String? = null

    var left: Int = 5
    var right: Int =5
    var tokParams: Map<String, Any?>? = null
    var filename: String? = null

    private val argsList = setOf(
        "prep",
        "tok",
        "mod",
        "form",        // components
        "tok_params",  // pybo
        "left",
        "right",       // concs
        "filename"      // others
    )

    init {
        parseProfile(profile)
    }

    /**
     * Process text through configured pipeline
     *
     * Pipeline stages:
     *   a. preprocessing
     *   b. tokenizing
     *   c. modifying
     *   d. formatting
     *
     * @param text Input text to process
     * @return Processed output
     */
    fun pipeStr(text: String): Any {
        var result: Any = text

        // a. preprocessing
        if (prep != null) {
            val prepFn = pipes["prep"]?.get(prep)
            if (prepFn != null) {
                result = (prepFn)(text)
            }
        }

        // b. tokenizing
        if (tok != null) {
            val tokFn = pipes["tok"]?.get(tok)
            if (tokFn != null) {
                val tokValue = tok ?: ""
                val hasTokParams = (tokValue.contains("word") || tokValue.contains("sentence") || tokValue.contains("paragraph")) && tokParams != null
                if (hasTokParams) {
                    @Suppress("UNCHECKED_CAST")
                    result = (tokFn as (String, Map<String, Any?>) -> Any)(result as String, tokParams!!)
                } else {
                    result = (tokFn as (String) -> Any)(result as String)
                }
            }
        }

        // c. modifying
        if (mod != null) {
            val modFn = pipes["mod"]?.get(mod)
            if (modFn != null) {
                val modValue = mod ?: ""
                result = when {
                    modValue.endsWith("concs") ->
                        (modFn as (Any, Int, Int) -> Any)(result, left, right)
                    else ->
                        (modFn as (Any) -> Any)(result)
                }
            }
        }

        // d. formatting
        if (form != null) {
            val formFn = pipes["form"]?.get(form)
            if (formFn != null) {
                result = (formFn as (Any) -> Any)(result)
            }
        }

        return result
    }

    /**
     * Process a file through the configured pipeline
     *
     * @param inputFile Input file path
     * @param outputFile Output file path
     */
    fun pipeFile(inputFile: String, outputFile: String) {
        val inFile = File(inputFile)
        val outFile = File(outputFile)

        check(inFile.exists()) { "Input file does not exist: $inputFile" }

        val dump = inFile.readText(Charsets.UTF_8)

        val output = pipeStr(dump)

        outFile.writeText(output.toString(), Charsets.UTF_8)
    }

    /**
     * Parse profile configuration
     *
     * @param pipeline Profile configuration map
     */
    private fun parseProfile(pipeline: Map<String, Any?>) {
        isValidParams(pipeline)

        for ((arg, value) in pipeline) {
            when (arg) {
                "prep" -> prep = value as? String
                "tok" -> tok = value as? String
                "mod" -> mod = value as? String
                "form" -> form = value as? String
                "tok_params" -> {
                    @Suppress("UNCHECKED_CAST")
                    tokParams = value as? Map<String, Any?>
                }
                "left" -> left = value as? Int ?: left
                "right" -> right = value as? Int ?: right
                "filename" -> filename = value as? String
            }
        }

        isValidPipeline()
    }

    /**
     * Validate profile parameters
     *
     * @param pipeline Profile configuration map
     * @throws SyntaxError if invalid parameters are found
     */
    private fun isValidParams(pipeline: Map<String, Any?>) {
        for ((arg, value) in pipeline) {
            // Ensure all arguments are valid attributes
            if (arg !in argsList) {
                throw SyntaxError(
                    "$arg is not a valid argument\n" +
                    "valid options are ${argsList.joinToString(" ")}"
                )
            }

            // Ensure arguments have valid values
            if (arg in pipes && value is String && value !in pipes[arg]!!.keys) {
                throw SyntaxError(
                    "$value is not a valid value for $arg\n" +
                    "valid options are ${pipes[arg]!!.keys.joinToString(" ")}"
                )
            }
        }
    }

    /**
     * Validate pipeline configuration
     *
     * @throws BrokenPipeError if required components are missing
     */
    private fun isValidPipeline() {
        if (tok == null || mod == null || form == null) {
            throw BrokenPipeError(
                "A valid pipeline must have a tokenizer, a processor and a formatter."
            )
        }
    }
}

/**
 * Custom exception for pipeline errors
 */
class SyntaxError(message: String) : Exception(message)

/**
 * Custom exception for missing pipeline components
 */
class BrokenPipeError(message: String) : Exception(message)