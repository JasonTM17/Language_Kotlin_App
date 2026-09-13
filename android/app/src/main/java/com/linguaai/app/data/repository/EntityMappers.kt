package com.linguaai.app.data.repository

import com.linguaai.app.data.local.entity.GrammarEntity
import com.linguaai.app.data.local.entity.LessonEntity
import com.linguaai.app.data.local.entity.VocabularyEntity
import com.linguaai.app.data.remote.dto.GrammarDto
import com.linguaai.app.data.remote.dto.LessonDto
import com.linguaai.app.data.remote.dto.LessonSummaryDto
import com.linguaai.app.data.remote.dto.VocabularyDto
import kotlinx.serialization.json.Json

/** Entity <-> DTO mapping keeps Room details out of the domain layer. */

private val examplesJson = Json { ignoreUnknownKeys = true }

fun LessonSummaryDto.toEntity() =
    LessonEntity(
        id = id,
        languageId = languageId,
        level = level,
        title = title,
        description = description,
        type = type,
        estimatedMinutes = estimatedMinutes,
        difficulty = difficulty,
        content = null,
    )

fun LessonDto.toEntity() =
    LessonEntity(
        id = id,
        languageId = languageId,
        level = level,
        title = title,
        description = description,
        type = type,
        estimatedMinutes = estimatedMinutes,
        difficulty = difficulty,
        content = content,
    )

fun LessonEntity.toSummaryDto() =
    LessonSummaryDto(
        id = id,
        languageId = languageId,
        level = level,
        title = title,
        description = description,
        type = type,
        estimatedMinutes = estimatedMinutes,
        difficulty = difficulty,
    )

fun LessonEntity.toDto() =
    LessonDto(
        id = id,
        languageId = languageId,
        level = level,
        title = title,
        description = description,
        type = type,
        estimatedMinutes = estimatedMinutes,
        difficulty = difficulty,
        content = content,
    )

fun VocabularyDto.toEntity() =
    VocabularyEntity(
        id = id,
        languageId = languageId,
        level = level,
        word = word,
        reading = reading,
        pronunciation = pronunciation,
        meaning = meaning,
        example = example,
        exampleTranslation = exampleTranslation,
        category = category,
    )

fun VocabularyEntity.toDto() =
    VocabularyDto(
        id = id,
        languageId = languageId,
        level = level,
        word = word,
        reading = reading,
        pronunciation = pronunciation,
        meaning = meaning,
        example = example,
        exampleTranslation = exampleTranslation,
        category = category,
    )

fun GrammarDto.toEntity() =
    GrammarEntity(
        id = id,
        languageId = languageId,
        level = level,
        title = title,
        structure = structure,
        meaning = meaning,
        usage = usage,
        examplesJson =
            examples
                .takeIf { it.isNotEmpty() }
                ?.let { list -> list.joinToString("\n") { "${it.sentence}|${it.translation}" } },
        notes = notes,
        difficulty = difficulty,
    )

fun GrammarEntity.toDto() =
    GrammarDto(
        id = id,
        languageId = languageId,
        level = level,
        title = title,
        structure = structure,
        meaning = meaning,
        usage = usage,
        examples =
            examplesJson
                ?.split("\n")
                ?.mapNotNull { line ->
                    val parts = line.split("|")
                    if (parts.size == 2) {
                        com.linguaai.app.data.remote.dto
                            .GrammarExampleDto(parts[0].trim(), parts[1].trim())
                    } else {
                        null
                    }
                }
                ?: emptyList(),
        notes = notes,
        difficulty = difficulty,
    )
