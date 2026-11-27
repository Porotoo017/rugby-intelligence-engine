package com.rugbypro.app

import android.app.Application
import com.rugbypro.app.live.RugbyLiveSyncWorker
import com.rugbypro.app.live.RugbyNotificationManager

class RugbyProApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { RugbyNotificationManager.ensureChannels(this) }
        runCatching { RugbyLiveSyncWorker.schedulePeriodic(this) }
    }
}
