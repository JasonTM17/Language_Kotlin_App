package com.linguaai.server.plugins

import com.linguaai.server.api.ApiException
import com.linguaai.server.api.ErrorBody
import com.linguaai.server.api.ErrorCodes
import com.linguaai.server.api.ErrorResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import java.util.UUID

/** Short request id for logs — long enough to correlate, short enough to read. */
private const val REQUEST_ID_LENGTH = 8

private val RequestIdKey = AttributeKey<String>("requestId")

/** Per-request correlation id used in logs and error envelopes. */
val ApplicationCall.requestId: String
    get() = attributes.getOrNull(RequestIdKey) ?: "unknown"

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            },
        )
    }
}

fun Application.configureMonitoring() {
    // Assign a correlation id before any logging happens downstream.
    intercept(ApplicationCallPipeline.Monitoring) {
        val target = call
        if (target.attributes.getOrNull(RequestIdKey) == null) {
            target.attributes.put(RequestIdKey, UUID.randomUUID().toString().take(REQUEST_ID_LENGTH))
        }
    }
    install(CallLogging) {
        level = org.slf4j.event.Level.INFO
        filter { call -> call.request.path().startsWith("/api") }
        format { call ->
            val status = call.response.status()?.value ?: "-"
            "${call.request.httpMethod.value} ${call.request.path()} -> $status [${call.requestId}]"
        }
    }
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respondError(cause.status, cause.code, cause.message ?: cause.code, cause.retryAfterSeconds)
        }
        exception<BadRequestException> { call, _ ->
            call.respondError(HttpStatusCode.BadRequest, ErrorCodes.VALIDATION, "Malformed request body.")
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception on ${call.request.path()}", cause)
            call.respondError(
                HttpStatusCode.InternalServerError,
                ErrorCodes.INTERNAL,
                "An unexpected error occurred.",
            )
        }
    }
}

private suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    code: String,
    message: String,
    retryAfterSeconds: Long? = null,
) {
    if (retryAfterSeconds != null) {
        response.header(io.ktor.http.HttpHeaders.RetryAfter, retryAfterSeconds.toString())
    }
    respondText(
        text =
            jsonMapper.encodeToString(
                ErrorResponse.serializer(),
                ErrorResponse(ErrorBody(code = code, message = message, requestId = requestId)),
            ),
        contentType = ContentType.Application.Json,
        status = status,
    )
}

private val jsonMapper =
    Json {
        encodeDefaults = true
    }
