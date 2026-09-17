package com.linguaai.server.ops

import java.util.concurrent.atomic.AtomicBoolean

/**
 * One mutual-exclusion gate shared by seed, reindex and purge. Splitting the
 * guard per operation let a seed and a reindex run concurrently (each claiming
 * exclusivity), and purge had no guard at all — leaving partial synthetic rows
 * behind.
 */
class OpsSingleFlight {
    private val running = AtomicBoolean(false)

    fun tryBegin(): Boolean = running.compareAndSet(false, true)

    fun end() {
        running.set(false)
    }
}
