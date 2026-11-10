package com.scrollmeter.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.scrollmeter.app.MainActivity
import com.scrollmeter.app.R
import com.scrollmeter.app.utils.PermissionUtils

/**
 * Manages non-intrusive daily threshold alerts (50%, 75%, 90%, 100%).
 *
 * Strictly respects user autonomy: never forcibly closes applications, blocks navigation,
 * or overlays intrusive system windows over other apps.
 */
class ScrollMeterNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID_DAILY_LIMITS = "scrollmeter_daily_limits"
        private const val NOTIFICATION_ID_THRESHOLD = 4101
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val name = context.getString(R.string.notification_channel_name)
        val descriptionText = context.getString(R.string.notification_channel_desc)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID_DAILY_LIMITS, name, importance).apply {
            description = descriptionText
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.createNotificationChannel(channel)
    }

    /**
     * Posts a respectful system notification when a configured threshold (50%, 75%, 90%, 100%)
     * is reached, provided notifications are enabled in settings and Android runtime permission
     * is granted.
     */
    @Suppress("MissingPermission")
    fun sendThresholdAlertIfPermitted(
        thresholdPercent: Int,
        currentSwipes: Int,
        dailyLimit: Int,
        reasonTitle: String,
        reasonBody: String
    ) {
        if (!PermissionUtils.isNotificationPermissionGranted(context)) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_DAILY_LIMITS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(reasonTitle)
            .setContentText(reasonBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reasonBody))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setProgress(dailyLimit.coerceAtLeast(1), currentSwipes.coerceAtMost(dailyLimit), false)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_THRESHOLD + thresholdPercent,
                notification
            )
        }
    }
}
