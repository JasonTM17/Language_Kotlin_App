package com.linguaai.app.data.repository

import com.linguaai.app.data.datastore.SessionManager
import com.linguaai.app.data.remote.api.AuthApi
import com.linguaai.app.data.remote.dto.LoginRequestDto
import com.linguaai.app.data.remote.dto.LogoutRequestDto
import com.linguaai.app.data.remote.dto.ProfileDto
import com.linguaai.app.data.remote.dto.RefreshRequestDto
import com.linguaai.app.data.remote.dto.RegisterRequestDto
import com.linguaai.app.data.remote.dto.UpdateProfileRequestDto
import com.linguaai.app.data.remote.safeApiCall
import com.linguaai.app.domain.model.AppError
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.model.User
import com.linguaai.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot of the learner profile returned by the backend. */
data class ProfileData(
    val user: User,
    val languageId: Long?,
    val level: String?,
    val goal: String?,
    val dailyGoalMinutes: Int,
    val onboarded: Boolean,
) {
    constructor(dto: ProfileDto) : this(
        user =
            User(
                id = dto.user.id,
                email = dto.user.email,
                username = dto.user.username,
                avatarUrl = dto.user.avatarUrl,
            ),
        languageId = dto.languageId,
        level = dto.level,
        goal = dto.goal,
        dailyGoalMinutes = dto.dailyGoalMinutes,
        onboarded = dto.onboarded,
    )
}

/** Remote-backed authentication repository wired to the Ktor backend. */
@Singleton
class RemoteAuthRepository
    @Inject
    constructor(
        private val authApi: AuthApi,
        private val sessionManager: SessionManager,
    ) : AuthRepository {
        private val currentUser = MutableStateFlow<User?>(null)

        override suspend fun register(
            email: String,
            username: String,
            password: String,
        ): AppResult<User> {
            val result =
                safeApiCall {
                    authApi.register(RegisterRequestDto(email = email, username = username, password = password))
                }
            return when (result) {
                is AppResult.Success -> {
                    persistSession(result.data.user, result.data.tokens.accessToken, result.data.tokens.refreshToken)
                    AppResult.Success(currentUser.value!!)
                }
                is AppResult.Failure -> result
            }
        }

        override suspend fun login(
            email: String,
            password: String,
        ): AppResult<User> {
            val result =
                safeApiCall {
                    authApi.login(LoginRequestDto(email = email, password = password))
                }
            return when (result) {
                is AppResult.Success -> {
                    persistSession(result.data.user, result.data.tokens.accessToken, result.data.tokens.refreshToken)
                    AppResult.Success(currentUser.value!!)
                }
                is AppResult.Failure -> result
            }
        }

        override suspend fun logout() {
            sessionManager.refreshTokenSync()?.let { refreshToken ->
                // Best-effort server revocation; the local session clears regardless.
                safeApiCall { authApi.logout(LogoutRequestDto(refreshToken = refreshToken)) }
            }
            sessionManager.clear()
            currentUser.value = null
        }

        override fun observeCurrentUser(): Flow<User?> = currentUser.asStateFlow()

        /** Verifies the stored session on app start; clears it when stale. */
        suspend fun restoreSession(): AppResult<ProfileData> {
            val refreshToken =
                sessionManager.refreshTokenSync()
                    ?: return AppResult.Failure(AppError.Unauthorized)
            val refreshResult = safeApiCall { authApi.refresh(RefreshRequestDto(refreshToken = refreshToken)) }
            return when (refreshResult) {
                is AppResult.Success -> {
                    val tokens = refreshResult.data.tokens
                    sessionManager.saveTokens(tokens.accessToken, tokens.refreshToken)
                    when (val profile = fetchProfile()) {
                        is AppResult.Success -> AppResult.Success(profile.data)
                        is AppResult.Failure -> profile
                    }
                }
                is AppResult.Failure -> {
                    sessionManager.clear()
                    refreshResult
                }
            }
        }

        suspend fun updateProfile(request: UpdateProfileRequestDto): AppResult<ProfileData> = mapProfile { authApi.updateProfile(request) }

        suspend fun fetchProfile(): AppResult<ProfileData> = mapProfile { authApi.profile() }

        private suspend fun mapProfile(call: suspend () -> retrofit2.Response<ProfileDto>): AppResult<ProfileData> {
            val result = safeApiCall(call)
            return when (result) {
                is AppResult.Success -> AppResult.Success(ProfileData(result.data))
                is AppResult.Failure -> result
            }
        }

        private suspend fun persistSession(
            user: com.linguaai.app.data.remote.dto.UserDto,
            accessToken: String,
            refreshToken: String,
        ) {
            currentUser.value = User(id = user.id, email = user.email, username = user.username, avatarUrl = user.avatarUrl)
            sessionManager.saveTokens(accessToken, refreshToken)
        }
    }
