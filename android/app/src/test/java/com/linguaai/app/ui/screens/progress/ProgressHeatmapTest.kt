package com.linguaai.app.ui.screens.progress

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The heatmap intensity ladder must stay stable: UI colour mapping and any
 * future analytics both read the same buckets.
 */
class ProgressHeatmapTest {
    @Test
    fun `zero minutes maps to the empty bucket`() {
        assertEquals(0, intensityBucket(0))
        assertEquals(0, intensityBucket(-3))
    }

    @Test
    fun `short sessions land in the low buckets`() {
        assertEquals(1, intensityBucket(1))
        assertEquals(1, intensityBucket(4))
        assertEquals(2, intensityBucket(5))
        assertEquals(2, intensityBucket(14))
    }

    @Test
    fun `long sessions saturate the ladder`() {
        assertEquals(3, intensityBucket(15))
        assertEquals(3, intensityBucket(24))
        assertEquals(4, intensityBucket(25))
        assertEquals(4, intensityBucket(120))
    }
}
