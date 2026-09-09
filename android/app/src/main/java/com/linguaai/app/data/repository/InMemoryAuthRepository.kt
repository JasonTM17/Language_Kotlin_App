package com.linguaai.app.data.repository

import com.linguaai.app.domain.model.AppError
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.model.User
import com.linguaai.app.domain.repository.AuthRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * TEMPORARY in-memory authentication store so the app builds and navigates
 * before the backend wiring lands; replaced by the remote-backed repository
 * in the auth networking phase.
 */
@Singleton
class InMemoryAuthRepository @Inject constructor() : AuthRepository {

    private data class Account(val username: String, val password: String)

    private val accounts = ConcurrentHashMap<String, Account>()
    private val idSequence = AtomicLong(0)
    private val currentUser = MutableStateFlow<User?>(null)

    override suspend fun register(
        email: String,
        username: String,
        password: String,
    ): AppResult<User> {
        if (accounts.containsKey(email)) {
            return AppResult.Failure(AppError.Conflict)
        }
        accounts[email] = Account(username = username, password = password)
        val user = User(id = idSequence.incrementAndGet(), email = email, username = username)
        currentUser.value = user
        return AppResult.Success(user)
    }

    override suspend fun login(email: String, password: String): AppResult<User> {
        val account = accounts[email] ?: return AppResult.Failure(AppError.Unauthorized)
        if (account.password != password) {
            return AppResult.Failure(AppError.Unauthorized)
        }
        val user = User(id = idSequence.incrementAndGet(), email = email, username = account.username)
        currentUser.value = user
        return AppResult.Success(user)
    }

    override suspend fun logout() {
        currentUser.value = null
    }

    override fun observeCurrentUser(): Flow<User?> = currentUser.asStateFlow()
}
