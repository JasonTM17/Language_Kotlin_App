package com.linguaai.server

import com.linguaai.server.ai.rag.Chunker
import com.linguaai.server.ai.rag.EmbeddingProvider
import com.linguaai.server.ai.rag.HashingEmbeddingProvider
import com.linguaai.server.ai.rag.KnowledgeIndexer
import com.linguaai.server.ai.rag.OpenAiEmbeddingProvider
import com.linguaai.server.ai.rag.QdrantVectorStore
import com.linguaai.server.ai.rag.RagRepository
import com.linguaai.server.ai.rag.RagService
import com.linguaai.server.ai.rag.SqlVectorStore
import com.linguaai.server.ai.rag.VectorStore
import com.linguaai.server.config.AppConfig
import com.linguaai.server.db.DatabaseFactory
import com.linguaai.server.ops.OpsService
import com.linguaai.server.ops.OpsSingleFlight
import com.linguaai.server.ops.SeedRepository
import com.linguaai.server.plugins.configureAuthentication
import com.linguaai.server.plugins.configureMonitoring
import com.linguaai.server.plugins.configureSerialization
import com.linguaai.server.plugins.configureStatusPages
import com.linguaai.server.repository.AuthRepository
import com.linguaai.server.repository.ContentRepository
import com.linguaai.server.routes.aiHttpClient
import com.linguaai.server.routes.configureAiRoutes
import com.linguaai.server.routes.configureAuthRoutes
import com.linguaai.server.routes.configureContentRoutes
import com.linguaai.server.routes.configureOpsRoutes
import com.linguaai.server.routes.configureProfileRoutes
import com.linguaai.server.routes.configureProgressRoutes
import com.linguaai.server.routes.configureQuizRoutes
import com.linguaai.server.routes.configureRouting
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

private val bootLog = LoggerFactory.getLogger("Application")

fun main() {
    // Fail fast rather than boot with a publicly known signing key. Docker
    // Compose already enforces JWT_SECRET via ${JWT_SECRET:?}; this guard
    // covers bare-jar and other deployment paths.
    if (System.getenv("APP_ENV") == "production" && System.getenv("JWT_SECRET").isNullOrBlank()) {
        System.err.println("Refusing to start: JWT_SECRET must be set when APP_ENV=production")
        exitProcess(1)
    }
    // Ops endpoints (reindex/seed/stats) authenticate with OPS_TOKEN. A
    // production deployment that fell back to the dev default would be
    // unauthenticated mutation access, so it refuses to boot instead.
    if (productionOpsTokenIsUnsafe(System.getenv("APP_ENV"), System.getenv("OPS_TOKEN"))) {
        System.err.println("Refusing to start: OPS_TOKEN must be set to a non-default value when APP_ENV=production")
        exitProcess(1)
    }

    val config = AppConfig.fromEnv()
    embeddedServer(
        factory = Netty,
        port = config.serverPort,
        host = "0.0.0.0",
        module = { module(config, startBackgroundJobs = true) },
    ).start(wait = true)
}

internal fun productionOpsTokenIsUnsafe(
    appEnv: String?,
    opsToken: String?,
): Boolean = appEnv == "production" && (opsToken.isNullOrBlank() || opsToken == AppConfig.DEV_OPS_TOKEN)

/**
 * Composition root: database first, then cross-cutting plugins, then routes.
 *
 * [startBackgroundJobs] is false for tests: the RAG auto-index is an
 * asynchronous boot side effect, and a test that could not tell whether the
 * corpus was indexed yet would be nondeterministic. Tests that need an index
 * call the ops reindex endpoint explicitly.
 */
fun Application.module(
    config: AppConfig = AppConfig.fromEnv(),
    startBackgroundJobs: Boolean = false,
) {
    DatabaseFactory.init(config)

    val authRepository = AuthRepository()
    val contentRepository = ContentRepository()

    // RAG pipeline. Engine selection is a config decision made exactly once:
    // Qdrant (Phase 02) when QDRANT_URL is set, SQL brute-force otherwise.
    // There is no runtime fallback between engines — a half-working engine
    // must be visible, not silently swapped.
    val ragRepository = RagRepository()
    val embedder: EmbeddingProvider =
        when (config.aiProvider) {
            AppConfig.AiProviderKind.MOCK -> HashingEmbeddingProvider()
            AppConfig.AiProviderKind.OPENAI_COMPATIBLE -> OpenAiEmbeddingProvider(config, aiHttpClient(config))
        }
    val sqlStore: VectorStore = SqlVectorStore(config.ragCandidateLimit)
    val searchEngine: VectorStore =
        config.qdrantUrl?.let { url ->
            QdrantVectorStore(
                baseUrl = url.trimEnd('/'),
                collection = config.qdrantCollection,
                dimensions = embedder.dimensions,
                client = aiHttpClient(config),
            )
        } ?: sqlStore
    val opsGuard = OpsSingleFlight()
    val indexer = KnowledgeIndexer(ragRepository, embedder, sqlStore, searchEngine, Chunker(), opsGuard)
    val ragService =
        RagService(
            repository = ragRepository,
            embedder = embedder,
            searchEngine = searchEngine,
            indexer = indexer,
            topK = config.ragTopK,
            maxContextChars = config.ragMaxContextChars,
        )
    val opsService = OpsService(ragRepository, SeedRepository(), sqlStore, searchEngine, opsGuard)

    configureSerialization()
    configureMonitoring()
    configureStatusPages()
    // Must be installed before any route that uses authenticate("auth-jwt"),
    // which is why it is no longer a side effect of configuring auth endpoints.
    configureAuthentication(config)
    configureRouting()
    configureContentRoutes(contentRepository)
    configureAuthRoutes(config, authRepository)
    configureProfileRoutes(authRepository)
    configureQuizRoutes(contentRepository)
    configureAiRoutes(config, authRepository, contentRepository, ragService)
    configureProgressRoutes()
    configureOpsRoutes(config, ragService, opsService)

    if (startBackgroundJobs && config.ragAutoIndex) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { ragService.reindex() }
                .onSuccess {
                    bootLog.info(
                        "RAG auto-index complete: {} documents scanned, {} chunks written in {} ms",
                        it.documentsScanned,
                        it.chunksWritten,
                        it.durationMs,
                    )
                }.onFailure {
                    bootLog.warn("RAG auto-index failed; the corpus stays unindexed", it)
                }
        }
    }
}
