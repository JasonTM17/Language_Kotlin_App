package com.linguaai.server.ai

import java.time.Instant
import java.util.Deque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * In-process sliding-window rate limiter keyed by user id (post-JWT).
 * Single-instance assumption: document Redis as the multi-replica upgrade.
 */
class AiRateLimiter(
    private val maxRequestsPerMinute: Int,
) {
    private data class Window(
        val hits: Deque<Instant> = ConcurrentLinkedDeque(),
    )

    private val windows = ConcurrentHashMap<Long, Window>()
    private val windowMillis = 60_000L

    /** Returns true when the request is allowed; false means HTTP 429. */
    fun tryAcquire(
        userId: Long,
        now: Instant = Instant.now(),
    ): Boolean {
        val window = windows.computeIfAbsent(userId) { Window() }
        synchronized(window) {
            while (!window.hits.isEmpty() && window.hits.peekFirst().isBefore(now.minusMillis(windowMillis))) {
                window.hits.pollFirst()
            }
            if (window.hits.size >= maxRequestsPerMinute) {
                return false
            }
            window.hits.addLast(now)
            return true
        }
    }
}
