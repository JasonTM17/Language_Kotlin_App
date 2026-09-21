package com.linguaai.app.data.remote

import com.linguaai.app.domain.model.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `toAppError` is the one place a transport failure becomes a domain error, and
 * its branch order *is* its precedence: the first branch whose server code or
 * HTTP status matches wins. Reordering the branches would silently change which
 * message the UI shows, so the order is pinned here rather than left to review.
 */
class SafeApiCallTest {
    /**
     * The tutor's 429 is only actionable if the learner can see how long to
     * wait, so the server's Retry-After grant has to survive the transport.
     */
    @Test
    fun `retry-after grant survives the transport mapping`() {
        val error = toAppError(429, null, "20")

        assertEquals(20L, (error as AppError.RateLimited).retryAfterSeconds)
    }

    @Test
    fun `a missing or malformed retry-after header leaves the grant unknown`() {
        assertNull((toAppError(429, null) as AppError.RateLimited).retryAfterSeconds)
        assertNull((toAppError(429, null, "Wed, 21 Oct 2026 07:28:00 GMT") as AppError.RateLimited).retryAfterSeconds)
    }

    private fun envelope(
        code: String,
        message: String = "Something went wrong",
    ): String = """{"error":{"code":"$code","message":"$message","requestId":"req-1"}}"""

    // ---- HTTP status mapping ----

    @Test
    fun `401 is unauthorised`() {
        assertEquals(AppError.Unauthorized, toAppError(401, null))
    }

    @Test
    fun `429 is rate limited`() {
        assertEquals(AppError.RateLimited(), toAppError(429, null))
    }

    @Test
    fun `409 is a conflict`() {
        assertEquals(AppError.Conflict, toAppError(409, null))
    }

    @Test
    fun `400 and 422 are validation failures`() {
        assertEquals(AppError.Validation(), toAppError(400, null))
        assertEquals(AppError.Validation(), toAppError(422, null))
    }

    @Test
    fun `403 is forbidden`() {
        assertEquals(AppError.Forbidden, toAppError(403, null))
    }

    @Test
    fun `404 is not found`() {
        assertEquals(AppError.NotFound, toAppError(404, null))
    }

    @Test
    fun `the whole 5xx range is a server error`() {
        assertEquals(AppError.ServerError, toAppError(500, null))
        // 503 used to be listed separately from `500..599`, which made that
        // clause dead code. It still has to map the same way.
        assertEquals(AppError.ServerError, toAppError(503, null))
        assertEquals(AppError.ServerError, toAppError(599, null))
    }

    @Test
    fun `a status outside every branch is unknown`() {
        assertEquals(AppError.Unknown, toAppError(418, null))
        assertEquals(AppError.Unknown, toAppError(600, null))
    }

    // ---- Server envelope mapping ----

    @Test
    fun `the server code is read from the envelope`() {
        assertEquals(AppError.Unauthorized, toAppError(200, envelope("INVALID_CREDENTIALS")))
        assertEquals(AppError.RateLimited(), toAppError(200, envelope("RATE_LIMITED")))
        assertEquals(AppError.Conflict, toAppError(200, envelope("CONFLICT")))
    }

    @Test
    fun `a validation failure carries the server message`() {
        assertEquals(
            AppError.Validation(reason = "Email is required"),
            toAppError(400, envelope("VALIDATION_ERROR", "Email is required")),
        )
    }

    @Test
    fun `an unparseable envelope falls back to the status`() {
        assertEquals(AppError.ServerError, toAppError(502, "not json at all"))
        // Valid JSON, but not the envelope the DTO describes.
        assertEquals(AppError.Unknown, toAppError(200, """{"error":{}}"""))
    }

    // ---- Precedence, which is the part that is easy to break ----

    @Test
    fun `the server code outranks a later http branch`() {
        // CONFLICT is branch 3, 404 is branch 6: the earlier branch wins.
        assertEquals(AppError.Conflict, toAppError(404, envelope("CONFLICT")))
    }

    @Test
    fun `an earlier http branch outranks a later server code`() {
        // 401 is branch 1, VALIDATION_ERROR is branch 4. This is the case a
        // "server code first" rewrite would silently change.
        assertEquals(AppError.Unauthorized, toAppError(401, envelope("VALIDATION_ERROR")))
    }

    @Test
    fun `an unknown server code does not mask the status`() {
        assertEquals(AppError.Unauthorized, toAppError(401, envelope("SOMETHING_NEW")))
    }
}
