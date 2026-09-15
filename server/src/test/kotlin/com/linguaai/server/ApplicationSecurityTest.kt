package com.linguaai.server

import com.linguaai.server.config.AppConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplicationSecurityTest {
    @Test
    fun `production rejects blank and development ops tokens`() {
        assertTrue(productionOpsTokenIsUnsafe("production", null))
        assertTrue(productionOpsTokenIsUnsafe("production", ""))
        assertTrue(productionOpsTokenIsUnsafe("production", AppConfig.DEV_OPS_TOKEN))
    }

    @Test
    fun `production accepts a configured ops token and non production stays flexible`() {
        assertFalse(productionOpsTokenIsUnsafe("production", "operator-secret"))
        assertFalse(productionOpsTokenIsUnsafe("development", AppConfig.DEV_OPS_TOKEN))
        assertFalse(productionOpsTokenIsUnsafe(null, null))
    }
}
