package com.linguaai.app.domain.usecase

import com.linguaai.app.domain.model.AppError
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.model.User
import com.linguaai.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Login and registration use cases.
 *
 * The behaviour worth pinning is not "validation runs" but the contract that
 * follows from it: an invalid form must never reach the network, and the values
 * that do reach it must be normalised, because the server keys accounts on the
 * normalised form.
 */
class AuthUseCasesTest {
    private val fake = FakeAuthRepository()

    // ---- LoginUseCase ----

    @Test
    fun `login with an invalid email fails without calling the repository`() =
        runTest {
            val result = LoginUseCase(fake)("not-an-email", "password123")

            assertTrue(result is AppResult.Failure)
            assertEquals(
                AppError.Validation(field = "email", reason = "Enter a valid email address"),
                (result as AppResult.Failure).error,
            )
            // The point of validating first: no wasted round trip.
            assertEquals(0, fake.loginCalls)
        }

    @Test
    fun `login with a short password fails without calling the repository`() =
        runTest {
            val result = LoginUseCase(fake)("son@example.com", "short")

            assertTrue(result is AppResult.Failure)
            assertEquals("password", ((result as AppResult.Failure).error as AppError.Validation).field)
            assertEquals(0, fake.loginCalls)
        }

    @Test
    fun `login normalises the email before calling the repository`() =
        runTest {
            LoginUseCase(fake)("  Son@Example.COM  ", "password123")

            assertEquals(1, fake.loginCalls)
            assertEquals("son@example.com", fake.lastLoginEmail)
            // The password must be passed through untouched.
            assertEquals("password123", fake.lastLoginPassword)
        }

    @Test
    fun `login propagates a repository failure unchanged`() =
        runTest {
            fake.loginResult = AppResult.Failure(AppError.Unauthorized)

            val result = LoginUseCase(fake)("son@example.com", "password123")

            assertTrue(result is AppResult.Failure)
            assertEquals(AppError.Unauthorized, (result as AppResult.Failure).error)
        }

    // ---- RegisterUseCase ----

    @Test
    fun `register with a too-short username fails without calling the repository`() =
        runTest {
            val result = RegisterUseCase(fake)("son@example.com", "ab", "password123")

            assertTrue(result is AppResult.Failure)
            assertEquals("username", ((result as AppResult.Failure).error as AppError.Validation).field)
            assertEquals(0, fake.registerCalls)
        }

    @Test
    fun `register rejects a username that is too short once trimmed`() =
        runTest {
            // " a " is three characters but one of content, and the repository would
            // receive the trimmed value.
            val result = RegisterUseCase(fake)("son@example.com", " a ", "password123")

            assertTrue(result is AppResult.Failure)
            assertEquals(0, fake.registerCalls)
        }

    @Test
    fun `register normalises email and username but not the password`() =
        runTest {
            RegisterUseCase(fake)("  Son@Example.COM ", "  Son  ", "Pass word 123")

            assertEquals(1, fake.registerCalls)
            assertEquals("son@example.com", fake.lastRegisterEmail)
            assertEquals("Son", fake.lastRegisterUsername)
            assertEquals("Pass word 123", fake.lastRegisterPassword)
        }

    @Test
    fun `register reports the first invalid field when several are invalid`() =
        runTest {
            // Field order is a user-facing decision: email is checked first so the
            // form highlights the topmost problem.
            val result = RegisterUseCase(fake)("bad", "ab", "short")

            assertEquals("email", ((result as AppResult.Failure).error as AppError.Validation).field)
            assertEquals(0, fake.registerCalls)
        }

    private class FakeAuthRepository : AuthRepository {
        var loginCalls = 0
        var registerCalls = 0
        var lastLoginEmail: String? = null
        var lastLoginPassword: String? = null
        var lastRegisterEmail: String? = null
        var lastRegisterUsername: String? = null
        var lastRegisterPassword: String? = null

        var loginResult: AppResult<User> = AppResult.Success(User(1, "son@example.com", "Son"))
        var registerResult: AppResult<User> = AppResult.Success(User(1, "son@example.com", "Son"))

        override suspend fun register(
            email: String,
            username: String,
            password: String,
        ): AppResult<User> {
            registerCalls++
            lastRegisterEmail = email
            lastRegisterUsername = username
            lastRegisterPassword = password
            return registerResult
        }

        override suspend fun login(
            email: String,
            password: String,
        ): AppResult<User> {
            loginCalls++
            lastLoginEmail = email
            lastLoginPassword = password
            return loginResult
        }

        override suspend fun logout() = Unit

        override fun observeCurrentUser(): Flow<User?> = MutableStateFlow(null)
    }
}
