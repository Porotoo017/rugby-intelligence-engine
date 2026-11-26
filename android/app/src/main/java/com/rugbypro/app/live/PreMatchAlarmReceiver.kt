package com.rugbypro.app.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PreMatchAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val matchId = intent?.getStringExtra(PreMatchAlarmScheduler.EXTRA_MATCH_ID).orEmpty()
        RugbyLiveSyncWorker.enqueueImmediatePreload(context.applicationContext, matchId)
    }
}
