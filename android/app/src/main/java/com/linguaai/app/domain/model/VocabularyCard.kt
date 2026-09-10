package com.linguaai.app.domain.model

/** UI-facing vocabulary card: server content + local review state. */
data class VocabularyCard(
    val id: Long,
    val languageId: Long,
    val level: String,
    val word: String,
    val reading: String?,
    val pronunciation: String?,
    val meaning: String,
    val example: String?,
    val exampleTranslation: String?,
    val category: String?,
    val favorite: Boolean,
    val masteryLevel: Int,
)
