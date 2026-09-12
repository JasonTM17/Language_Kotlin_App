package com.linguaai.server.ai

import kotlinx.serialization.Serializable

/** One message in an AI exchange. */
@Serializable
data class AiMessage(
    val role: String, // system | user | assistant
    val content: String,
)

/**
 * Provider-agnostic chat request. `jsonMode` asks the provider for a strict
 * JSON response (used by quiz generation and scoring); `scenarioHint` is only
 * consumed by the mock provider for deterministic tests and demos.
 */
data class AiChatRequest(
    val messages: List<AiMessage>,
    val temperature: Double = 0.7,
    val maxTokens: Int = 1024,
    val jsonMode: Boolean = false,
    val scenarioHint: String? = null,
)

data class AiChatResponse(
    val content: String,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
)

/** Transport/provider failures normalized for the gateway. */
class AiProviderException(
    val kind: Kind,
    message: String,
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    enum class Kind { TIMEOUT, RATE_LIMITED, PROVIDER_ERROR, INVALID_RESPONSE, EMPTY_RESPONSE, NETWORK }
}

/**
 * The single seam between LinguaAI and any AI vendor. The Android app never
 * talks to a provider directly and never sees API keys (ADR-002).
 */
interface AiProvider {
    suspend fun chat(request: AiChatRequest): AiChatResponse
}
