package com.rugbypro.app.live

import android.content.Context
import com.rugbypro.app.domain.model.AppConfig
import com.rugbypro.app.domain.model.MatchStatus
import com.rugbypro.app.domain.model.RugbyDashboard
import com.rugbypro.app.domain.repository.RugbyRepository
import com.rugbypro.app.domain.usecase.DetectKeyLiveEventsUseCase
import com.rugbypro.app.domain.usecase.PreMatchWindowUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LiveMatchTracker(
    private val context: Context,
    private val repository: RugbyRepository
) {
    private var pollingJob: Job? = null

    fun start(
        scope: CoroutineScope,
        getConfig: () -> AppConfig,
        getCurrentDashboard: () -> RugbyDashboard?,
        onDashboardUpdated: (RugbyDashboard) -> Unit
    ) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            var tick = 0
            while (isActive) {
                val curDash = getCurrentDashboard()
                val curMatches = curDash?.matches.orEmpty()
                val hasLiveOrPreMatch = curMatches.any {
                    it.status == MatchStatus.LIVE || PreMatchWindowUseCase.isInsideThirtyMinuteWindow(it)
                }
                val baseSec = getConfig().liveRefreshSec.coerceIn(15, 60)
                val intervalMs = if (hasLiveOrPreMatch) 5_000L else (baseSec * 1000L)
                delay(intervalMs)
                tick++
                runCatching {
                    val latestDash = getCurrentDashboard()
                    val prevMatches = latestDash?.matches.orEmpty()
                    val cfg = getConfig()
                    val nowMs = System.currentTimeMillis()
                    val preMatchNow = prevMatches.firstOrNull { PreMatchWindowUseCase.isInsideThirtyMinuteWindow(it, nowMs) }
                    val stillLiveOrPre = prevMatches.any {
                        it.status == MatchStatus.LIVE || PreMatchWindowUseCase.isInsideThirtyMinuteWindow(it, nowMs)
                    }
                    val runFullSync = !stillLiveOrPre || tick % 12 == 0
                    val nextDash = if (runFullSync) {
                        repository.syncWithCascade(
                            forceLiveRefresh = preMatchNow != null || tick % 24 == 0,
                            forcePreMatchId = preMatchNow?.id
                        )
                    } else {
                        repository.syncLivePulseOnly(latestDash)
                    }
                    val alerts = DetectKeyLiveEventsUseCase.execute(
                        previous = prevMatches,
                        current = nextDash.matches,
                        notifyTries = cfg.notifyTries,
                        notifyRedCards = cfg.notifyRedCards,
                        notifyFullTime = cfg.notifyFullTime
                    )
                    for (a in alerts) {
                        if (!repository.hasNotifiedEvent(a.uniqueKey)) {
                            RugbyNotificationManager.postAlert(context, a)
                            repository.markEventNotified(a.uniqueKey)
                        }
                    }
                    onDashboardUpdated(nextDash)
                }
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }
}
