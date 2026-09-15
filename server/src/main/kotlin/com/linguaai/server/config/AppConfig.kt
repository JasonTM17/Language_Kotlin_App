package com.linguaai.server.config

/**
 * Immutable server configuration resolved from environment variables.
 * Defaults keep local development frictionless; production values come from
 * `.env` / container environment. Secrets never have defaults.
 */
data class AppConfig(
    val serverPort: Int,
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val runMigrations: Boolean,
    val jwtSecret: String,
    val accessTokenTtlMinutes: Long,
    val refreshTokenTtlDays: Long,
    val aiProvider: AiProviderKind,
    val aiBaseUrl: String,
    val aiApiKey: String,
    val aiModel: String,
    val aiTimeoutSeconds: Int,
    val aiRateLimitPerMinute: Int,
    val aiMockScenario: String?,
    val aiEmbeddingModel: String,
    val ragEnabled: Boolean,
    val ragTopK: Int,
    val ragMaxContextChars: Int,
    val ragCandidateLimit: Int,
    val ragAutoIndex: Boolean,
    val qdrantUrl: String?,
    val qdrantCollection: String,
    val opsToken: String,
) {
    enum class AiProviderKind { MOCK, OPENAI_COMPATIBLE }

    companion object {
        // Fallback values used when an environment variable is absent. They are
        // named because they duplicate the defaults documented in .env.example,
        // and a drift between the two is only detectable if both are greppable.
        const val DEFAULT_SERVER_PORT = 8080
        const val DEFAULT_DB_PORT = 3306
        const val DEFAULT_ACCESS_TOKEN_TTL_MINUTES = 30L
        const val DEFAULT_REFRESH_TOKEN_TTL_DAYS = 14L
        const val DEFAULT_AI_RATE_LIMIT_PER_MINUTE = 20
        const val DEFAULT_RAG_TOP_K = 4
        const val DEFAULT_RAG_MAX_CONTEXT_CHARS = 2400
        const val DEFAULT_RAG_CANDIDATE_LIMIT = 20_000

        /**
         * Development-only ops token. Production must override OPS_TOKEN with a
         * real secret; Application.main refuses to boot a production deployment
         * that would otherwise run with this publicly known value (same guard
         * pattern as the JWT fail-fast).
         */
        const val DEV_OPS_TOKEN = "linguaai-dev-ops-token"

        fun fromEnv(env: (String) -> String? = System::getenv): AppConfig {
            fun str(
                key: String,
                default: String,
            ) = env(key)?.takeIf { it.isNotBlank() } ?: default

            fun int(
                key: String,
                default: Int,
            ) = env(key)?.toIntOrNull() ?: default

            fun long(
                key: String,
                default: Long,
            ) = env(key)?.toLongOrNull() ?: default

            return AppConfig(
                serverPort = int("SERVER_PORT", DEFAULT_SERVER_PORT),
                dbUrl =
                    str(
                        "DB_URL",
                        "jdbc:mysql://${str("DB_HOST", "localhost")}:${int("DB_PORT", DEFAULT_DB_PORT)}/" +
                            str("DB_NAME", "linguaai") +
                            "?connectionTimeZone=UTC&useSSL=false&allowPublicKeyRetrieval=true",
                    ),
                dbUser = str("DB_USER", "linguaai"),
                dbPassword = str("DB_PASSWORD", "linguaai"),
                runMigrations = str("RUN_MIGRATIONS", "true").toBoolean(),
                jwtSecret = str("JWT_SECRET", "dev-only-secret-change-me-in-production-0123456789abcdef"),
                accessTokenTtlMinutes =
                    long("JWT_ACCESS_TOKEN_EXPIRY_MINUTES", DEFAULT_ACCESS_TOKEN_TTL_MINUTES),
                refreshTokenTtlDays =
                    long("JWT_REFRESH_TOKEN_EXPIRY_DAYS", DEFAULT_REFRESH_TOKEN_TTL_DAYS),
                aiProvider =
                    when (str("AI_PROVIDER", "mock").lowercase()) {
                        "openai-compatible", "openai", "deepseek" -> AiProviderKind.OPENAI_COMPATIBLE
                        else -> AiProviderKind.MOCK
                    },
                aiBaseUrl = str("AI_BASE_URL", "https://api.openai.com/v1"),
                aiApiKey = str("AI_API_KEY", ""),
                aiModel = str("AI_MODEL", "gpt-4o-mini"),
                aiTimeoutSeconds = int("AI_TIMEOUT_SECONDS", 60),
                aiRateLimitPerMinute =
                    int("AI_RATE_LIMIT_PER_MINUTE", DEFAULT_AI_RATE_LIMIT_PER_MINUTE),
                aiMockScenario = env("AI_MOCK_SCENARIO")?.takeIf { it.isNotBlank() },
                aiEmbeddingModel = str("AI_EMBEDDING_MODEL", "text-embedding-3-small"),
                ragEnabled = str("RAG_ENABLED", "true").toBoolean(),
                ragTopK = int("RAG_TOP_K", DEFAULT_RAG_TOP_K),
                ragMaxContextChars = int("RAG_MAX_CONTEXT_CHARS", DEFAULT_RAG_MAX_CONTEXT_CHARS),
                ragCandidateLimit = int("RAG_SQL_CANDIDATE_LIMIT", DEFAULT_RAG_CANDIDATE_LIMIT),
                ragAutoIndex = str("RAG_AUTO_INDEX", "true").toBoolean(),
                qdrantUrl = env("QDRANT_URL")?.takeIf { it.isNotBlank() },
                qdrantCollection = str("QDRANT_COLLECTION", "linguaai_knowledge"),
                opsToken = str("OPS_TOKEN", DEV_OPS_TOKEN),
            )
        }
    }
}
