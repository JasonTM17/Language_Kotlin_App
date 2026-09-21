// Column widths in this file are schema declarations, not magic numbers.
//
// `varchar("email", 255)` reads better than
// `varchar("email", EMAIL_MAX_LENGTH)` — the width belongs next to the column it
// constrains, and a named constant for each would add roughly two dozen
// declarations while moving every value away from what it describes.
//
// This is a file-level exemption, not a rule change: MagicNumber stays active
// everywhere else in the build.
@file:Suppress("MagicNumber")

package com.linguaai.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Exposed table definitions mirroring Flyway V1 exactly. DDL ownership stays
 * with Flyway; these objects are query-only.
 */
object Users : Table("users") {
    val id = long("id").autoIncrement()
    val email = varchar("email", 255)
    val username = varchar("username", 80)
    val passwordHash = varchar("password_hash", 255)
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object RefreshTokens : Table("refresh_tokens") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val tokenHash = varchar("token_hash", 255)
    val familyId = varchar("family_id", 64)
    val expiresAt = datetime("expires_at")
    val revoked = bool("revoked").default(false)
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}

object UserProfiles : Table("user_profiles") {
    val userId = long("user_id").references(Users.id)
    val languageId = long("language_id").nullable()
    val level = varchar("level", 20).nullable()
    val goal = varchar("goal", 60).nullable()
    val dailyGoalMinutes = integer("daily_goal_minutes").default(10)
    val onboarded = bool("onboarded").default(false)
    val updatedAt = datetime("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

object Languages : Table("languages") {
    val id = long("id").autoIncrement()
    val code = varchar("code", 10)
    val name = varchar("name", 80)
    val levels = varchar("levels", 500)
    override val primaryKey = PrimaryKey(id)
}

object Lessons : Table("lessons") {
    val id = long("id").autoIncrement()
    val languageId = long("language_id").references(Languages.id)
    val level = varchar("level", 20)
    val title = varchar("title", 200)
    val description = varchar("description", 1000).nullable()
    val type = varchar("type", 20)
    val estimatedMinutes = integer("estimated_minutes")
    val difficulty = integer("difficulty")
    val content = text("content").nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Vocabularies : Table("vocabularies") {
    val id = long("id").autoIncrement()
    val languageId = long("language_id").references(Languages.id)
    val level = varchar("level", 20)
    val word = varchar("word", 120)
    val reading = varchar("reading", 200).nullable()
    val pronunciation = varchar("pronunciation", 200).nullable()
    val meaning = varchar("meaning", 500)
    val example = text("example").nullable()
    val exampleTranslation = text("example_translation").nullable()
    val category = varchar("category", 80).nullable()
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}

object GrammarLessons : Table("grammar_lessons") {
    val id = long("id").autoIncrement()
    val languageId = long("language_id").references(Languages.id)
    val level = varchar("level", 20)
    val title = varchar("title", 200)
    val structure = varchar("structure", 500).nullable()
    val meaning = varchar("meaning", 1000).nullable()

    // "usage" is a MySQL reserved word; the column is named usage_notes
    val usageNotes = text("usage_notes").nullable()
    val examples = text("examples").nullable()
    val notes = text("notes").nullable()
    val difficulty = integer("difficulty")
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Quizzes : Table("quizzes") {
    val id = long("id").autoIncrement()
    val languageId = long("language_id").references(Languages.id)
    val level = varchar("level", 20)
    val title = varchar("title", 200)
    val description = varchar("description", 500).nullable()
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}

object QuizQuestions : Table("quiz_questions") {
    val id = long("id").autoIncrement()
    val quizId = long("quiz_id").references(Quizzes.id)
    val questionType = varchar("question_type", 20)
    val prompt = varchar("prompt", 1000)
    val options = text("options").nullable() // JSON array
    val correctAnswer = varchar("correct_answer", 500)
    val explanation = text("explanation").nullable()
    val position = integer("position")
    override val primaryKey = PrimaryKey(id)
}

object QuizAttempts : Table("quiz_attempts") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val quizId = long("quiz_id").references(Quizzes.id)
    val score = integer("score")
    val total = integer("total")
    val durationSeconds = integer("duration_seconds").nullable()
    val completedAt = datetime("completed_at")
    override val primaryKey = PrimaryKey(id)
}

object QuizAnswers : Table("quiz_answers") {
    val id = long("id").autoIncrement()
    val attemptId = long("attempt_id").references(QuizAttempts.id)
    val questionId = long("question_id").references(QuizQuestions.id)
    val answer = varchar("answer", 500).nullable()
    val correct = bool("correct")
    override val primaryKey = PrimaryKey(id)
}

object AiConversations : Table("ai_conversations") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val title = varchar("title", 200)
    val mode = varchar("mode", 30)
    val summary = text("summary").nullable()
    val summarizedUntil = long("summarized_until").nullable()
    val contextLessonId = long("context_lesson_id").nullable()
    val contextGrammarId = long("context_grammar_id").nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object AiMessages : Table("ai_messages") {
    val id = long("id").autoIncrement()
    val conversationId = long("conversation_id").references(AiConversations.id)
    val role = varchar("role", 10)
    val content = text("content")
    val tokenCount = integer("token_count").nullable()
    /** Serialized `AiSourceDto` list this reply was grounded in; null when nothing was retrieved. */
    val sources = text("sources").nullable()
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}

object UserProgress : Table("user_progress") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val clientOperationId = varchar("client_operation_id", 64)
    val eventType = varchar("event_type", 30)
    val refId = long("ref_id").nullable()
    val minutes = integer("minutes").default(0)
    val occurredAt = datetime("occurred_at")
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_progress_operation", userId, clientOperationId)
    }
}

object UserVocabularyProgress : Table("user_vocabulary_progress") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val vocabularyId = long("vocabulary_id").references(Vocabularies.id)
    val favorite = bool("favorite").default(false)
    val masteryLevel = integer("mastery_level").default(0)
    val reviewCount = integer("review_count").default(0)
    val correctCount = integer("correct_count").default(0)
    val wrongCount = integer("wrong_count").default(0)
    val lastReviewedAt = datetime("last_reviewed_at").nullable()
    val nextReviewAt = datetime("next_review_at").nullable()
    val clientUpdatedAt = datetime("client_updated_at").nullable()
    val updatedAt = datetime("updated_at")
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_uvp_user_vocab", userId, vocabularyId)
    }
}

