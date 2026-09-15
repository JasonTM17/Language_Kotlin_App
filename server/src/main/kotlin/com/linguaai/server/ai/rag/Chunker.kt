package com.linguaai.server.ai.rag

/**
 * Splits corpus documents into retrieval-sized chunks on sentence boundaries.
 *
 * Chunking policy: target [maxChars] per chunk, sentence-aligned where the
 * text allows it, hard-wrapped when a single sentence exceeds the budget, and
 * a trailing fragment smaller than [minChars] merged back so the store never
 * fills with one-line stubs.
 */
class Chunker(
    private val maxChars: Int = DEFAULT_MAX_CHARS,
    private val minChars: Int = DEFAULT_MIN_CHARS,
) {
    fun chunk(text: String): List<String> {
        val normalized = text.replace(NEWLINES, "\n").trim()
        if (normalized.isEmpty()) return emptyList()
        if (normalized.length <= maxChars) return listOf(normalized)

        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (sentence in splitSentences(normalized)) {
            if (sentence.length > maxChars) {
                if (current.isNotBlank()) {
                    chunks.add(current.toString().trim())
                    current.setLength(0)
                }
                chunks.addAll(hardWrap(sentence))
                continue
            }
            if (current.length + sentence.length > maxChars) {
                chunks.add(current.toString().trim())
                current.setLength(0)
            }
            current.append(sentence)
        }
        if (current.isNotBlank()) chunks.add(current.toString().trim())

        if (chunks.size > 1 && chunks.last().length < minChars) {
            val tail = chunks.removeAt(chunks.size - 1)
            chunks[chunks.size - 1] = "${chunks.last()} $tail".trim()
        }
        return chunks.filter { it.isNotBlank() }
    }

    private fun splitSentences(text: String): List<String> =
        text
            .split(SENTENCE_BOUNDARY)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { "$it " }

    private fun hardWrap(sentence: String): List<String> {
        val pieces = mutableListOf<String>()
        var start = 0
        while (start < sentence.length) {
            val end = minOf(start + maxChars, sentence.length)
            pieces.add(sentence.substring(start, end).trim())
            start = end
        }
        return pieces.filter { it.isNotEmpty() }
    }

    private companion object {
        const val DEFAULT_MAX_CHARS = 600
        const val DEFAULT_MIN_CHARS = 160
        val SENTENCE_BOUNDARY = Regex("""(?<=[.。！!？?；;])\s*""")
        val NEWLINES = Regex("""\r\n|\r""")
    }
}
