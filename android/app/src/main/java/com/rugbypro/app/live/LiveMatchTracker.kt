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
                val curMatches = getCurrentDashboard()?.matches.orEmpty()
                val hasLiveOrPreMatch = curMatches.any {
                    it.status == MatchStatus.LIVE || PreMatchWindowUseCase.isInsideThirtyMinuteWindow(it)
                }
                val baseSec = getConfig().liveRefreshSec.coerceIn(15, 60)
                val intervalSec = if (hasLiveOrPreMatch) minOf(baseSec, 15) else baseSec
                delay(intervalSec * 1000L)
                tick++
                runCatching {
                    val prevMatches = getCurrentDashboard()?.matches.orEmpty()
                    val cfg = getConfig()
                    val nowMs = System.currentTimeMillis()
                    val preMatchNow = prevMatches.firstOrNull { PreMatchWindowUseCase.isInsideThirtyMinuteWindow(it, nowMs) }
                    val nextDash = repository.syncWithCascade(
                        forceLiveRefresh = preMatchNow != null || tick % 6 == 0,
                        forcePreMatchId = preMatchNow?.id
                    )
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
