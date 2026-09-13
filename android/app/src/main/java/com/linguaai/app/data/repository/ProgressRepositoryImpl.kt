package com.linguaai.app.data.repository

import com.linguaai.app.data.local.dao.ProgressCacheDao
import com.linguaai.app.data.local.entity.ProgressCacheEntity
import com.linguaai.app.data.remote.api.ProgressApi
import com.linguaai.app.data.remote.dto.ProgressSummaryDto
import com.linguaai.app.data.remote.dto.RecordProgressEventRequestDto
import com.linguaai.app.data.remote.safeApiCall
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Server-backed progress with a single-row local cache.
 *
 * The cached payload is the serialized summary, so a schema change to
 * [ProgressSummaryDto] is a cache-format change: a decode failure is treated as
 * "no cache" rather than crashing the screen.
 */
@Singleton
class ProgressRepositoryImpl
    @Inject
    constructor(
        private val progressApi: ProgressApi,
        private val progressCacheDao: ProgressCacheDao,
        private val json: Json,
    ) : ProgressRepository {
        override fun observeCached(): Flow<ProgressSummaryDto?> =
            progressCacheDao.observeEntry().map { entry ->
                entry?.let { decode(it.payload) }
            }

        override suspend fun refresh(): AppResult<ProgressSummaryDto> =
            when (val result = safeApiCall { progressApi.summary() }) {
                is AppResult.Success -> {
                    persist(result.data)
                    result
                }
                is AppResult.Failure -> result
            }

        override suspend fun recordEvent(
            operationId: String,
            eventType: String,
            refId: Long?,
            minutes: Int,
        ): AppResult<Unit> =
            when (
                val result =
                    safeApiCall {
                        progressApi.recordEvent(
                            RecordProgressEventRequestDto(
                                clientOperationId = operationId,
                                eventType = eventType,
                                refId = refId,
                                minutes = minutes,
                            ),
                        )
                    }
            ) {
                is AppResult.Success -> AppResult.Success(Unit)
                is AppResult.Failure -> result
            }

        override suspend fun clearCache() = progressCacheDao.clear()

        private suspend fun persist(summary: ProgressSummaryDto) {
            progressCacheDao.put(
                ProgressCacheEntity(
                    payload = json.encodeToString(ProgressSummaryDto.serializer(), summary),
                ),
            )
        }

        private fun decode(payload: String): ProgressSummaryDto? =
            runCatching { json.decodeFromString(ProgressSummaryDto.serializer(), payload) }.getOrNull()
    }
