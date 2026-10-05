package com.rizwan.tradingagentarmy.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.data.remote.WebSocketManager
import com.rizwan.tradingagentarmy.data.repository.BotRepository
import com.rizwan.tradingagentarmy.data.repository.MarketRepository
import com.rizwan.tradingagentarmy.domain.model.MarketTicker
import com.rizwan.tradingagentarmy.domain.model.PortfolioSummary
import com.rizwan.tradingagentarmy.domain.model.Trade
import com.rizwan.tradingagentarmy.domain.model.WsEvent
import com.rizwan.tradingagentarmy.notifications.Notifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val marketRepo: MarketRepository,
    private val botRepo: BotRepository,
    private val prefs: SecurePreferences,
    private val ws: WebSocketManager
) : ViewModel() {

    private val _tickers = MutableStateFlow<List<MarketTicker>>(emptyList())
    val tickers: StateFlow<List<MarketTicker>> = _tickers.asStateFlow()

    private val _portfolio = MutableStateFlow<PortfolioSummary?>(null)
    val portfolio: StateFlow<PortfolioSummary?> = _portfolio.asStateFlow()

    private val _trades = MutableStateFlow<List<Trade>>(emptyList())
    val trades: StateFlow<List<Trade>> = _trades.asStateFlow()

    private val _circuit = MutableStateFlow<WsEvent.CircuitBreaker?>(null)
    val circuit: StateFlow<WsEvent.CircuitBreaker?> = _circuit.asStateFlow()

    private val _backendAlive = MutableStateFlow(false)
    val backendAlive: StateFlow<Boolean> = _backendAlive.asStateFlow()

    private val _wsConnected = ws.connected
    val wsConnected: StateFlow<Boolean> = _wsConnected

    private val _lastSync = MutableStateFlow(0L)
    val lastSync: StateFlow<Long> = _lastSync.asStateFlow()

    private val _selected = MutableStateFlow("BTC/USDT")
    val selected: StateFlow<String> = _selected.asStateFlow()

    private val _chartError = MutableStateFlow(false)
    val chartError: StateFlow<Boolean> = _chartError.asStateFlow()

    private var poller: Job? = null

    init {
        ws.connect()
        viewModelScope.launch {
            ws.events.collect { handle(it) }
        }
        startPolling()
    }

    fun selectSymbol(sym: String) {
        _selected.value = sym
        _chartError.value = false
    }

    fun onChartError() { _chartError.value = true }

    private fun startPolling() {
        poller?.cancel()
        poller = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay((prefs.refreshInterval.coerceIn(5, 60)) * 1000L)
            }
        }
    }

    private suspend fun refresh() {
        runCatching {
            val list = marketRepo.watchlist()
            if (list.isNotEmpty()) {
                _tickers.value = list.map { new ->
                    val old = _tickers.value.find { it.symbol == new.symbol }
                    if (old != null && new.change24h == 0.0) new.copy(change24h = old.change24h) else new
                }
            }
        }
        runCatching {
            val todayStart = java.time.LocalDate.now()
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val today = botRepo.pnlSince(todayStart)
            val total = botRepo.pnlSince(0)
            _portfolio.value = marketRepo.portfolio(today, total)
        }
        runCatching { _trades.value = botRepo.allTrades().take(10) }
        _backendAlive.value = marketRepo.backendAlive()
        _lastSync.value = System.currentTimeMillis()
    }

    private fun handle(event: WsEvent) {
        when (event) {
            is WsEvent.Price -> {
                val cur = _tickers.value
                val idx = cur.indexOfFirst { it.symbol == event.symbol }
                if (idx >= 0) {
                    val updated = cur.toMutableList()
                    updated[idx] = updated[idx].copy(price = event.price, change24h = event.change)
                    _tickers.value = updated
                } else {
                    _tickers.value = cur + MarketTicker(event.symbol, event.price, event.change)
                }
            }
            is WsEvent.Trade -> {
                runCatching {
                    val o = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                        .parseToJsonElement(event.payload).jsonObject
                    val sym = o["symbol"]?.jsonPrimitive?.content ?: return@runCatching
                    val side = o["side"]?.jsonPrimitive?.content ?: return@runCatching
                    val entry = o["entry"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@runCatching
                    val exit = o["exit"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    val pnl = o["pnl"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                    val mode = o["mode"]?.jsonPrimitive?.content ?: "LIVE"
                    val bot = o["botName"]?.jsonPrimitive?.content ?: "manual"
                    viewModelScope.launch {
                        botRepo.recordTrade(sym, side, entry, exit, pnl, mode, bot, null)
                        if (prefs.notifyTrades) {
                            Notifier.trade(
                                context,
                                "$side $sym @ $entry | P&L ${"%.2f".format(pnl)} | $mode"
                            )
                        }
                        refresh()
                    }
                }
            }
            is WsEvent.Alert -> {
                if (event.critical && prefs.notifyCircuit) Notifier.circuitBreaker(context, event.body)
                else Notifier.botCrash(context, "${event.title}: ${event.body}")
            }
            is WsEvent.CircuitBreaker -> {
                _circuit.value = event
                if (event.active && prefs.notifyCircuit) {
                    Notifier.circuitBreaker(context, "Daily loss limit reached. Resets in ${event.resetIn}.")
                }
            }
            else -> Unit
        }
    }

    fun chartUrl(): String = when (val s = _selected.value) {
        "BTC/USDT" -> tv("BINANCE:BTCUSDT")
        "ETH/USDT" -> tv("BINANCE:ETHUSDT")
        "SOL/USDT" -> tv("BINANCE:SOLUSDT")
        "XAUUSD" -> tv("TVC:GOLD")
        "EURUSD" -> tv("FX:EURUSD")
        "GBPUSD" -> tv("FX:GBPUSD")
        else -> tv("BINANCE:BTCUSDT")
    }

    private fun tv(symbol: String) =
        "https://s.tradingview.com/widgetembed/?symbol=" +
            java.net.URLEncoder.encode(symbol, "UTF-8") +
            "&interval=15&theme=dark&style=1&locale=en&hide_top_toolbar=0&saveimage=0&withdateranges=1"
}
