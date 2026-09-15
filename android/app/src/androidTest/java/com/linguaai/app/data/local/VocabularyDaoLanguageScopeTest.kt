package com.linguaai.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linguaai.app.data.local.entity.PendingSyncOpEntity
import com.linguaai.app.data.local.entity.SyncOpState
import com.linguaai.app.data.local.entity.VocabularyEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Guards the multilingual review boundary against cross-language cache leaks. */
@RunWith(AndroidJUnit4::class)
class VocabularyDaoLanguageScopeTest {
    private lateinit var database: LinguaDatabase

    @Before
    fun setUp() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            database =
                Room
                    .inMemoryDatabaseBuilder(context, LinguaDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            database.vocabularyDao().upsertAll(
                listOf(
                    word(id = 1, languageId = 1, value = "環境"),
                    word(id = 2, languageId = 6, value = "今天"),
                ),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun dueForReview_returnsOnlyTheSelectedLanguage() {
        runBlocking {
            val chinese = database.vocabularyDao().dueForReview(languageId = 6, now = 0, limit = 20)

            assertEquals(listOf("今天"), chinese.map { it.word })
        }
    }

    @Test
    fun applyProgress_updatesAWordWithoutPendingLocalWrite() {
        runBlocking {
            database.vocabularyDao().applyProgressIfNoPending(
                vocabularyId = 2,
                favorite = true,
                masteryLevel = 4,
                reviewCount = 3,
                correctCount = 3,
                wrongCount = 0,
                lastReviewedAt = 100,
                nextReviewAt = 200,
                stateUpdatedAt = 300,
            )

            val updated = database.vocabularyDao().findById(2)
            assertEquals(true, updated?.favorite)
            assertEquals(4, updated?.masteryLevel)
            assertEquals(3, updated?.reviewCount)
            assertEquals(300L, updated?.stateUpdatedAt)
        }
    }

    @Test
    fun applyProgress_doesNotOverwriteAWordWithPendingLocalWrite() {
        runBlocking {
            database.syncDao().enqueue(
                PendingSyncOpEntity(
                    operationId = "pending-favorite",
                    eventType = "VOCABULARY_STATE_SYNC",
                    refId = 2,
                    minutes = 0,
                    occurredAt = 1,
                    state = SyncOpState.STATE_PENDING,
                ),
            )

            database.vocabularyDao().applyProgressIfNoPending(
                vocabularyId = 2,
                favorite = true,
                masteryLevel = 4,
                reviewCount = 3,
                correctCount = 3,
                wrongCount = 0,
                lastReviewedAt = 100,
                nextReviewAt = 200,
                stateUpdatedAt = 300,
            )

            val unchanged = database.vocabularyDao().findById(2)
            assertEquals(false, unchanged?.favorite)
            assertEquals(0, unchanged?.masteryLevel)
            assertEquals(0, unchanged?.reviewCount)
        }
    }

    private fun word(
        id: Long,
        languageId: Long,
        value: String,
    ): VocabularyEntity =
        VocabularyEntity(
            id = id,
            languageId = languageId,
            level = "HSK1",
            word = value,
            reading = null,
            pronunciation = null,
            meaning = value,
            example = null,
            exampleTranslation = null,
            category = "Daily",
        )
}
