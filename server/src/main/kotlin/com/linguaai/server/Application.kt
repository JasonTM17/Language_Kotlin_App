package com.linguaai.server

import com.linguaai.server.config.AppConfig
import com.linguaai.server.db.DatabaseFactory
import com.linguaai.server.plugins.configureAuthentication
import com.linguaai.server.plugins.configureMonitoring
import com.linguaai.server.plugins.configureSerialization
import com.linguaai.server.plugins.configureStatusPages
import com.linguaai.server.repository.AuthRepository
import com.linguaai.server.repository.ContentRepository
import com.linguaai.server.routes.configureAiRoutes
import com.linguaai.server.routes.configureAuthRoutes
import com.linguaai.server.routes.configureContentRoutes
import com.linguaai.server.routes.configureProfileRoutes
import com.linguaai.server.routes.configureProgressRoutes
import com.linguaai.server.routes.configureQuizRoutes
import com.linguaai.server.routes.configureRouting
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlin.system.exitProcess

fun main() {
    // Fail fast rather than boot with a publicly known signing key. Docker
    // Compose already enforces JWT_SECRET via ${JWT_SECRET:?}; this guard
    // covers bare-jar and other deployment paths.
    if (System.getenv("APP_ENV") == "production" && System.getenv("JWT_SECRET").isNullOrBlank()) {
        System.err.println("Refusing to start: JWT_SECRET must be set when APP_ENV=production")
        exitProcess(1)
    }

    val config = AppConfig.fromEnv()
    embeddedServer(
        factory = Netty,
        port = config.serverPort,
        host = "0.0.0.0",
        module = { module(config) },
    ).start(wait = true)
}

/**
 * Composition root: database first, then cross-cutting plugins, then routes.
 */
fun Application.module(config: AppConfig = AppConfig.fromEnv()) {
    DatabaseFactory.init(config)

    val authRepository = AuthRepository()
    val contentRepository = ContentRepository()

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
    configureAiRoutes(config, authRepository, contentRepository)
    configureProgressRoutes()
}
