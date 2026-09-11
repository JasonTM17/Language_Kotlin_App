package com.linguaai.app.domain.repository

import com.linguaai.app.data.remote.dto.ProgressSummaryDto
import com.linguaai.app.domain.model.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Progress access.
 *
 * The server owns the aggregation and the streak; this layer only caches the last
 * summary so the screen can render immediately and stay useful offline.
 */
interface ProgressRepository {

    /** Last cached summary, or null when nothing has been fetched yet. */
    fun observeCached(): Flow<ProgressSummaryDto?>

    /**
     * Pulls a fresh summary. On failure the caller keeps whatever is cached —
     * progress is never shown as an error when offline data exists.
     */
    suspend fun refresh(): AppResult<ProgressSummaryDto>

    /**
     * Records a meaningful learning event. [operationId] must be stable across
     * retries so the server can deduplicate; a repeated call is a no-op
     * server-side rather than a double count.
     */
    suspend fun recordEvent(
        operationId: String,
        eventType: String,
        refId: Long? = null,
        minutes: Int = 0,
    ): AppResult<Unit>

    /** Drops cached progress. Called on sign-out. */
    suspend fun clearCache()
}
