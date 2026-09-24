package com.linguaai.app.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CachedLearningLanguageTest {
    @Test
    fun `offline locale is used only when cached and profile language IDs match`() {
        assertEquals("ja", cachedLearningLanguageCode(profileLanguageId = 7L, cachedLanguageId = 7L, cachedLanguageCode = " ja "))
        assertNull(cachedLearningLanguageCode(profileLanguageId = 7L, cachedLanguageId = 8L, cachedLanguageCode = "en"))
        assertNull(cachedLearningLanguageCode(profileLanguageId = null, cachedLanguageId = 7L, cachedLanguageCode = "ja"))
        assertNull(cachedLearningLanguageCode(profileLanguageId = 7L, cachedLanguageId = 7L, cachedLanguageCode = " "))
    }
}
