package com.linguaai.server.routes

import com.linguaai.server.api.dto.HealthResponse
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureRouting() {
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
