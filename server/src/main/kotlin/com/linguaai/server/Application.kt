package com.linguaai.server

import com.linguaai.server.config.AppConfig
import com.linguaai.server.plugins.configureMonitoring
import com.linguaai.server.plugins.configureSerialization
import com.linguaai.server.plugins.configureStatusPages
import com.linguaai.server.repository.ContentRepository
import com.linguaai.server.routes.configureContentRoutes
import com.linguaai.server.routes.configureRouting
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
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
    val database = com.linguaai.server.db.DatabaseFactory.init(config)
    database // keep reference for tests that need the pooled datasource

    configureSerialization()
    configureMonitoring()
    configureStatusPages()
    configureRouting(config)
    configureContentRoutes(ContentRepository())
}
