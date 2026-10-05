package com.rizwan.tradingagentarmy.trading

import android.content.Context
import com.rizwan.tradingagentarmy.agents.AppEvents
import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.data.local.TradeDao
import com.rizwan.tradingagentarmy.data.local.TradeEntity
import com.rizwan.tradingagentarmy.notifications.Notifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HftEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SecurePreferences,
    private val tradeDao: TradeDao,
    private val risk: RiskGuard,
    private val bitget: BitgetClient
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active
    private val _lastSignal = MutableStateFlow("—")
    val lastSignal: StateFlow<String> = _lastSignal
    private val _position = MutableStateFlow("FLAT")
    val position: StateFlow<String> = _position

    private var posSide = ""
    private var posEntry = 0.0
    private var lastTradeAt = 0L

    fun start() {
        if (_active.value) return
        _active.value = true
        AppEvents.record("hft", "engine started")
        job = scope.launch {
            val symbol = prefs.getString("army_symbol", "BTCUSDT").uppercase()
            val closes = ArrayDeque<Double>()
            while (isActive) {
                runCatching {
                    val price = lastPrice(symbol)
                    if (price > 0) {
                        closes.addLast(price)
                        while (closes.size > 60) closes.removeFirst()
                        if (closes.size >= 25) {
                            val e9 = ema(closes.toList(), 9)
                            val e21 = ema(closes.toList(), 21)
                            val prevE9 = ema(closes.toList().dropLast(1), 9)
                            val prevE21 = ema(closes.toList().dropLast(1), 21)
                            val rsi = rsi(closes.toList(), 14)
                            decide(symbol, price, prevE9, prevE21, e9, e21, rsi)
                        }
                    }
                }
                delay(2000L)
            }
        }
    }

    fun stop() {
        if (!_active.value) return
        job?.cancel()
        job = null
        _active.value = false
        AppEvents.record("hft", "engine stopped")
    }

    private suspend fun decide(
        symbol: String, price: Double,
        prevE9: Double, prevE21: Double, e9: Double, e21: Double, rsi: Double
    ) {
        val crossUp = prevE9 <= prevE21 && e9 > e21
        val crossDown = prevE9 >= prevE21 && e9 < e21
        val now = System.currentTimeMillis()

        if (posSide.isNotEmpty()) {
            val tp = posEntry * (if (posSide == "LONG") 1.0015 else 0.9985)
            val sl = posEntry * (if (posSide == "LONG") 0.9985 else 1.0015)
            val takeProfit = if (posSide == "LONG") price >= tp else price <= tp
            val stopLoss = if (posSide == "LONG") price <= sl else price >= sl
            val exitSignal = (posSide == "LONG" && crossDown) || (posSide == "SHORT" && crossUp)
            if (takeProfit || stopLoss || exitSignal) closePosition(price, if (takeProfit) "TP" else if (stopLoss) "SL" else "X")
            return
        }

        val long = crossUp && rsi < 70
        val short = crossDown && rsi > 30
        if (!long && !short) {
            _lastSignal.value = "WAIT · E9=${"%.1f".format(e9)} E21=${"%.1f".format(e21)} RSI=${"%.0f".format(rsi)}"
            return
        }
        if (now - lastTradeAt < 30_000) return
        val side = if (long) "LONG" else "SHORT"
        val size = risk.capSize(prefs.getString("hft_size", "10").toDoubleOrNull() ?: 10.0)
        val deny = risk.evaluate(size)
        if (deny != null) {
            _lastSignal.value = "BLOCKED: $deny"
            AppEvents.record("hft", "blocked: $deny")
            return
        }
        val live = prefs.getBool("live_trading", false)
        val marketType = prefs.getString("bitget_market_type", "SPOT").uppercase()
        if (live && !bitget.configured) {
            _lastSignal.value = "BLOCKED: Bitget keys missing"
            return
        }
        if (live && marketType == "SPOT" && side == "SHORT") {
            _lastSignal.value = "BLOCKED: spot HFT short is unsupported"
            return
        }
        val stop = if (side == "LONG") price * 0.9985 else price * 1.0015
        val take = if (side == "LONG") price * 1.0015 else price * 0.9985
        if (live) {
            val result = bitget.placeProtected(
                marketType, symbol, if (side == "LONG") "buy" else "sell", size, price,
                stop, take, "hft_${symbol}_${now}"
            )
            if (!result.ok || result.orderId.isBlank()) {
                _lastSignal.value = "BLOCKED: live order/protection failed"
                AppEvents.record("hft", "live entry failed: ${result.message}")
                return
            }
        }
        lastTradeAt = now
        posSide = side
        posEntry = price
        _position.value = "$side @ ${"%.2f".format(price)}"
        _lastSignal.value = "$side entry RSI=${"%.0f".format(rsi)}"
        AppEvents.record("hft", "$side $symbol @ $price size=$size")
    }

    private suspend fun closePosition(price: Double, reason: String) {
        val side = posSide
        val entry = posEntry
        val notional = prefs.getString("hft_size", "10").toDoubleOrNull() ?: 10.0
        val marketType = prefs.getString("bitget_market_type", "SPOT").uppercase()
        if (prefs.getBool("live_trading", false)) {
            val qty = notional / entry
            val result = if (marketType == "CFD") {
                bitget.closeCfd(prefs.getString("army_symbol", "XAUUSD"))
            } else if (marketType == "FUTURES") {
                bitget.closeFuturesMarket(prefs.getString("army_symbol", "BTCUSDT"), if (side == "LONG") "sell" else "buy", "%.8f".format(qty), "hft_exit_${System.currentTimeMillis()}")
            } else {
                bitget.closeSpotMarket(prefs.getString("army_symbol", "BTCUSDT"), "sell", qty, "hft_exit_${System.currentTimeMillis()}")
            }
            if (!result.ok) {
                AppEvents.record("hft", "live exit failed: ${result.message}")
                _lastSignal.value = "EXIT FAILED — manual check required"
                return
            }
        }
        val pnl = (if (side == "LONG") price - entry else entry - price) * notional / entry
        tradeDao.insert(
            TradeEntity(
                symbol = prefs.getString("army_symbol", "BTCUSDT"), side = side,
                entry = entry, exit = price, pnl = pnl, mode = "hft-paper",
                botName = "HFT Scalper", model = "ema-cross", timestamp = System.currentTimeMillis()
            )
        )
        AppEvents.record("hft", "closed $side @ $price ($reason) pnl=${"%.2f".format(pnl)}")
        Notifier.post(context, "agent_army", "⚡ HFT closed $side ($reason)",
            "PnL: ${if (pnl >= 0) "+" else ""}${"%.2f".format(pnl)} USD")
        posSide = ""
        posEntry = 0.0
        _position.value = "FLAT"
    }

    private fun lastPrice(symbol: String): Double = runCatching {
        val body = http.newCall(
            Request.Builder().url("https://api.binance.com/api/v3/ticker/price?symbol=${symbol.uppercase()}").build()
        ).execute().use { it.body?.string().orEmpty() }
        Regex("\"price\":\"([0-9.]+)\"").find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }.getOrDefault(0.0)

    private fun ema(vals: List<Double>, period: Int): Double {
        val k = 2.0 / (period + 1)
        var e = vals.take(period).average()
        for (v in vals.drop(period)) e = v * k + e * (1 - k)
        return e
    }

    private fun rsi(vals: List<Double>, period: Int): Double {
        if (vals.size <= period) return 50.0
        val recent = vals.takeLast(period + 1)
        var gain = 0.0; var loss = 0.0
        for (i in 1 until recent.size) {
            val d = recent[i] - recent[i - 1]
            if (d > 0) gain += d else loss -= d
        }
        if (loss == 0.0) return 100.0
        val rs = (gain / period) / (loss / period)
        return 100 - 100 / (1 + rs)
    }
}
