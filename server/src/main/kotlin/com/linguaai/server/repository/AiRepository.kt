package com.linguaai.server.repository

import com.linguaai.server.db.AiConversations
import com.linguaai.server.db.AiMessages
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

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
)

/** Persistence for AI conversations, messages and the rolling summary. */
class AiRepository {

    fun createConversation(
        userId: Long,
        title: String,
        mode: String,
        contextLessonId: Long?,
        contextGrammarId: Long?,
    ): ConversationRow = transaction {
        val now = LocalDateTime.now()
        val id = AiConversations.insert { row ->
            row[AiConversations.userId] = userId
            row[AiConversations.title] = title.take(190)
            row[AiConversations.mode] = mode
            row[AiConversations.contextLessonId] = contextLessonId
            row[AiConversations.contextGrammarId] = contextGrammarId
            row[AiConversations.createdAt] = now
            row[AiConversations.updatedAt] = now
        } get AiConversations.id
        ConversationRow(
            id = id,
            userId = userId,
            title = title.take(190),
            mode = mode,
            summary = null,
            summarizedUntil = null,
            contextLessonId = contextLessonId,
            contextGrammarId = contextGrammarId,
        )
    }

    fun findConversation(id: Long, userId: Long): ConversationRow? = transaction {
        AiConversations.selectAll()
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

    fun listConversations(userId: Long): List<ConversationRow> = transaction {
        AiConversations.selectAll()
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

    fun addMessage(conversationId: Long, role: String, content: String, tokenCount: Int? = null): MessageRow =
        transaction {
            val id = AiMessages.insert { row ->
                row[AiMessages.conversationId] = conversationId
                row[AiMessages.role] = role
                row[AiMessages.content] = content
                row[AiMessages.tokenCount] = tokenCount
                row[AiMessages.createdAt] = LocalDateTime.now()
            } get AiMessages.id
            AiConversations.update({ AiConversations.id eq conversationId }) {
                it[AiConversations.updatedAt] = LocalDateTime.now()
            }
            MessageRow(id, conversationId, role, content, tokenCount)
        }

    fun messages(conversationId: Long, limit: Int = 200): List<MessageRow> = transaction {
        AiMessages.selectAll()
            .andWhere { AiMessages.conversationId eq conversationId }
            .orderBy(AiMessages.id, SortOrder.ASC)
            .map { row ->
                MessageRow(
                    id = row[AiMessages.id],
                    conversationId = row[AiMessages.conversationId],
                    role = row[AiMessages.role],
                    content = row[AiMessages.content],
                    tokenCount = row[AiMessages.tokenCount],
                )
            }
            .takeLast(limit)
    }

    fun updateSummary(conversationId: Long, summary: String, summarizedUntil: Long) = transaction {
        AiConversations.update({ AiConversations.id eq conversationId }) { row ->
            row[AiConversations.summary] = summary
            row[AiConversations.summarizedUntil] = summarizedUntil
        }
    }
}
