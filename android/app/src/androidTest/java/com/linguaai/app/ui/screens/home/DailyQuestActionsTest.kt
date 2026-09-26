package com.linguaai.app.ui.screens.home

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.linguaai.app.domain.model.DailyQuest
import com.linguaai.app.domain.model.DailyQuestType
import com.linguaai.app.domain.model.VocabularyCard
import com.linguaai.app.ui.theme.LinguaAiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class DailyQuestActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unfinishedActionsStartMatchingQuest_andCompletedAndClaimedKeepTheirActions() {
        val started = mutableListOf<DailyQuestType>()
        val claimed = mutableListOf<DailyQuestType>()
        render(
            languageTag = "en",
            quests =
                listOf(
                    quest(DailyQuestType.AI_CHAT, progress = 0),
                    quest(DailyQuestType.FLASHCARDS, progress = 3),
                    quest(DailyQuestType.QUIZ, progress = 1),
                    quest(DailyQuestType.WORD_OF_DAY, progress = 0),
                ),
            onStart = started::add,
            onClaim = claimed::add,
        )

        composeRule
            .onAllNodesWithText("Start")[0]
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Start: Chat with AI Tutor")
            .performClick()
        composeRule
            .onNodeWithText("Continue")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Continue: Review 10 flashcards")
            .performClick()
        composeRule.onAllNodesWithText("Start")[1].assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Claim +50 XP").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(DailyQuestType.AI_CHAT, DailyQuestType.FLASHCARDS, DailyQuestType.WORD_OF_DAY),
                started,
            )
            assertEquals(listOf(DailyQuestType.QUIZ), claimed)
        }
    }

    @Test
    fun startAndContinueActionsAndRewardStatesUseVietnameseStrings() {
        val started = mutableListOf<DailyQuestType>()
        val claimed = mutableListOf<DailyQuestType>()
        render(
            languageTag = "vi",
            quests =
                listOf(
                    quest(DailyQuestType.AI_CHAT, progress = 0),
                    quest(DailyQuestType.FLASHCARDS, progress = 3),
                    quest(DailyQuestType.QUIZ, progress = 1),
                    quest(DailyQuestType.WORD_OF_DAY, progress = 1, isClaimed = true),
                ),
            onStart = started::add,
            onClaim = claimed::add,
        )

        composeRule
            .onNodeWithText("Bắt đầu")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Bắt đầu: Trò chuyện với Gia sư AI")
        composeRule
            .onNodeWithText("Tiếp tục")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Tiếp tục: Ôn tập 10 thẻ từ vựng")
        composeRule.onNodeWithText("Nhận +50 XP").assertIsDisplayed()
        composeRule.onNodeWithText("Đã nhận ✓").assertIsDisplayed().assertHasNoClickAction()
        composeRule.runOnIdle {
            assertEquals(emptyList<DailyQuestType>(), started)
            assertEquals(emptyList<DailyQuestType>(), claimed)
        }
    }

    @Test
    fun wordOfDayProgressUsesExistingActionOnlyWhenTheCurrentWordExists() {
        var progressCalls = 0
        val navigated = mutableListOf<Pair<DailyQuestType, VocabularyCard?>>()
        val word =
            VocabularyCard(
                id = 1,
                languageId = 1,
                level = "A1",
                word = "hello",
                reading = null,
                pronunciation = null,
                meaning = "greeting",
                example = null,
                exampleTranslation = null,
                category = "Daily",
                favorite = false,
                masteryLevel = 0,
            )

        startDailyQuest(
            questType = DailyQuestType.WORD_OF_DAY,
            wordOfDay = word,
            onPracticeWord = { progressCalls++ },
            onNavigate = { type, card -> navigated += type to card },
        )
        startDailyQuest(
            questType = DailyQuestType.WORD_OF_DAY,
            wordOfDay = null,
            onPracticeWord = { progressCalls++ },
            onNavigate = { type, card -> navigated += type to card },
        )

        assertEquals(1, progressCalls)
        assertEquals(listOf(DailyQuestType.WORD_OF_DAY to word, DailyQuestType.WORD_OF_DAY to null), navigated)
    }

    private fun render(
        languageTag: String,
        quests: List<DailyQuest>,
        onStart: (DailyQuestType) -> Unit,
        onClaim: (DailyQuestType) -> Unit,
    ) {
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val localizedConfiguration =
            Configuration(baseContext.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(languageTag))
            }
        val localizedContext = baseContext.createConfigurationContext(localizedConfiguration)

        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration,
            ) {
                LinguaAiTheme(darkTheme = false) {
                    DailyQuestsCard(
                        quests = quests,
                        onClaimQuest = onClaim,
                        onStartQuest = onStart,
                    )
                }
            }
        }
    }

    private fun quest(
        type: DailyQuestType,
        progress: Int,
        isClaimed: Boolean = false,
    ) = DailyQuest(type = type, progress = progress, isClaimed = isClaimed)
}
