package com.linguaai.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.linguaai.app.data.local.entity.PendingSyncOpEntity
import com.linguaai.app.data.local.entity.SyncOpState
import kotlinx.coroutines.flow.Flow

/** Outbox DAO for idempotent background synchronization. */
@Dao
interface SyncDao {

    @Insert
    suspend fun enqueue(op: PendingSyncOpEntity): Long

    @Query("SELECT * FROM pending_sync_ops WHERE state = :state ORDER BY id LIMIT :limit")
    suspend fun byState(state: String, limit: Int): List<PendingSyncOpEntity>

    @Query("SELECT * FROM pending_sync_ops WHERE state != 'SYNCED' ORDER BY id")
    fun observeUnsynced(): Flow<List<PendingSyncOpEntity>>

    @Query("UPDATE pending_sync_ops SET state = :state WHERE id = :id")
    suspend fun updateState(id: Long, state: String)

    @Query("DELETE FROM pending_sync_ops WHERE state = :state")
    suspend fun deleteByState(state: String)

    @Query("SELECT COUNT(*) FROM pending_sync_ops WHERE state != 'SYNCED'")
    suspend fun unsyncedCount(): Int

    suspend fun pending(limit: Int = 50): List<PendingSyncOpEntity> = byState(SyncOpState.STATE_PENDING, limit)
}
