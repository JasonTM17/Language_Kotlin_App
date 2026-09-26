package com.linguaai.app.ui.screens.listenandtype

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.linguaai.app.domain.model.ListenAndTypeRound
import com.linguaai.app.domain.model.VocabularyCard
import com.linguaai.app.ui.theme.LinguaAiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

class ListenAndTypeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun correctAnswer_showsWordMeaningScoreAndRetry() {
        var state by mutableStateOf(readyState(listOf(card(id = 1, word = "hello", meaning = "greeting"))))
        var playCount = 0

        composeRule.setContent {
            LinguaAiTheme(darkTheme = false) {
                ListenAndTypeContent(
                    state = state,
                    onBack = {},
                    onOpenVocabulary = {},
                    onRetryLoad = {},
                    onSubmitAnswer = { answer -> state = state.copy(round = state.round!!.submitAnswer(answer)) },
                    onAdvance = { state = state.copy(round = state.round!!.advance()) },
                    onRetryRound = { state = state.copy(round = state.round!!.retry(Random(2))) },
                    onPlayWord = { playCount++ },
                )
            }
        }

        captureFrame("listen-and-type-question.png")
        composeRule.onNodeWithText("Play word audio").performClick()
        composeRule.onNodeWithText("Your answer").performTextInput("  HELLO ")
        composeRule.onNodeWithContentDescription("Check answer").performClick()

        composeRule.onNodeWithText("Correct!").assertIsDisplayed()
        composeRule.onNodeWithText("Word: hello").assertIsDisplayed()
        composeRule.onNodeWithText("greeting").assertIsDisplayed()
        captureFrame("listen-and-type-feedback.png")
        composeRule.onNodeWithText("Next word").performClick()
        composeRule.onNodeWithText("Round complete").assertIsDisplayed()
        composeRule.onNodeWithText("You got 1 out of 1 words right.").assertIsDisplayed()
        captureFrame("listen-and-type-complete.png")
        composeRule.onNodeWithText("Play again").performClick()
        composeRule.onNodeWithText("Question 1 of 1").assertIsDisplayed()

        composeRule.runOnIdle { assertEquals(1, playCount) }
    }

    @Test
    fun incorrectAnswer_revealsAnswerAndMeaning() {
        var state by mutableStateOf(readyState(listOf(card(id = 2, word = "café", meaning = "coffee shop"))))

        composeRule.setContent {
            LinguaAiTheme(darkTheme = false) {
                ListenAndTypeContent(
                    state = state,
                    onBack = {},
                    onOpenVocabulary = {},
                    onRetryLoad = {},
                    onSubmitAnswer = { answer -> state = state.copy(round = state.round!!.submitAnswer(answer)) },
                    onAdvance = { state = state.copy(round = state.round!!.advance()) },
                    onRetryRound = {},
                    onPlayWord = {},
                )
            }
        }

        composeRule.onNodeWithText("Your answer").performTextInput("cafe")
        composeRule.onNodeWithContentDescription("Check answer").performClick()

        composeRule.onNodeWithText("Not quite. Here is the answer:").assertIsDisplayed()
        composeRule.onNodeWithText("Word: café").assertIsDisplayed()
        composeRule.onNodeWithText("coffee shop").assertIsDisplayed()
        composeRule.onNodeWithText("Next word").assertIsDisplayed()
    }

    @Test
    fun emptyRound_offersVocabularyAndBackActions() {
        var vocabularyOpens = 0
        val state =
            ListenAndTypeUiState(
                isLoading = false,
                round = ListenAndTypeRound.start(languageId = 1, candidates = emptyList()),
            )

        composeRule.setContent {
            LinguaAiTheme(darkTheme = false) {
                ListenAndTypeContent(
                    state = state,
                    onBack = {},
                    onOpenVocabulary = { vocabularyOpens++ },
                    onRetryLoad = {},
                    onSubmitAnswer = {},
                    onAdvance = {},
                    onRetryRound = {},
                    onPlayWord = {},
                )
            }
        }

        composeRule.onNodeWithText("No words ready for this round").assertIsDisplayed()
        composeRule.onNodeWithText("Open vocabulary").performClick()

        composeRule.runOnIdle { assertEquals(1, vocabularyOpens) }
    }

    private fun readyState(words: List<VocabularyCard>): ListenAndTypeUiState =
        ListenAndTypeUiState(
            isLoading = false,
            round = ListenAndTypeRound.start(languageId = 1, candidates = words, random = Random(3)),
            languageCode = "en",
        )

    private fun captureFrame(fileName: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "listen-and-type")
        check(directory.exists() || directory.mkdirs())
        val file = File(directory, fileName)
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun card(
        id: Long,
        word: String,
        meaning: String,
    ): VocabularyCard =
        VocabularyCard(
            id = id,
            languageId = 1,
            level = "A1",
            word = word,
            reading = null,
            pronunciation = null,
            meaning = meaning,
            example = null,
            exampleTranslation = null,
            category = "Daily",
            favorite = false,
            masteryLevel = 0,
        )
}
