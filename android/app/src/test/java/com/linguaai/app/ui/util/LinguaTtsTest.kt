package com.linguaai.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LinguaTtsTest {
    @Test
    fun `localeFromCode maps standard codes properly`() {
        assertEquals(Locale.JAPANESE, LinguaTts.localeFromCode("ja"))
        assertEquals(Locale.ENGLISH, LinguaTts.localeFromCode("en"))
        assertEquals(Locale.FRENCH, LinguaTts.localeFromCode("fr"))
        assertEquals(Locale.GERMAN, LinguaTts.localeFromCode("de"))
        assertEquals(Locale.KOREAN, LinguaTts.localeFromCode("ko"))
        assertEquals(Locale("vi", "VN"), LinguaTts.localeFromCode("vi"))
    }

    @Test
    fun `resolveLocale auto detects Japanese characters`() {
        val locale = LinguaTts.resolveLocale("こんにちは", null)
        assertEquals(Locale.JAPANESE, locale)
    }

    @Test
    fun `resolveLocale auto detects Vietnamese tones`() {
        val locale = LinguaTts.resolveLocale("xin chào", null)
        assertEquals(Locale("vi", "VN"), locale)
    }

    @Test
    fun `resolveLocale respects explicit language code override`() {
        val locale = LinguaTts.resolveLocale("bonjour", "fr")
        assertEquals(Locale.FRENCH, locale)
    }
}