object UserMistakes : Table("user_mistakes") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val languageId = long("language_id").nullable()
    val topic = varchar("topic", 200)
    val detail = text("detail").nullable()

    // named sourceType in Kotlin: "source" clashes with an Exposed supertype member
    val sourceType = varchar("source", 30).nullable()
    val createdAt = datetime("created_at")
    val resolved = bool("resolved").default(false)
    override val primaryKey = PrimaryKey(id)
}

object LearningStreaks : Table("learning_streaks") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val activityDate = date("activity_date")
    val minutes = integer("minutes").default(0)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_streak_user_date", userId, activityDate)
    }
}

/**
 * RAG corpus chunks (Flyway V5). `embedding` is little-endian float32, owned by
 * db/rag VectorMath; `embeddingModel` names the space the vector lives in so
 * vectors from different providers can never be compared.
 */
object KnowledgeChunks : Table("knowledge_chunks") {
    val id = long("id").autoIncrement()
    val languageId = long("language_id").references(Languages.id)
    val level = varchar("level", 20).nullable()
    val sourceType = varchar("source_type", 30)
    val sourceId = long("source_id")
    val chunkIndex = integer("chunk_index")
    val title = varchar("title", 300)
    val content = text("content")
    val embeddingModel = varchar("embedding_model", 80)
    val embedding = blob("embedding")
    val contentHash = varchar("content_hash", 64)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_chunk", sourceType, sourceId, chunkIndex)
    }
}
