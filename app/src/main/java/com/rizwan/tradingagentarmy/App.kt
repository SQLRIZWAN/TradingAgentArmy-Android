package com.rizwan.tradingagentarmy

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rizwan.tradingagentarmy.data.firebase.FirebaseSync
import com.rizwan.tradingagentarmy.data.firebase.FirebaseSyncHolder
import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.data.remote.WebSocketManager
import com.rizwan.tradingagentarmy.notifications.DailySummaryWorker
import com.rizwan.tradingagentarmy.notifications.Notifier
import dagger.hilt.android.HiltAndroidApp
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var firebaseSync: FirebaseSync
    @Inject lateinit var prefs: SecurePreferences
    @Inject lateinit var ws: WebSocketManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.rizwan.tradingagentarmy.ui.theme.ThemeController.applyTheme(prefs.themeMode)
        createChannels()
        FirebaseSyncHolder.sync = firebaseSync
        firebaseSync.ensureSignedIn()
        firebaseSync.registerFcmWithBackend()
        ws.connect()
        scheduleDailySummary()
    }

    fun scheduleDailySummary() {
        if (!prefs.notifyDaily) return
        runCatching {
            val now = LocalDateTime.now()
            var next = now.withHour(prefs.dailyHour).withMinute(0).withSecond(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMinutes().coerceAtLeast(1)
            val req = PeriodicWorkRequestBuilder<DailySummaryWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork("daily_summary", ExistingPeriodicWorkPolicy.REPLACE, req)
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannels(
                listOf(
                    NotificationChannel("trade_alerts", "Trade Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Executed trades and signals"
                        enableVibration(true)
                    },
                    NotificationChannel("bot_health", "Bot Health", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Bot crashes, restarts and gate updates"
                    },
                    NotificationChannel("agent_army", "Agent Army", NotificationManager.IMPORTANCE_HIGH),
                    NotificationChannel("daily_summary", "Daily Summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Daily P&L summary"
                    }
                )
            )
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
