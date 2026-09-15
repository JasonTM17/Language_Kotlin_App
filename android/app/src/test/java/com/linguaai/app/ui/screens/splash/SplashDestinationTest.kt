package com.linguaai.app.ui.screens.splash

import com.linguaai.app.data.repository.ProfileData
import com.linguaai.app.domain.model.AppError
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Test

class SplashDestinationTest {
    @Test
    fun onboardedProfileLandsOnHome() {
        assertEquals(StartDestination.HOME, startDestinationFor(AppResult.Success(profile(onboarded = true))))
    }

    @Test
    fun newProfileLandsOnOnboarding() {
        assertEquals(StartDestination.ONBOARDING, startDestinationFor(AppResult.Success(profile(onboarded = false))))
    }

    @Test
    fun failedSessionLandsOnLogin() {
        assertEquals(StartDestination.LOGIN, startDestinationFor(AppResult.Failure(AppError.Unauthorized)))
    }

    private fun profile(onboarded: Boolean) =
        ProfileData(
            user = User(id = 1, email = "test@example.com", username = "Test"),
            languageId = null,
            level = null,
            goal = null,
            dailyGoalMinutes = 20,
            onboarded = onboarded,
        )
}
