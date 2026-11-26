package com.rugbypro.app.live

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.rugbypro.app.domain.model.RugbyMatch
import com.rugbypro.app.domain.usecase.PreMatchWindowUseCase
import kotlin.math.abs

object PreMatchAlarmScheduler {
    const val EXTRA_MATCH_ID = "extra_prematch_id"

    fun scheduleUpcoming(context: Context, matches: List<RugbyMatch>) {
        val nowMs = System.currentTimeMillis()
        val targets = PreMatchWindowUseCase.computeTargets(matches, nowMs).take(12)
        if (targets.isEmpty()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        for (t in targets) {
            if (t.triggerAtMs <= nowMs + 15_000L) {
                RugbyLiveSyncWorker.enqueueImmediatePreload(context, t.matchId)
                continue
            }
            val intent = Intent(context, PreMatchAlarmReceiver::class.java).apply {
                action = "com.rugbypro.app.ACTION_PREMATCH_PRELOAD"
                putExtra(EXTRA_MATCH_ID, t.matchId)
            }
            val requestCode = abs(t.matchId.hashCode())
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            runCatching {
                val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.canScheduleExactAlarms()
                } else {
                    true
                }
                if (canExact) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.triggerAtMs, pi)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.triggerAtMs, pi)
                }
            }.onFailure {
                runCatching {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.triggerAtMs, pi)
                }
            }
        }
    }
}
