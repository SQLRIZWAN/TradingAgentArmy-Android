package com.rizwan.tradingagentarmy.data.remote

import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.domain.model.WsEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketManager @Inject constructor(private val prefs: SecurePreferences) {

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: WebSocket? = null
    private var job: Job? = null
    private var attempt = 0

    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun connect() {
        if (prefs.wsUrl.isBlank()) return
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                runCatching { dial() }
                // exponential backoff capped at 30s
                val wait = (2000L * (1 shl attempt.coerceAtMost(4))).coerceAtMost(30_000L)
                attempt = (attempt + 1).coerceAtMost(5)
                delay(wait)
            }
        }
    }

    fun disconnect() {
        job?.cancel()
        socket?.close(1000, "user")
        socket = null
        _connected.value = false
    }

    private fun dial() {
        val url = prefs.wsUrl
        if (url.isBlank()) return
        val req = Request.Builder().url(url).apply {
            if (prefs.bearerToken.isNotBlank()) header("Authorization", "Bearer ${prefs.bearerToken}")
        }.build()
        val client = OkHttpClient.Builder().pingInterval(20, java.util.concurrent.TimeUnit.SECONDS).build()
        val latch = java.util.concurrent.CountDownLatch(1)
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                attempt = 0
                _connected.value = true
                _events.tryEmit(WsEvent.Connected)
                latch.countDown()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parse(text)?.let { _events.tryEmit(it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connected.value = false
                _events.tryEmit(WsEvent.Disconnected)
                latch.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connected.value = false
                _events.tryEmit(WsEvent.Disconnected)
                latch.countDown()
            }
        })
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun parse(text: String): WsEvent? = runCatching {
        val obj = json.parseToJsonElement(text).jsonObject
        when (obj["type"]?.jsonPrimitive?.content) {
            "price" -> WsEvent.Price(
                obj["symbol"]!!.jsonPrimitive.content,
                obj["price"]!!.jsonPrimitive.content.toDoubleOrNull() ?: 0.0,
                obj["change"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            )
            "trade" -> WsEvent.Trade(obj["payload"]?.jsonPrimitive?.content ?: text)
            "alert" -> WsEvent.Alert(
                obj["title"]?.jsonPrimitive?.content ?: "Alert",
                obj["body"]?.jsonPrimitive?.content ?: "",
                obj["critical"]?.jsonPrimitive?.content?.toBoolean() ?: false
            )
            "bot_status" -> WsEvent.BotStatus(
                obj["botId"]!!.jsonPrimitive.content,
                obj["status"]!!.jsonPrimitive.content
            )
            "circuit_breaker" -> WsEvent.CircuitBreaker(
                obj["active"]?.jsonPrimitive?.booleanOrNull ?: false,
                obj["resetIn"]?.jsonPrimitive?.content ?: ""
            )
            "model" -> WsEvent.ModelChanged(obj["model"]!!.jsonPrimitive.content)
            else -> null
        }
    }.getOrNull()
}
