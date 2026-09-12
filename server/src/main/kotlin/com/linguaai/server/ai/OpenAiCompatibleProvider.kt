package com.linguaai.server.ai

import com.linguaai.server.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add

/**
 * Talks to any OpenAI-compatible chat completions endpoint (OpenAI, DeepSeek,
 * Groq, self-hosted gateways). The API key lives only on the server.
 */
class OpenAiCompatibleProvider(
    private val config: AppConfig,
    private val client: HttpClient,
) : AiProvider {

    @Serializable
    private data class ProviderMessage(val role: String, val content: String)

    @Serializable
    private data class ProviderChoice(val message: ProviderMessage)

    @Serializable
    private data class ProviderUsage(
        @SerialName("prompt_tokens") val promptTokens: Int? = null,
        @SerialName("completion_tokens") val completionTokens: Int? = null,
    )

    @Serializable
    private data class ProviderResponse(
        val choices: List<ProviderChoice> = emptyList(),
        val usage: ProviderUsage? = null,
    )

    override suspend fun chat(request: AiChatRequest): AiChatResponse {
        val payload = buildJsonObject {
            put("model", config.aiModel)
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            putJsonArray("messages") {
                request.messages.forEach { message ->
                    add(
                        buildJsonObject {
                            put("role", message.role)
                            put("content", message.content)
                        },
                    )
                }
            }
            if (request.jsonMode) {
                putJsonObject("response_format") { put("type", "json_object") }
            }
        }

        var attempt = 0
        while (true) {
            attempt++
            try {
                val response: HttpResponse = client.post("${config.aiBaseUrl.trimEnd('/')}/chat/completions") {
                    headers { append(HttpHeaders.Authorization, "Bearer ${config.aiApiKey}") }
                    setBody(payload)
                }
                if (response.status.value == 429) {
                    throw AiProviderException(
                        AiProviderException.Kind.RATE_LIMITED,
                        "AI provider rate limited",
                        retryAfterSeconds = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull(),
                    )
                }
                if (!response.status.isSuccess()) {
                    throw AiProviderException(
                        AiProviderException.Kind.PROVIDER_ERROR,
                        "AI provider returned ${response.status.value}",
                    )
                }
                val body = response.body<ProviderResponse>()
                val content = body.choices.firstOrNull()?.message?.content
                if (content.isNullOrBlank()) {
                    throw AiProviderException(
                        AiProviderException.Kind.EMPTY_RESPONSE,
                        "AI provider returned an empty response",
                    )
                }
                return AiChatResponse(
                    content = content,
                    promptTokens = body.usage?.promptTokens,
                    completionTokens = body.usage?.completionTokens,
                )
            } catch (e: AiProviderException) {
                throw e
            } catch (e: IOException) {
                // Connection setup failures may be retried once; a request that
                // already reached the provider is never re-sent (not idempotent).
                if (attempt >= MAX_ATTEMPTS) {
                    // Carry the cause so the operator can tell DNS failure from
                    // connection reset from read timeout.
                    throw AiProviderException(
                        AiProviderException.Kind.NETWORK,
                        "AI provider unreachable",
                        cause = e,
                    )
                }
            }
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
    }
}
