package com.rizwan.tradingagentarmy.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rizwan.tradingagentarmy.MainActivity
import com.rizwan.tradingagentarmy.R

object Notifier {

    fun requestPermissionIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Activity will request on first launch; channels are ready either way.
        }
    }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE
        )

    fun post(
        context: Context,
        channel: String,
        title: String,
        body: String,
        high: Boolean = false,
        id: Int = System.currentTimeMillis().toInt()
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notifier)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending(context))
            .setPriority(if (high) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    fun trade(context: Context, text: String) =
        post(context, "trade_alerts", "🟢 Trade Executed", text, high = true)

    fun botCrash(context: Context, text: String) =
        post(context, "bot_health", "🔴 Bot Alert", text)

    fun dailySummary(context: Context, text: String) =
        post(context, "daily_summary", "📊 Daily Summary", text)

    fun circuitBreaker(context: Context, text: String) =
        post(context, "trade_alerts", "🚨 Circuit Breaker", text, high = true)
}
