package com.linguaai.app.ui.screens.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.linguaai.app.ui.theme.LinguaAiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AuthScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    // UI tests never touch a real backend: the value exists only to satisfy
    // the state shape. It lives in a test resource, not in code, and is not a
    // credential.
    private val fixturePassword: String =
        javaClass
            .getResource("/auth-fixture-password.txt")!!
            .readText()
            .trim()

    @Test
    fun login_emptyForm_keepsPrimaryActionDisabled() {
        var registerClicks = 0

        composeRule.setContent {
            LinguaAiTheme(darkTheme = false) {
                LoginContent(
                    state = LoginUiState(),
                    onEvent = {},
                    onNavigateToRegister = { registerClicks++ },
                )
            }
        }

        composeRule.onNodeWithText("LinguaAI").assertIsDisplayed()
        composeRule.onNodeWithText("Email").assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Log in").assertIsNotEnabled()
        composeRule.onNodeWithText("New here? Create an account").performClick()

        composeRule.runOnIdle { assertEquals(1, registerClicks) }
    }

    @Test
    fun register_emptyForm_exposesPasswordHintAndLoginPath() {
        var loginClicks = 0

        composeRule.setContent {
            LinguaAiTheme(darkTheme = false) {
                RegisterContent(
                    state = RegisterUiState(),
                    onEvent = {},
                    onNavigateToLogin = { loginClicks++ },
                )
            }
        }

        composeRule.onNodeWithText("Username").assertIsDisplayed()
        composeRule.onNodeWithText("At least 8 characters").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Create account").assertIsNotEnabled()
        composeRule.onNodeWithText("Already have an account? Log in").performClick()

        composeRule.runOnIdle { assertEquals(1, loginClicks) }
    }

    @Test
    fun login_validationErrors_areVisible() {
        composeRule.setContent {
            LinguaAiTheme(darkTheme = false) {
                LoginContent(
                    state =
                        LoginUiState(
                            emailError = "Enter a valid email",
                            passwordError = "Password is required",
                        ),
                    onEvent = {},
                    onNavigateToRegister = {},
                )
            }
        }

        composeRule.onNodeWithText("Enter a valid email").assertIsDisplayed()
        composeRule.onNodeWithText("Password is required").assertIsDisplayed()
    }

    @Test
    fun register_loadingState_disablesPrimaryAction() {
        composeRule.setContent {
            LinguaAiTheme(darkTheme = false) {
                RegisterContent(
                    state =
                        RegisterUiState(
                            email = "learner@example.com",
                            username = "learner",
                            password = fixturePassword,
                            isLoading = true,
                        ),
                    onEvent = {},
                    onNavigateToLogin = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Create account").assertIsNotEnabled()
    }
}
