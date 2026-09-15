package com.linguaai.server.routes

import com.linguaai.server.ai.rag.RagService
import com.linguaai.server.api.ApiException
import com.linguaai.server.api.ErrorCodes
import com.linguaai.server.config.AppConfig
import com.linguaai.server.ops.OpsService
import com.linguaai.server.ops.SeedInProgressException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import java.security.MessageDigest

/*
 * Machine-facing operations endpoints. These mutate the corpus (reindex,
 * seed) and expose platform statistics, so they authenticate with a shared
 * ops secret rather than a learner JWT. The token is compared in constant
 * time, and production refuses to boot without a real secret (Application.main).
 */

@Serializable
data class ReindexResponseDto(
    val documentsScanned: Long,
    val chunksWritten: Long,
    val documentsUnchanged: Long,
    val durationMs: Long,
)

@Serializable
data class RagStatsDto(
    val vocabularies: Long,
    val grammarLessons: Long,
    val lessons: Long,
    val knowledgeChunks: Long,
    val engine: String,
    val embeddingModel: String,
)

fun Application.configureOpsRoutes(
    config: AppConfig,
    ragService: RagService,
    opsService: OpsService,
) {
    routing {
        route("/api/v1/ops") {
            post("/rag/reindex") {
                call.requireOpsToken(config)
                val force = call.request.queryParameters["force"]?.toBoolean() ?: false
                try {
                    call.respond(ragService.reindex(force).toDto())
                } catch (busy: RagService.ReindexInProgressException) {
                    throw conflict("A reindex or seed operation is already running", busy)
                }
            }

            post("/seed/scale") {
                call.requireOpsToken(config)
                try {
                    call.respond(
                        opsService.seedScale(
                            wordsPerLanguage = call.parameters["wordsPerLanguage"]?.toIntOrNull() ?: 0,
                            grammarPerLanguage = call.parameters["grammarPerLanguage"]?.toIntOrNull() ?: 0,
                            lessonsPerLanguage = call.parameters["lessonsPerLanguage"]?.toIntOrNull() ?: 0,
                        ),
                    )
                } catch (busy: SeedInProgressException) {
                    throw conflict("A seed or reindex operation is already running", busy)
                }
            }

            delete("/seed") {
                call.requireOpsToken(config)
                call.respond(opsService.purge())
            }

            get("/stats") {
                call.requireOpsToken(config)
                val counts = ragService.corpusCounts()
                call.respond(
                    RagStatsDto(
                        vocabularies = counts.vocabularies,
                        grammarLessons = counts.grammarLessons,
                        lessons = counts.lessons,
                        knowledgeChunks = ragService.chunkCount(),
                        engine = ragService.searchEngineKind(),
                        embeddingModel = ragService.embeddingModel(),
                    ),
                )
            }
        }
    }
}

private fun ApplicationCall.requireOpsToken(config: AppConfig) {
    val provided = request.header(OPS_TOKEN_HEADER).orEmpty()
    val expected = config.opsToken
    val matches =
        MessageDigest.isEqual(provided.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
    if (!matches) {
        throw ApiException(
            HttpStatusCode.Unauthorized,
            ErrorCodes.UNAUTHORIZED,
            "Valid X-Ops-Token header required",
        )
    }
}

private fun conflict(
    message: String,
    cause: Throwable,
): ApiException = ApiException(HttpStatusCode.Conflict, ErrorCodes.CONFLICT, message, cause = cause)

private fun com.linguaai.server.ai.rag.IndexReport.toDto(): ReindexResponseDto =
    ReindexResponseDto(
        documentsScanned = documentsScanned,
        chunksWritten = chunksWritten,
        documentsUnchanged = documentsUnchanged,
        durationMs = durationMs,
    )

private const val OPS_TOKEN_HEADER = "X-Ops-Token"
