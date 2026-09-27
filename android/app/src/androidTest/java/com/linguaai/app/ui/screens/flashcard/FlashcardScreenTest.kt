package com.linguaai.app.ui.screens.flashcard

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.linguaai.app.domain.model.VocabularyCard
import com.linguaai.app.ui.theme.LinguaAiTheme
import com.linguaai.app.ui.theme.PrimaryDark
import com.linguaai.app.ui.theme.PrimaryLight
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class FlashcardScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun gradeButtonsShareThemePrimary_andCaptureVietnameseReviewScreen() {
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val localizedConfiguration =
            Configuration(baseContext.resources.configuration).apply {
                setLocale(Locale.forLanguageTag("vi-VN"))
            }
        val localizedContext = baseContext.createConfigurationContext(localizedConfiguration)
        var darkTheme by mutableStateOf(false)

        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration,
            ) {
                LinguaAiTheme(darkTheme = darkTheme) {
                    FlashcardScreenContent(
                        state = reviewState(),
                        onBack = {},
                        onEvent = {},
                        onStartCramSession = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        assertGradeButtonColors(PrimaryLight.toArgb())
        captureReferenceImage()

        composeRule.runOnIdle { darkTheme = true }
        composeRule.waitForIdle()
        assertGradeButtonColors(PrimaryDark.toArgb())
    }

    private fun assertGradeButtonColors(expectedColor: Int) {
        listOf("Quên", "Khó", "Được", "Dễ").forEach { label ->
            val bitmap = composeRule.onNodeWithText(label).captureToImage().asAndroidBitmap()
            val interiorColor = bitmap.getPixel(bitmap.width / 4, bitmap.height / 2)
            assertEquals("$label should use the theme primary color", expectedColor, interiorColor)
        }
    }

    private fun captureReferenceImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "flashcard")
        check(directory.exists() || directory.mkdirs())
        val file = File(directory, "flashcard-review-vi.png")
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun reviewState(): FlashcardUiState {
        val sample =
            VocabularyCard(
                id = 1,
                languageId = 1,
                level = "N3",
                word = "環境",
                reading = "かんきょう",
                pronunciation = null,
                meaning = "Môi trường",
                example = "環境を守ることは大切です。",
                exampleTranslation = "Bảo vệ môi trường là điều quan trọng.",
                category = "Daily",
                favorite = false,
                masteryLevel = 1,
            )
        return FlashcardUiState(
            isLoading = false,
            queue = List(20) { index -> sample.copy(id = index.toLong() + 1) },
            languageCode = "ja",
            isRevealed = true,
        )
    }
}
