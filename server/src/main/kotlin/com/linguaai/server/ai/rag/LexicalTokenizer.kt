package com.linguaai.server.ai.rag

/**
 * Lexical token extraction shared by the hashing embedder and the retrieval
 * verifier. One owner for the regexes: the embedder hashes these features and
 * the verifier checks them against stored content, so a tokenizer change must
 * change both consistently.
 */
object LexicalTokenizer {
    /** Kana, CJK ideographs and fullwidth forms — the scripts char n-grams serve. */
    val CJK_RUNS = Regex("""[\u3040-\u30ff\u3400-\u4dbf\u4e00-\u9fff\uff66-\uff9f]+""")
    val WORD_TOKENS = Regex("""[\p{L}\p{N}]+""")

    private const val MIN_WORD_TOKEN_LENGTH = 3
    private const val MIN_CJK_TOKEN_LENGTH = 2

    /**
     * Verifiable tokens of a query: word tokens long enough to be meaningful
     * plus CJK bigrams (環境 must be verifiable at two characters).
     */
    fun verifierTokens(text: String): List<String> {
        val lowered = text.lowercase()
        val tokens = mutableListOf<String>()
        for (token in WORD_TOKENS.findAll(lowered)) {
            if (token.value.length >= MIN_WORD_TOKEN_LENGTH) tokens.add(token.value)
        }
        for (run in CJK_RUNS.findAll(lowered)) {
            if (run.value.length >= MIN_CJK_TOKEN_LENGTH) {
                for (i in 0..run.value.length - MIN_CJK_TOKEN_LENGTH) {
                    tokens.add(run.value.substring(i, i + MIN_CJK_TOKEN_LENGTH))
                }
            }
        }
        return tokens.distinct()
    }
}
