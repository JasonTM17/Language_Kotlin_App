package com.linguaai.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.linguaai.app.data.local.entity.PendingSyncOpEntity
import com.linguaai.app.data.local.entity.SyncOpState
import kotlinx.coroutines.flow.Flow

/** Outbox DAO for idempotent background synchronization. */
@Dao
interface SyncDao {
    @Insert
    suspend fun enqueue(op: PendingSyncOpEntity): Long

    @Query("SELECT * FROM pending_sync_ops WHERE state = :state ORDER BY id LIMIT :limit")
    suspend fun byState(
        state: String,
        limit: Int,
    ): List<PendingSyncOpEntity>

    @Query("SELECT * FROM pending_sync_ops WHERE state != 'SYNCED' ORDER BY id")
    fun observeUnsynced(): Flow<List<PendingSyncOpEntity>>

    @Query("UPDATE pending_sync_ops SET state = :state WHERE id = :id")
    suspend fun updateState(
        id: Long,
        state: String,
    )

    /** Records a failed delivery attempt and parks the op for the next run. */
    @Query("UPDATE pending_sync_ops SET attempts = attempts + 1, state = :state WHERE id = :id")
    suspend fun recordAttempt(
        id: Long,
        state: String,
    )

    @Query("SELECT COUNT(*) FROM pending_sync_ops WHERE state = :state")
    suspend fun countByState(state: String): Int

    @Query("DELETE FROM pending_sync_ops WHERE state = :state")
    suspend fun deleteByState(state: String)

    /** Wipes the outbox. Used on sign-out so one user's events never reach another. */
    @Query("DELETE FROM pending_sync_ops")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM pending_sync_ops WHERE state != 'SYNCED'")
    suspend fun unsyncedCount(): Int

    suspend fun pending(limit: Int = 50): List<PendingSyncOpEntity> = byState(SyncOpState.STATE_PENDING, limit)
}

@Dao
interface AiMessageCacheDao {
    @Insert
    suspend fun insertAll(messages: List<com.linguaai.app.data.local.entity.AiMessageCacheEntity>)

    @Query("SELECT * FROM ai_message_cache WHERE conversationId = :conversationId ORDER BY id")
    suspend fun byConversation(conversationId: Long): List<com.linguaai.app.data.local.entity.AiMessageCacheEntity>

    @Query("DELETE FROM ai_message_cache WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: Long)

    /** Replaces one server-owned history atomically so a failed insert cannot erase the old cache. */
    @Transaction
    suspend fun replaceConversation(
        conversationId: Long,
        messages: List<com.linguaai.app.data.local.entity.AiMessageCacheEntity>,
    ) {
        clearConversation(conversationId)
        insertAll(messages)
    }

    /** Wipes cached conversation content. Used on sign-out. */
    @Query("DELETE FROM ai_message_cache")
    suspend fun clearAll()
}
