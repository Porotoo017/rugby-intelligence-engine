package com.rugbypro.app.live

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil.Coil
import coil.request.ImageRequest
import com.rugbypro.app.data.repository.RugbyRepositoryImpl
import com.rugbypro.app.domain.model.RugbyDashboard
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
            val prematchId = inputData.getString(KEY_PREMATCH_ID).orEmpty()
            val preloadKey = if (prematchId.isNotBlank()) "prematch_$prematchId" else ""
            if (preloadKey.isNotBlank() && repo.hasNotifiedEvent(preloadKey)) {
                return Result.success()
            }

            val cfg = repo.observeConfig().first()
            val prev = repo.loadInitialDashboard()
            val latest = repo.syncWithCascade(forceLiveRefresh = true, forcePreMatchId = prematchId.takeIf { it.isNotBlank() })

            if (prematchId.isNotBlank()) {
                prefetchMatchMedia(applicationContext, latest, prematchId)
                repo.markEventNotified(preloadKey)
            }

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

    private fun prefetchMatchMedia(context: Context, dash: RugbyDashboard, matchId: String) {
        val targetMatch = dash.matches.firstOrNull { it.id == matchId } ?: return
        val loader = Coil.imageLoader(context)
        val urls = linkedSetOf<String>()
        listOf(
            targetMatch.homeCrestUrl,
            targetMatch.homeFlagUrl,
            targetMatch.awayCrestUrl,
            targetMatch.awayFlagUrl
        ).filter { it.isNotBlank() }.forEach { urls.add(it) }

        val matchTeamNames = listOf(targetMatch.homeName.lowercase(), targetMatch.awayName.lowercase())
        dash.teams.filter { t ->
            val tn = t.name.lowercase()
            matchTeamNames.any { tn.contains(it) || it.contains(tn) }
        }.forEach { team ->
            if (team.crestUrl.isNotBlank()) urls.add(team.crestUrl)
            if (team.flagUrl.isNotBlank()) urls.add(team.flagUrl)
            team.roster.take(30).forEach { player ->
                player.photoUrl?.takeIf { it.isNotBlank() }?.let { urls.add(it) }
                player.nationalityFlagUrl?.takeIf { it.isNotBlank() }?.let { urls.add(it) }
            }
        }
        (targetMatch.homeLineup + targetMatch.awayLineup).forEach { player ->
            player.photoUrl?.takeIf { it.isNotBlank() }?.let { urls.add(it) }
            player.nationalityFlagUrl?.takeIf { it.isNotBlank() }?.let { urls.add(it) }
        }

        urls.take(65).forEach { url ->
            val req = ImageRequest.Builder(context)
                .data(url)
                .build()
            loader.enqueue(req)
        }
    }

    companion object {
        private const val WORK_NAME = "rugby_live_background_sync"
        private const val PRELOAD_WORK_PREFIX = "rugby_prematch_preload_"
        const val KEY_PREMATCH_ID = "key_prematch_id"

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

        fun enqueueImmediatePreload(context: Context, matchId: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val data = Data.Builder()
                .putString(KEY_PREMATCH_ID, matchId)
                .build()
            val req = OneTimeWorkRequestBuilder<RugbyLiveSyncWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            val unique = PRELOAD_WORK_PREFIX + (matchId.ifBlank { "general" })
            WorkManager.getInstance(context).enqueueUniqueWork(
                unique,
                ExistingWorkPolicy.KEEP,
                req
            )
        }
    }
}
