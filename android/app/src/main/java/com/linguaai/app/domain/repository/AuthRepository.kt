package com.linguaai.app.domain.repository

import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.model.User
import kotlinx.coroutines.flow.Flow

/** Contract for authentication operations; implemented by the data layer. */
interface AuthRepository {
    suspend fun register(email: String, username: String, password: String): AppResult<User>
    suspend fun login(email: String, password: String): AppResult<User>
    suspend fun logout()
    /** Emits the currently authenticated user, or null when signed out. */
    fun observeCurrentUser(): Flow<User?>
}
