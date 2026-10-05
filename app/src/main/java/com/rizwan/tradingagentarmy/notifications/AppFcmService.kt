package com.rizwan.tradingagentarmy.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AppFcmService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "AI Trading Army"
        val body = data["body"] ?: message.notification?.body ?: ""
        when (data["channel"]) {
            "bot_health" -> Notifier.botCrash(this, body)
            "daily_summary" -> Notifier.dailySummary(this, body)
            "circuit_breaker" -> Notifier.circuitBreaker(this, body)
            else -> Notifier.trade(this, body.ifBlank { title })
        }
    }

    override fun onNewToken(token: String) {
        // token is registered to the backend by FirebaseSync.registerFcmWithBackend()
        runCatching {
            com.rizwan.tradingagentarmy.data.firebase.FirebaseSyncHolder.sync?.registerFcmWithBackend()
        }
    }
}
