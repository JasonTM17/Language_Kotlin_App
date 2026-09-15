package com.linguaai.app.ui.screens.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.linguaai.app.data.remote.dto.LanguageDto
import com.linguaai.app.ui.theme.LinguaAiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun languageStep_selectionEnablesContinueAndMarksChoice() {
        var state by
            mutableStateOf(
                OnboardingUiState(
                    languages =
                        listOf(
                            LanguageDto(
                                id = 7L,
                                code = "ja",
                                name = "Japanese",
                                levels = listOf("N5", "N4"),
                            ),
                        ),
                ),
            )

        composeRule.setContent {
            LinguaAiTheme(darkTheme = false) {
                OnboardingContent(
                    state = state,
                    onAction = { action ->
                        if (action is OnboardingAction.LanguageSelected) {
                            state = state.copy(selectedLanguageId = action.id)
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Onboarding step 1 of 4").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Continue").assertIsNotEnabled()
        composeRule.onNodeWithText("Japanese").performClick()

        composeRule.onNodeWithContentDescription("Japanese selected").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Continue").assertIsEnabled()
    }

    @Test
    fun dailyStep_selectionEnablesStartLearning() {
        var state by mutableStateOf(OnboardingUiState(step = OnboardingStep.DAILY))

        composeRule.setContent {
            LinguaAiTheme(darkTheme = false) {
                OnboardingContent(
                    state = state,
                    onAction = { action ->
                        if (action is OnboardingAction.DailyGoalSelected) {
                            state = state.copy(selectedDailyGoal = action.minutes)
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithText("60 min").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Start learning").assertIsNotEnabled()
        composeRule.onNodeWithText("20 min").performClick()

        composeRule.onNodeWithContentDescription("Start learning").assertIsEnabled()
        composeRule.runOnIdle { assertEquals(20, state.selectedDailyGoal) }
    }
}
