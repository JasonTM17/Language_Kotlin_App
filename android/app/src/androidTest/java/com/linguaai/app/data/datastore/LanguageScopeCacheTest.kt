package com.linguaai.app.data.datastore

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageScopeCacheTest {
    private lateinit var settingsDataStore: SettingsDataStore

    @Before
    fun setUp() {
        settingsDataStore = SettingsDataStore(ApplicationProvider.getApplicationContext())
        runBlocking { settingsDataStore.setLearningLanguageId(null) }
    }

    @After
    fun tearDown() {
        runBlocking { settingsDataStore.setLearningLanguageId(null) }
    }

    @Test
    fun languageScopeIsClearedAtTheAccountBoundary() =
        runBlocking {
            settingsDataStore.setLearningLanguageId(6L)
            assertEquals(6L, settingsDataStore.learningLanguageId.first())

            settingsDataStore.setLearningLanguageId(null)

            assertNull(settingsDataStore.learningLanguageId.first())
        }
}
