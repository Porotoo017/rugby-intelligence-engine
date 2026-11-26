package com.rugbypro.app.live

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rugbypro.app.data.repository.RugbyRepositoryImpl
import com.rugbypro.app.domain.usecase.DetectKeyLiveEventsUseCase
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class RugbyLiveSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val repo = RugbyRepositoryImpl(applicationContext)
            val cfg = repo.observeConfig().first()
            val prev = repo.loadInitialDashboard()
            val latest = repo.syncWithCascade(forceLiveRefresh = true)
            val alerts = DetectKeyLiveEventsUseCase.execute(
                previous = prev.matches,
                current = latest.matches,
                notifyTries = cfg.notifyTries,
                notifyRedCards = cfg.notifyRedCards,
                notifyFullTime = cfg.notifyFullTime
            )
            for (alert in alerts) {
                if (!repo.hasNotifiedEvent(alert.uniqueKey)) {
                    RugbyNotificationManager.postAlert(applicationContext, alert)
                    repo.markEventNotified(alert.uniqueKey)
                }
            }
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "rugby_live_background_sync"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = PeriodicWorkRequestBuilder<RugbyLiveSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }
}
