package com.rizwan.tradingagentarmy.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizwan.tradingagentarmy.agents.AgentArmy
import com.rizwan.tradingagentarmy.agents.AgentArmyService
import com.rizwan.tradingagentarmy.agents.AgentRole
import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.data.local.TradeDao
import com.rizwan.tradingagentarmy.data.remote.AiProviders
import com.rizwan.tradingagentarmy.data.remote.WebSocketManager
import com.rizwan.tradingagentarmy.data.repository.BotRepository
import com.rizwan.tradingagentarmy.data.repository.MarketRepository
import com.rizwan.tradingagentarmy.domain.model.MarketTicker
import com.rizwan.tradingagentarmy.domain.model.PortfolioSummary
import com.rizwan.tradingagentarmy.domain.model.Trade
import com.rizwan.tradingagentarmy.domain.model.WsEvent
import com.rizwan.tradingagentarmy.notifications.Notifier
import com.rizwan.tradingagentarmy.trading.BitgetClient
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

data class AgentCard(
    val role: AgentRole,
    val lastActive: Long,
    val runs: Int,
    val live: Boolean
)

data class ApiLine(
    val label: String,
    val detail: String,
    val state: Int // 0 = ok, 1 = warn, 2 = down
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val marketRepo: MarketRepository,
    private val botRepo: BotRepository,
    private val tradeDao: TradeDao,
    private val prefs: SecurePreferences,
    private val ws: WebSocketManager,
    private val bitget: BitgetClient,
    private val army: AgentArmy,
    private val ai: AiProviders
) : ViewModel() {

    private val _tickers = MutableStateFlow<List<MarketTicker>>(emptyList())
    val tickers: StateFlow<List<MarketTicker>> = _tickers.asStateFlow()

    private val _portfolio = MutableStateFlow<PortfolioSummary?>(null)
    val portfolio: StateFlow<PortfolioSummary?> = _portfolio.asStateFlow()

    private val _trades = MutableStateFlow<List<Trade>>(emptyList())
    val trades: StateFlow<List<Trade>> = _trades.asStateFlow()

    private val _openTrades = MutableStateFlow<List<Trade>>(emptyList())
    val openTrades: StateFlow<List<Trade>> = _openTrades.asStateFlow()

    private val _circuit = MutableStateFlow<WsEvent.CircuitBreaker?>(null)
    val circuit: StateFlow<WsEvent.CircuitBreaker?> = _circuit.asStateFlow()

    private val _backendAlive = MutableStateFlow(false)
    val backendAlive: StateFlow<Boolean> = _backendAlive.asStateFlow()

    private val _noBackend = MutableStateFlow(prefs.backendUrl.isBlank())
    val noBackend: StateFlow<Boolean> = _noBackend.asStateFlow()

    private val _liveTrading = MutableStateFlow(prefs.getBool("live_trading", false))
    val liveTrading: StateFlow<Boolean> = _liveTrading.asStateFlow()

    private val _wsConnected = ws.connected
    val wsConnected: StateFlow<Boolean> = _wsConnected

    private val _lastSync = MutableStateFlow(0L)
    val lastSync: StateFlow<Long> = _lastSync.asStateFlow()

    private val _serviceOn = AgentArmyService.running
    val serviceOn: StateFlow<Boolean> = _serviceOn

    private val _agentsOn = MutableStateFlow(prefs.getBool("agents_enabled", true))
    val agentsOn: StateFlow<Boolean> = _agentsOn.asStateFlow()

    private val _agentCards = MutableStateFlow<List<AgentCard>>(emptyList())
    val agentCards: StateFlow<List<AgentCard>> = _agentCards.asStateFlow()

    private val _apiLines = MutableStateFlow<List<ApiLine>>(emptyList())
    val apiLines: StateFlow<List<ApiLine>> = _apiLines.asStateFlow()

    private val _armyStatus = army.status
    val armyStatus: StateFlow<String> = _armyStatus

    private val _tradesToday = MutableStateFlow(0)
    val tradesToday: StateFlow<Int> = _tradesToday.asStateFlow()

    private val _totalTrades = MutableStateFlow(0)
    val totalTrades: StateFlow<Int> = _totalTrades.asStateFlow()

    private var poller: Job? = null

    init {
        ws.connect()
        viewModelScope.launch {
            ws.events.collect { handle(it) }
        }
        viewModelScope.launch {
            while (isActive) {
                buildAgents()
                delay(5_000)
            }
        }
        startPolling()
    }

    /** 24/7 master switch — starts/stops the foreground Army service. */
    fun toggleArmy(on: Boolean) {
        prefs.putBool("agents_enabled", on)
        _agentsOn.value = on
        if (on) {
            runCatching { AgentArmyService.start(context) }
            AppEventLog.record("army", "24/7 army STARTED from dashboard")
        } else {
            runCatching { AgentArmyService.stop(context) }
            AppEventLog.record("army", "24/7 army STOPPED from dashboard")
        }
    }

    fun runRoundNow() {
        viewModelScope.launch {
            if (!AgentArmyService.running.value) runCatching { AgentArmyService.start(context) }
            army.runRound()
        }
    }

    private fun buildAgents() {
        val last = army.agentLast.value
        val runs = army.agentRuns.value
        val on = _serviceOn.value
        _agentCards.value = AgentRole.entries.map { role ->
            val at = last[role.id] ?: 0L
            AgentCard(role, at, runs[role.id] ?: 0, on && at > 0)
        }
        buildApiLines()
    }

    private fun buildApiLines() {
        val chain = ai.chain()
        val aiLine = if (chain.isEmpty()) {
            ApiLine("AI", "Koi key nahi — Settings → AI", 2)
        } else {
            ApiLine("AI", ai.labelOf(chain.first()) + if (chain.size > 1) " +${chain.size - 1} fallback" else "", 0)
        }
        val marketType = prefs.getString("bitget_market_type", "SPOT")
        val demo = prefs.getBool("bitget_demo_mode", false)
        val exLine = if (bitget.configured) {
            ApiLine("Exchange", "Bitget ${if (demo) "DEMO" else "REAL"} · $marketType", 0)
        } else {
            ApiLine("Exchange", "Public data (API keys nahi)", 1)
        }
        val feedLine = if (_wsConnected.value) {
            ApiLine("Feed", "WebSocket live", 0)
        } else {
            ApiLine("Feed", "REST polling", 1)
        }
        val backLine = when {
            _backendAlive.value -> ApiLine("Backend", "Connected", 0)
            _noBackend.value -> ApiLine("Backend", "On-device mode", 1)
            else -> ApiLine("Backend", "Offline", 2)
        }
        val pnlSource = if (_portfolio.value?.fromBackend == true) "backend" else "local DB"
        val dataLine = ApiLine("PnL source", pnlSource, 0)
        _apiLines.value = listOf(aiLine, exLine, feedLine, backLine, dataLine)
    }

    fun closePosition(id: Long) {
        viewModelScope.launch {
            val trade = tradeDao.all().firstOrNull { it.id == id } ?: return@launch
            val price = if (trade.marketType == "CFD") {
                bitget.cfdPrice(trade.symbol, if (trade.side == "BUY") "sell" else "buy").getOrDefault(0.0)
            } else toolsLastPrice(trade.symbol)
            if (price <= 0.0) return@launch
            val live = trade.mode.equals("live", true) || trade.mode.equals("hft-live", true)
            val result = if (!live) true else runCatching {
                when (trade.marketType) {
                    "CFD" -> {
                        val positionId = trade.positionId.ifBlank {
                            bitget.cfdPositionList(trade.symbol).getOrDefault(emptyList())
                                .firstOrNull { it.symbol.equals(trade.symbol, true) }?.positionId.orEmpty()
                        }
                        bitget.closeCfdPosition(positionId, trade.quantity).ok
                    }
                    "FUTURES" -> bitget.closeFuturesMarket(
                        trade.symbol,
                        if (trade.side == "BUY") "sell" else "buy",
                        "%.8f".format(trade.quantity),
                        "manual_exit_${trade.id}_${System.currentTimeMillis()}"
                    ).ok
                    else -> bitget.closeSpotMarket(
                        trade.symbol, "sell", trade.quantity,
                        "manual_exit_${trade.id}_${System.currentTimeMillis()}"
                    ).ok
                }
            }.getOrDefault(false)
            if (result) {
                val pnl = if (trade.side == "BUY") (price - trade.entry) * trade.quantity else (trade.entry - price) * trade.quantity
                tradeDao.closeTrade(trade.id, price, pnl, "MANUAL_CLOSE")
                refresh()
            }
        }
    }

    private suspend fun toolsLastPrice(symbol: String): Double =
        if (symbol.uppercase().replace("/", "") in setOf("XAUUSD", "EURUSD", "GBPUSD", "USDJPY")) {
            bitget.cfdPrice(symbol).getOrDefault(0.0)
        } else runCatching {
            val body = okhttp3.OkHttpClient().newCall(
                okhttp3.Request.Builder().url("https://api.binance.com/api/v3/ticker/price?symbol=${symbol.replace("/", "").uppercase()}").build()
            ).execute().use { it.body?.string().orEmpty() }
            Regex("\"price\":\"([0-9.]+)\"").find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        }.getOrDefault(0.0)

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
        _liveTrading.value = prefs.getBool("live_trading", false)
        _agentsOn.value = prefs.getBool("agents_enabled", true)
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
            _tradesToday.value = tradeDao.all().count { it.timestamp >= todayStart }
            _totalTrades.value = tradeDao.all().size
        }
        runCatching {
            val all = botRepo.allTrades()
            _trades.value = all.take(12)
            _openTrades.value = all.filter {
                it.status.equals("OPEN", true) || it.status.equals("PAPER_OPEN", true) ||
                    it.status.equals("EXIT_PENDING", true) || it.status.equals("UNKNOWN", true)
            }
        }
        _backendAlive.value = marketRepo.backendAlive()
        _lastSync.value = System.currentTimeMillis()
        buildAgents()
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
}

object AppEventLog {
    fun record(type: String, detail: String) =
        com.rizwan.tradingagentarmy.agents.AppEvents.record(type, detail)
}
