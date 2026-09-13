package com.linguaai.app.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Input validation rules.
 *
 * These gate every login and registration attempt, and they are the cheapest
 * place to catch a malformed value before it costs a network round trip. The
 * cases below are the boundaries where a hand-rolled rule usually drifts from
 * what the user expects.
 */
class ValidatorsTest {
    // ---- email ----

    @Test
    fun `blank email is rejected`() {
        assertNotNull(Validators.validateEmail(""))
        assertNotNull(Validators.validateEmail("   "))
    }

    @Test
    fun `common email shapes are accepted`() {
        for (email in listOf(
            "son@example.com",
            "son.tran@example.co.uk",
            "son+tag@example.com",
            "son_tran@example-domain.com",
        )) {
            assertNull("$email should be valid", Validators.validateEmail(email))
        }
    }

    @Test
    fun `malformed emails are rejected`() {
        for (email in listOf(
            "no-at-sign",
            "missing@tld",
            "@example.com",
            "spaces in@example.com",
            "trailing@example.",
        )) {
            assertNotNull("$email should be invalid", Validators.validateEmail(email))
        }
    }

    @Test
    fun `surrounding whitespace does not invalidate an email`() {
        // Users paste addresses with stray spaces; the validator trims before
        // matching, so this must pass rather than fail on a trailing space.
        assertNull(Validators.validateEmail("  son@example.com  "))
    }

    // ---- password ----

    @Test
    fun `password must be at least eight characters`() {
        assertNotNull(Validators.validatePassword("short"))
        assertNotNull(Validators.validatePassword("seven77"))
        assertNull(Validators.validatePassword("eight888"))
    }

    @Test
    fun `blank password is rejected`() {
        assertNotNull(Validators.validatePassword(""))
        assertNotNull(Validators.validatePassword("        "))
    }

    // ---- username ----

    @Test
    fun `username must be at least three characters`() {
        assertNotNull(Validators.validateUsername("ab"))
        assertNotNull(Validators.validateUsername(""))
        assertNull(Validators.validateUsername("son"))
    }

    @Test
    fun `username length is measured on the trimmed value`() {
        // RegisterUseCase sends the trimmed username to the server, so a padded
        // value must not be able to smuggle a too-short name past validation.
        assertEquals(
            "Username must be at least 3 characters",
            Validators.validateUsername(" a "),
        )
        assertNull(Validators.validateUsername("  son  "))
    }
}
