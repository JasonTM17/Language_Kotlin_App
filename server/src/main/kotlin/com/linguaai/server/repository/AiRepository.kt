package com.linguaai.server.repository

import com.linguaai.server.db.AiConversations
import com.linguaai.server.db.AiMessages
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

/**
 * Titles are clipped to the width of ai_conversations.title in db/Tables.kt. A
 * longer value fails the insert rather than being clipped, and the limit is
 * applied in two places, so it is named once.
 */
private const val MAX_TITLE_LENGTH = 190

data class ConversationRow(
    val id: Long,
    val userId: Long,
    val title: String,
    val mode: String,
    val summary: String?,
    val summarizedUntil: Long?,
    val contextLessonId: Long?,
    val contextGrammarId: Long?,
)

data class MessageRow(
    val id: Long,
    val conversationId: Long,
    val role: String,
    val content: String,
    val tokenCount: Int?,
    val sources: String?,
)

/** Persistence for AI conversations, messages and the rolling summary. */
class AiRepository {
    fun createConversation(
        userId: Long,
        title: String,
        mode: String,
        contextLessonId: Long?,
        contextGrammarId: Long?,
    ): ConversationRow =
        transaction {
            val now = LocalDateTime.now()
            val id =
                AiConversations.insert { row ->
                    row[AiConversations.userId] = userId
                    row[AiConversations.title] = title.take(MAX_TITLE_LENGTH)
                    row[AiConversations.mode] = mode
                    row[AiConversations.contextLessonId] = contextLessonId
                    row[AiConversations.contextGrammarId] = contextGrammarId
                    row[AiConversations.createdAt] = now
                    row[AiConversations.updatedAt] = now
                } get AiConversations.id
            ConversationRow(
                id = id,
                userId = userId,
                title = title.take(MAX_TITLE_LENGTH),
                mode = mode,
                summary = null,
                summarizedUntil = null,
                contextLessonId = contextLessonId,
                contextGrammarId = contextGrammarId,
            )
        }

    fun findConversation(
        id: Long,
        userId: Long,
    ): ConversationRow? =
        transaction {
            AiConversations
                .selectAll()
                .andWhere { AiConversations.id eq id }
                .andWhere { AiConversations.userId eq userId }
                .firstOrNull()
                ?.let { row ->
                    ConversationRow(
                        id = row[AiConversations.id],
                        userId = row[AiConversations.userId],
                        title = row[AiConversations.title],
                        mode = row[AiConversations.mode],
                        summary = row[AiConversations.summary],
                        summarizedUntil = row[AiConversations.summarizedUntil],
                        contextLessonId = row[AiConversations.contextLessonId],
                        contextGrammarId = row[AiConversations.contextGrammarId],
                    )
                }
        }

    fun listConversations(userId: Long): List<ConversationRow> =
        transaction {
            AiConversations
                .selectAll()
                .andWhere { AiConversations.userId eq userId }
                .orderBy(AiConversations.updatedAt, SortOrder.DESC)
                .map { row ->
                    ConversationRow(
                        id = row[AiConversations.id],
                        userId = row[AiConversations.userId],
                        title = row[AiConversations.title],
                        mode = row[AiConversations.mode],
                        summary = row[AiConversations.summary],
                        summarizedUntil = row[AiConversations.summarizedUntil],
                        contextLessonId = row[AiConversations.contextLessonId],
                        contextGrammarId = row[AiConversations.contextGrammarId],
                    )
                }
        }

    /** Stores a completed turn atomically so history never contains half an exchange. */
    fun addExchange(
        conversationId: Long,
        userContent: String,
        assistantContent: String,
        sourcesJson: String? = null,
    ) = transaction {
        val now = LocalDateTime.now()
        AiMessages.insert { row ->
            row[AiMessages.conversationId] = conversationId
            row[AiMessages.role] = "USER"
            row[AiMessages.content] = userContent
            row[AiMessages.tokenCount] = null
            row[AiMessages.createdAt] = now
        }
        AiMessages.insert { row ->
            row[AiMessages.conversationId] = conversationId
            row[AiMessages.role] = "ASSISTANT"
            row[AiMessages.content] = assistantContent
            row[AiMessages.tokenCount] = null
            row[AiMessages.sources] = sourcesJson
            row[AiMessages.createdAt] = now
        }
        AiConversations.update({ AiConversations.id eq conversationId }) {
            it[AiConversations.updatedAt] = now
        }
    }

    /** Removes a newly-created conversation when its first provider turn fails. */
    fun deleteConversation(conversationId: Long) =
        transaction {
            AiMessages.deleteWhere { AiMessages.conversationId eq conversationId }
            AiConversations.deleteWhere { AiConversations.id eq conversationId }
        }

    fun messages(
        conversationId: Long,
        limit: Int = 200,
    ): List<MessageRow> =
        transaction {
            AiMessages
                .selectAll()
                .andWhere { AiMessages.conversationId eq conversationId }
                .orderBy(AiMessages.id, SortOrder.ASC)
                .map { row ->
                    MessageRow(
                        id = row[AiMessages.id],
                        conversationId = row[AiMessages.conversationId],
                        role = row[AiMessages.role],
                        content = row[AiMessages.content],
                        tokenCount = row[AiMessages.tokenCount],
                        sources = row[AiMessages.sources],
                    )
                }.takeLast(limit)
        }

    fun updateSummary(
        conversationId: Long,
        summary: String,
        summarizedUntil: Long,
    ) = transaction {
        AiConversations.update({ AiConversations.id eq conversationId }) { row ->
            row[AiConversations.summary] = summary
            row[AiConversations.summarizedUntil] = summarizedUntil
        }
    }
}
