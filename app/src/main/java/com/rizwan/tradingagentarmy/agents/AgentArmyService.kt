package com.rizwan.tradingagentarmy.agents

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rizwan.tradingagentarmy.R
import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.trading.HftEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AgentArmyService : Service() {

    @Inject lateinit var army: AgentArmy
    @Inject lateinit var prefs: SecurePreferences
    @Inject lateinit var hft: HftEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        const val CHANNEL = "agent_army"
        const val NOTIF_ID = 4242
        fun start(context: Context) =
            context.startForegroundService(Intent(context, AgentArmyService::class.java))
        fun stop(context: Context) = context.stopService(Intent(context, AgentArmyService::class.java))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Agent Army", NotificationManager.IMPORTANCE_LOW)
        )
        val notif = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notifier)
            .setContentTitle("Agent Army is ON")
            .setContentText("Agents are watching the market 24/7")
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
        loop()
    }

    private fun loop() {
        scope.launch {
            var first = true
            while (isActive) {
                if (!first) {
                    val mins = prefs.getInt("army_round_minutes", 15).coerceIn(2, 180)
                    delay(mins * 60_000L)
                }
                first = false
                if (prefs.getBool("agents_enabled", true)) {
                    runCatching { army.runRound() }
                }
                if (prefs.getBool("hft_enabled", false)) hft.start() else hft.stop()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
