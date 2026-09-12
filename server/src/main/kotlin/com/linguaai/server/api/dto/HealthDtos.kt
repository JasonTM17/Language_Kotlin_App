package com.linguaai.server.api.dto

import kotlinx.serialization.Serializable

/** Liveness payload for `GET /api/v1/health`. */
@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val version: String,
)
