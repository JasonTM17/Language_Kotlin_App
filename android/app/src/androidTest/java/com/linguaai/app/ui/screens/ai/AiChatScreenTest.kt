package com.linguaai.app.ui.screens.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.linguaai.app.data.remote.dto.AiSourceDto
import com.linguaai.app.data.remote.dto.PracticeScoreDto
import com.linguaai.app.domain.model.AppError
import com.linguaai.app.ui.theme.LinguaAiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class AiChatScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun generalChat_acceptsInputAndInvokesSend() {
        var state by mutableStateOf(AiChatUiState(isLoading = false))
        var sendCount = 0

        composeRule.setContent {
            TestChatContent(
                state = state,
                onInputChanged = { state = state.copy(input = it) },
                onSend = { sendCount++ },
            )
        }

        composeRule.onNodeWithTag("ai-chat-input").performTextInput("Explain particles")
        composeRule.onNodeWithTag("ai-chat-send").assertIsEnabled().performClick()

        composeRule.runOnIdle { assertEquals(1, sendCount) }
    }

    @Test
    fun practiceChat_exposesScoreActionAndResult() {
        var scoreCount = 0
        val state =
            AiChatUiState(
                isLoading = false,
                conversationId = 42L,
                mode = "conversation-practice",
                practiceScore =
                    PracticeScoreDto(
                        score = 84,
                        grammarScore = 80,
                        vocabularyScore = 86,
                        naturalness = 85,
                        mistakes = listOf("Particle choice"),
                        recommendations = listOf("Practise counters"),
                    ),
            )

        composeRule.setContent {
            TestChatContent(state = state, onScorePractice = { scoreCount++ })
        }

        composeRule.onNodeWithTag("ai-practice-score-result").assertIsDisplayed()
        composeRule.onNodeWithText("Practice score: 84/100").assertIsDisplayed()
        composeRule.onNodeWithTag("ai-practice-score").assertIsEnabled().performClick()

        composeRule.runOnIdle { assertEquals(1, scoreCount) }
    }

    @Test
    fun emptyError_exposesRetryAction() {
        var retryCount = 0

        composeRule.setContent {
            TestChatContent(
                state = AiChatUiState(isLoading = false, error = AppError.ServerError),
                onRetry = { retryCount++ },
            )
        }

        composeRule.onNodeWithText("The server is having trouble. Please try again later.").assertIsDisplayed()
        composeRule.onNodeWithTag("ai-chat-retry").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun pendingReply_offersAStopAction() {
        var stopCount = 0

        composeRule.setContent {
            TestChatContent(
                state =
                    AiChatUiState(
                        isLoading = false,
                        isSending = true,
                        messages =
                            listOf(
                                ChatMessage("USER", "giải thích giúp mình"),
                                ChatMessage("ASSISTANT", "", isPending = true),
                            ),
                    ),
                onStop = { stopCount++ },
            )
        }

        composeRule.onNodeWithTag("ai-chat-stop").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertEquals(1, stopCount) }
    }

    /**
     * The tutor's controls are small by design, but a tap target under 24dp is a
     * miss the learner pays for mid-generation. Asserting the measured bounds
     * keeps the pill's and chip's compact look honest about being reachable: the
     * interrupt control gets Android's full 48dp, in-bubble citations 40dp.
     */
    @Test
    fun chatControls_meetTheMinimumTouchTarget() {
        composeRule.setContent {
            TestChatContent(
                state =
                    AiChatUiState(
                        isLoading = false,
                        isSending = true,
                        messages =
                            listOf(
                                ChatMessage(
                                    "ASSISTANT",
                                    "Use ので for a plain reason clause.",
                                    isPending = true,
                                    sources =
                                        listOf(
                                            AiSourceDto(
                                                title = "Reason clauses node",
                                                sourceType = "GRAMMAR",
                                                sourceId = 12L,
                                                chunkIndex = 0,
                                                level = "N4",
                                                score = 0.91,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                onStop = {},
            )
        }

        for ((tag, minDp) in listOf("ai-chat-stop" to 48, "ai-chat-source" to 40)) {
            composeRule
                .onNodeWithTag(tag)
                .assertWidthIsAtLeast(minDp.dp)
                .assertHeightIsAtLeast(minDp.dp)
        }
    }

    @Test
    fun lessonCitation_opensTheCitedLessonWhenTapped() {
        var opened: AiSourceDto? = null

        composeRule.setContent {
            TestChatContent(
                state =
                    AiChatUiState(
                        isLoading = false,
                        messages =
                            listOf(
                                ChatMessage(
                                    "ASSISTANT",
                                    "Use ので for a plain reason clause.",
                                    sources =
                                        listOf(
                                            AiSourceDto(
                                                title = "Reason clauses",
                                                sourceType = "LESSON",
                                                sourceId = 42L,
                                                chunkIndex = 0,
                                                level = "N4",
                                                score = 0.9,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                onOpenSource = { opened = it },
            )
        }

        composeRule.onNodeWithTag("ai-chat-source").performClick()

        assertEquals("LESSON", opened?.sourceType)
        assertEquals(42L, opened?.sourceId)
    }

    @Test
    fun vocabularyCitation_staysInertBecauseNoDetailRouteCarriesItsId() {
        var opened: AiSourceDto? = null

        composeRule.setContent {
            TestChatContent(
                state =
                    AiChatUiState(
                        isLoading = false,
                        messages =
                            listOf(
                                ChatMessage(
                                    "ASSISTANT",
                                    "ように marks purpose.",
                                    sources =
                                        listOf(
                                            AiSourceDto(
                                                title = "ように",
                                                sourceType = "VOCABULARY",
                                                sourceId = 7L,
                                                chunkIndex = 0,
                                                level = null,
                                                score = 0.8,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                onOpenSource = { opened = it },
            )
        }

        composeRule.onNodeWithTag("ai-chat-source").assertDoesNotExist()
        assertNull(opened)
    }

    @Test
    fun tutorReply_rendersMarkdownWithoutLiteralAsterisks() {
        composeRule.setContent {
            TestChatContent(
                state =
                    AiChatUiState(
                        isLoading = false,
                        messages = listOf(ChatMessage("ASSISTANT", "**Corrected:** 病気だったので、行きませんでした。")),
                    ),
            )
        }

        composeRule.onNodeWithText("Corrected: 病気だったので、行きませんでした。").assertIsDisplayed()
        composeRule.onNodeWithText("**Corrected:** 病気だったので、行きませんでした。").assertDoesNotExist()
    }

    @Test
    fun offlineState_displaysCachedContentBanner() {
        composeRule.setContent {
            TestChatContent(
                state =
                    AiChatUiState(
                        isLoading = false,
                        isOffline = true,
                        messages = listOf(ChatMessage("ASSISTANT", "Cached answer")),
                    ),
            )
        }

        composeRule.onNodeWithText("You're offline", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Cached answer").assertIsDisplayed()
    }

    @Test
    fun loadingHistory_disablesSending() {
        var sendCount = 0

        composeRule.setContent {
            TestChatContent(
                state = AiChatUiState(isLoading = true, input = "too early"),
                onSend = { sendCount++ },
            )
        }

        composeRule.onNodeWithTag("ai-chat-send").assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(0, sendCount) }
    }

    @Test
    fun duplicateMessages_renderWithoutDuplicateLazyKeys() {
        composeRule.setContent {
            TestChatContent(
                state =
                    AiChatUiState(
                        isLoading = false,
                        messages =
                            listOf(
                                ChatMessage("USER", "same reply"),
                                ChatMessage("USER", "same reply"),
                            ),
                    ),
            )
        }

        composeRule.onAllNodesWithText("same reply").assertCountEquals(2)
    }

    @Composable
    private fun TestChatContent(
        state: AiChatUiState,
        onInputChanged: (String) -> Unit = {},
        onSend: () -> Unit = {},
        onRetry: () -> Unit = {},
        onScorePractice: () -> Unit = {},
        onOpenSource: (AiSourceDto) -> Unit = {},
        onStop: () -> Unit = {},
    ) {
        LinguaAiTheme(darkTheme = false) {
            AiChatContent(
                state = state,
                onInputChanged = onInputChanged,
                onSend = onSend,
                onRetry = onRetry,
                onScorePractice = onScorePractice,
                onStop = onStop,
                onOpenSource = onOpenSource,
                onBack = {},
            )
        }
    }
}
