package com.linguaai.app.data.session

import android.content.Context
import com.linguaai.app.data.local.dao.AiMessageCacheDao
import com.linguaai.app.data.local.dao.ProgressCacheDao
import com.linguaai.app.data.local.dao.SyncDao
import com.linguaai.app.domain.repository.AuthRepository
import com.linguaai.app.work.WorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Single owner of the sign-out sequence.
 *
 * Signing out is a privacy boundary, not just a navigation change: leaving
 * cached conversation content, cached progress, or queued learning events on the
 * device means the next user of the device can read the previous user's data.
 * Every one of those stores is wiped here so the boundary is enforced in one
 * place rather than at each call site.
 *
 * Order matters: the server logout needs the refresh token, so it runs before
 * the local wipe. A failed logout (offline, expired token) must still wipe
 * locally — the device is the thing we control.
 */
@Singleton
class SignOutCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val progressCacheDao: ProgressCacheDao,
    private val aiMessageCacheDao: AiMessageCacheDao,
    private val syncDao: SyncDao,
) {

    suspend fun signOut() {
        runCatching { authRepository.logout() }
            .onFailure { Timber.w(it, "Server logout failed; clearing locally anyway") }

        WorkScheduler.cancelAll(context)

        // User-scoped local data. Order is irrelevant but all of it must go.
        progressCacheDao.clear()
        aiMessageCacheDao.clearAll()
        syncDao.clearAll()
    }
}
