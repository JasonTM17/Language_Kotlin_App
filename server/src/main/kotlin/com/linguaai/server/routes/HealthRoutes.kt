package com.linguaai.server.routes

import com.linguaai.server.config.AppConfig
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val version: String,
)

fun Application.configureRouting(config: AppConfig) {
    routing {
        get("/api/v1/health") {
            call.respond(
                HealthResponse(
                    status = "ok",
                    service = "linguaai-server",
                    version = "1.0.0",
                ),
            )
        }
    }
}
