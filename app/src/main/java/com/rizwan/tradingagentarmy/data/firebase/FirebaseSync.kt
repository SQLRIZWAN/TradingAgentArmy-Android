package com.rizwan.tradingagentarmy.data.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import okhttp3.MediaType.Companion.toMediaType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.rizwan.tradingagentarmy.data.local.BotEntity
import com.rizwan.tradingagentarmy.data.local.ChatMessageEntity
import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.data.local.TradeEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fire-and-forget bridge to the sqlrrr Firebase project (Firestore + FCM).
 * Everything is guarded: when google-services.json is not present the app
 * runs 100% on Room and no exception ever escapes.
 */
@Singleton
class FirebaseSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SecurePreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val available: Boolean
        get() = runCatching { FirebaseApp.getInstance() }.isSuccess

    fun ensureSignedIn(onResult: (String) -> Unit = {}) {
        if (!available) return
        scope.launch {
            runCatching {
                val auth = FirebaseAuth.getInstance()
                if (auth.currentUser == null) auth.signInAnonymously().await()
                onResult(auth.currentUser?.uid ?: "anon")
            }.onFailure {
                Log.w(TAG, "anon sign-in failed: ${it.message}")
                onResult("error")
            }
        }
    }

    fun pushChat(entity: ChatMessageEntity) = guarded("chat") {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@guarded
        firestore().collection("users").document(uid)
            .collection("chat_history").document(entity.id.toString())
            .set(
                mapOf(
                    "role" to entity.role,
                    "content" to entity.content,
                    "model" to (entity.model ?: ""),
                    "timestamp" to entity.timestamp
                )
            ).await()
    }

    fun pushTrade(entity: TradeEntity) = guarded("trade") {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@guarded
        firestore().collection("users").document(uid)
            .collection("trading_history").document("${entity.timestamp}_${entity.id}")
            .set(
                mapOf(
                    "symbol" to entity.symbol,
                    "side" to entity.side,
                    "entry" to entity.entry,
                    "exit" to (entity.exit ?: 0.0),
                    "pnl" to entity.pnl,
                    "mode" to entity.mode,
                    "botName" to entity.botName,
                    "model" to (entity.model ?: ""),
                    "timestamp" to entity.timestamp
                )
            ).await()
        // shared collection for army-wide self-improvement dataset
        firestore().collection("trading_history")
            .document("u${uid}_${entity.timestamp}_${entity.id}")
            .set(
                mapOf(
                    "uid" to uid, "symbol" to entity.symbol, "side" to entity.side,
                    "entry" to entity.entry, "exit" to (entity.exit ?: 0.0),
                    "pnl" to entity.pnl, "mode" to entity.mode,
                    "strategy" to entity.botName, "timestamp" to entity.timestamp
                )
            ).await()
    }

    fun pushBot(entity: BotEntity) = guarded("bot") {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@guarded
        firestore().collection("users").document(uid)
            .collection("bots").document(entity.id)
            .set(
                mapOf(
                    "name" to entity.name, "market" to entity.market,
                    "strategy" to entity.strategy, "status" to entity.status,
                    "pnlToday" to entity.pnlToday, "gate1" to entity.gate1,
                    "gate2" to entity.gate2, "gate3" to entity.gate3,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
    }

    fun currentFcmToken(onToken: (String) -> Unit) {
        if (!available) return
        scope.launch {
            runCatching {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { onToken(it) }
            }
        }
    }

    fun registerFcmWithBackend() {
        currentFcmToken { token ->
            if (token.isBlank() || prefs.backendUrl.isBlank()) return@currentFcmToken
            scope.launch {
                runCatching {
                    val base = prefs.backendUrl.trimEnd('/')
                    val body = okhttp3.RequestBody.create(
                        "application/json".toMediaType(),
                        """{"token":"$token","platform":"android"}"""
                    )
                    val req = okhttp3.Request.Builder()
                        .url("$base/api/notifications/token")
                        .post(body).build()
                    okhttp3.OkHttpClient().newCall(req).execute().close()
                }
            }
        }
    }

    private fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    private fun guarded(tag: String, block: suspend () -> Unit) {
        if (!available) return
        scope.launch {
            runCatching { block() }.onFailure { Log.d(TAG, "sync[$tag] skipped: ${it.message}") }
        }
    }

    companion object {
        private const val TAG = "FirebaseSync"
    }
}
