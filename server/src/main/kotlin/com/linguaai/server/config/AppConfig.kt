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

        fun fromEnv(env: (String) -> String? = System::getenv): AppConfig {
            fun str(key: String, default: String) = env(key)?.takeIf { it.isNotBlank() } ?: default
            fun int(key: String, default: Int) = env(key)?.toIntOrNull() ?: default
            fun long(key: String, default: Long) = env(key)?.toLongOrNull() ?: default

            return AppConfig(
                serverPort = int("SERVER_PORT", DEFAULT_SERVER_PORT),
                dbUrl = str(
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
                aiProvider = when (str("AI_PROVIDER", "mock").lowercase()) {
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
            )
        }
    }
}
