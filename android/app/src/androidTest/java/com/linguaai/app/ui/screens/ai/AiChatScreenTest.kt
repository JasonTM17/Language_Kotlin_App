package com.linguaai.app.ui.screens.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.linguaai.app.data.remote.dto.PracticeScoreDto
import com.linguaai.app.ui.theme.LinguaAiTheme
import org.junit.Assert.assertEquals
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
                state = AiChatUiState(isLoading = false, error = "Provider timed out"),
                onRetry = { retryCount++ },
            )
        }

        composeRule.onNodeWithText("Provider timed out").assertIsDisplayed()
        composeRule.onNodeWithTag("ai-chat-retry").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertEquals(1, retryCount) }
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
    ) {
        LinguaAiTheme(darkTheme = false) {
            AiChatContent(
                state = state,
                onInputChanged = onInputChanged,
                onSend = onSend,
                onRetry = onRetry,
                onScorePractice = onScorePractice,
                onBack = {},
            )
        }
    }
}
