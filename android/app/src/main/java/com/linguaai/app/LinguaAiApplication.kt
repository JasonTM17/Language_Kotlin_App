package com.linguaai.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.linguaai.app.work.NotificationChannels
import com.linguaai.app.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class LinguaAiApplication : Application(), Configuration.Provider {

    /**
     * Supplied to WorkManager so `@HiltWorker` classes can be constructed with
     * injected dependencies. Requires the default WorkManager initializer to be
     * removed in the manifest — see the provider block there.
     */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        NotificationChannels.create(this)

        // Drain anything left in the outbox from a previous session. The unique
        // work name coalesces, so calling this on every start is safe.
        WorkScheduler.enqueueSync(this)
    }
}
