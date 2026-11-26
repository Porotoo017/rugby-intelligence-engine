package com.rugbypro.app.live

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rugbypro.app.MainActivity
import com.rugbypro.app.R
import com.rugbypro.app.domain.model.LiveNotificationAlert

object RugbyNotificationManager {
    private const val CH_TRY = "rugby_tries"
    private const val CH_RED = "rugby_discipline"
    private const val CH_FT = "rugby_fulltime"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_TRY, "Tries en vivo", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alertas inmediatas de tries en partidos en vivo"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RED, "Tarjetas rojas", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alertas de expulsiones durante los partidos"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_FT, "Final del partido", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Resumen final de marcadores verificados"
            }
        )
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun postAlert(context: Context, alert: LiveNotificationAlert) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)
        val channelId = when (alert.channelType) {
            "TRY" -> CH_TRY
            "RED" -> CH_RED
            else -> CH_FT
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            alert.uniqueKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_monochrome_img)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(context).notify(alert.uniqueKey.hashCode(), notif)
    }
}
