package com.rizwan.tradingagentarmy.agents

import android.content.Context
import androidx.room.withTransaction
import com.rizwan.tradingagentarmy.data.local.AppDatabase
import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.data.local.TradeEntity
import com.rizwan.tradingagentarmy.data.local.WarDao
import com.rizwan.tradingagentarmy.data.local.WarMessageEntity
import com.rizwan.tradingagentarmy.data.remote.AiProviders
import com.rizwan.tradingagentarmy.notifications.Notifier
import com.rizwan.tradingagentarmy.trading.BitgetClient
import com.rizwan.tradingagentarmy.trading.RiskGuard
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentArmy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ai: AiProviders,
    private val prefs: SecurePreferences,
    private val tools: AgentTools,
    private val db: AppDatabase,
    private val warDao: WarDao,
    private val tradeDao: com.rizwan.tradingagentarmy.data.local.TradeDao,
    private val bitget: BitgetClient,
    private val risk: RiskGuard,
    private val hft: com.rizwan.tradingagentarmy.trading.HftEngine,
    private val botRepo: com.rizwan.tradingagentarmy.data.repository.BotRepository
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _messages = MutableStateFlow<List<WarMessage>>(emptyList())
    val messages: StateFlow<List<WarMessage>> = _messages

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _lastPlan = MutableStateFlow<TradePlan?>(null)
    val lastPlan: StateFlow<TradePlan?> = _lastPlan

    private val _status = MutableStateFlow("Idle — tap Start to deploy the army")
    val status: StateFlow<String> = _status

    /** role id → last time this agent posted (dashboard live status). */
    private val _agentLast = MutableStateFlow<Map<String, Long>>(emptyMap())
    val agentLast: StateFlow<Map<String, Long>> = _agentLast

    /** role id → how many times it ran since app start. */
    private val _agentRuns = MutableStateFlow<Map<String, Int>>(emptyMap())
    val agentRuns: StateFlow<Map<String, Int>> = _agentRuns

    private var roundId = 0L
    private var tickIndex = 0

    init {
        // hydrate from DB (newest first)
        kotlinx.coroutines.runBlocking {
            runCatching {
                val recent = warDao.recent()
                _messages.value = recent.map {
                    WarMessage(it.id, it.roundId, roleById(it.agentId), it.content, it.kind, it.timestamp)
                }
                roundId = recent.maxOfOrNull { it.roundId } ?: 0L
            }
        }
    }

    private fun roleById(id: String): AgentRole =
        AgentRole.entries.firstOrNull { it.id == id } ?: AgentRole.COORDINATOR

    private suspend fun post(role: AgentRole, content: String, kind: String = "talk"): WarMessage {
        val rowId = warDao.insert(
            WarMessageEntity(
                roundId = roundId, agentId = role.id, agentName = role.displayName,
                emoji = role.emoji, content = content, kind = kind
            )
        )
        val m = WarMessage(rowId, roundId, role, content, kind)
        _messages.update { (listOf(m) + it).take(400) }
        _agentLast.update { it + (role.id to System.currentTimeMillis()) }
        _agentRuns.update { it + (role.id to ((it[role.id] ?: 0) + 1)) }
        warDao.trim()
        AppEvents.record("agent", "${role.emoji} ${role.displayName}: ${content.take(90)}")
        return m
    }

    /** Operator (user) message inside the army feed. */
    suspend fun postOperator(text: String) {
        if (roundId == 0L) roundId = System.currentTimeMillis()
        post(AgentRole.COORDINATOR, "👤 Operator: $text", kind = "user")
    }

    /** Progress / system note posted by the commander. */
    suspend fun postNote(text: String, kind: String = "system") {
        if (roundId == 0L) roundId = System.currentTimeMillis()
        post(AgentRole.COORDINATOR, text, kind = kind)
    }

    suspend fun setBusy(value: Boolean) {
        _busy.value = value
    }

    fun markStatus(text: String) {
        _status.value = text
    }

    private suspend fun ask(role: AgentRole, prompt: String): String {
        val system = AgentPrompts.systemFor(role)
        var lastErr: String? = null
        for (target in ai.chain()) {
            try {
                val out = ai.stream(target, system, emptyList(), prompt) {}
                if (out.isNotBlank()) {
                    AppEvents.record("llm", "${role.id} <- ${ai.labelOf(target)}")
                    return out.trim()
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                lastErr = "${ai.labelOf(target)}: ${e.message?.take(80)}"
            }
        }
        return "[model unavailable — ${lastErr ?: "no AI key configured"}]"
    }

    suspend fun sendUserMessage(text: String) {
        if (text.isBlank()) return
        if (roundId == 0L) roundId = System.currentTimeMillis()
        post(AgentRole.COORDINATOR, "👤 Operator: $text", kind = "user")
        val cmd = Commander.parse(text)
        if (cmd != null && executeCommand(cmd)) return
        val reply = ask(AgentRole.COORDINATOR, "The operator said: $text\nRespond as the team commander in max 3 lines.")
        post(AgentRole.COORDINATOR, reply)
    }

    /** Runs a local (non-AI) operator command. Returns true when handled. */
    suspend fun executeCommand(cmd: Command): Boolean {
        when (cmd) {
            is Command.Help -> postNote(
                "🧭 Commands: `start army` · `stop army` · `run round` · `scalp on/off` · " +
                    "`symbol BTCUSDT` · `kill on/off` · `risk status` · `positions` · `bots` · `status`"
            )
            is Command.Hello -> postNote("👋 Commander online. `help` likho sab commands ke liye.")
            is Command.StartArmy -> {
                prefs.putBool("agents_enabled", true)
                _status.value = "Army ON — 24/7 mode"
                postNote("✅ Army ON — har minute agent scan + round timer chalu.")
            }
            is Command.StopArmy -> {
                prefs.putBool("agents_enabled", false)
                _status.value = "Army paused by operator"
                postNote("⏸️ Army paused — rounds aur minute ticks ruk gaye.")
            }
            is Command.RunRound -> {
                postNote("▶️ Full round shuru — data → analysts → debate → trader → risk → PM.")
                runRound(cmd.brief)
            }
            is Command.Scalp -> {
                prefs.putBool("scalp_enabled", cmd.on)
                prefs.putBool("hft_enabled", cmd.on)
                if (cmd.on) hft.start() else hft.stop()
                postNote(
                    if (cmd.on) "⚡ Scalper ON — ${risk.scalpTpPct}% TP / ${risk.scalpSlPct}% SL, target $${risk.scalpTargetUsd}/trade."
                    else "🛑 Scalper OFF."
                )
            }
            is Command.SetSymbol -> {
                prefs.putString("army_symbol", cmd.symbol)
                postNote("🎯 Mission symbol changed to **${cmd.symbol}**")
            }
            is Command.Kill -> {
                risk.killSwitch = cmd.on
                postNote(if (cmd.on) "🔴 KILL SWITCH ON — sab trading blocked." else "🟢 Kill switch OFF — trading resumed.")
            }
            is Command.RiskStatus -> postNote(
                "🛡️ Risk: kill=${risk.killSwitch} · today PnL $${"%.2f".format(risk.todayPnl())} " +
                    "(limit -${"%.2f".format(risk.maxDailyLossUsd)}) · trades ${risk.todayTradeCount()}/${risk.maxTradesPerDay} · " +
                    "max trade $${risk.maxTradeUsd} · consec-block=${risk.blockOnLosingStreak}"
            )
            is Command.Positions -> {
                val open = tradeDao.openTrades()
                if (open.isEmpty()) postNote("📭 Abhi koi open position nahi hai.")
                else postNote(
                    "📂 Open positions (${open.size}):\n" + open.take(6).joinToString("\n") { t ->
                        "• ${t.symbol} ${t.side} @ ${t.entry} (SL ${t.stopLoss ?: "-"} / TP ${t.takeProfit ?: "-"}) — $${"%.2f".format(t.unrealizedPnl)} [${t.status}]"
                    }
                )
            }
            is Command.Bots -> {
                val list = botRepo.allNow()
                if (list.isEmpty()) postNote("🤖 Abhi koi bot nahi hai (Bots tab se banao).")
                else postNote("🤖 Bots:\n" + list.take(8).joinToString("\n") { "• ${it.name} — ${it.status}" })
            }
            is Command.Status -> postNote(
                "📡 Status: army=${if (prefs.getBool("agents_enabled", true)) "ON" else "PAUSED"} · " +
                    "mode=${if (prefs.getBool("live_trading", false)) "LIVE" else "PAPER"} · " +
                    "symbol=${prefs.getString("army_symbol", "BTCUSDT")} · open=${tradeDao.openTrades().size} · " +
                    "today PnL $${"%.2f".format(risk.todayPnl())} · ${_status.value}"
            )
        }
        return true
    }

    /**
     * 1-minute rotation: har minute ek agent market par nazar rakhta hai aur
     * feed me apni 3-line report chhodta hai (group chat between all agents).
     */
    private val tickRotation = listOf(
        AgentRole.SCOUT, AgentRole.TECHNICAL, AgentRole.SENTIMENT, AgentRole.CHART,
        AgentRole.NEWS, AgentRole.FUNDAMENTAL, AgentRole.APP_WATCH, AgentRole.RISK
    )

    suspend fun runAgentTick() {
        if (_busy.value) return
        val symbol = prefs.getString("army_symbol", "BTCUSDT").ifBlank { "BTCUSDT" }
        val role = tickRotation[tickIndex % tickRotation.size]
        tickIndex++
        _status.value = "${role.emoji} ${role.displayName} scanning $symbol…"
        runCatching {
            val pack = buildString {
                appendLine("MARKET:")
                appendLine(tools.marketSnapshot(8))
                appendLine()
                appendLine("$symbol candles:")
                appendLine(tools.klines(symbol, "5m", 30))
                appendLine()
                appendLine(tools.appActivity())
            }
            val out = ask(role, "1-minute autonomous scan of $symbol. Use only this data.\n$pack\nMax 3 lines, give exact levels or NO SIGNAL.")
            post(role, out)
        }.onFailure {
            AppEvents.record("error", "agent tick failed: ${it.message}")
        }
    }

    suspend fun runRound(userBrief: String = "") {
        if (_busy.value) return
        _busy.value = true
        try {
            roundId = System.currentTimeMillis()
            val symbol = prefs.getString("army_symbol", "BTCUSDT").ifBlank { "BTCUSDT" }
            _status.value = "Round live — $symbol"
            AppEvents.record("round", "started for $symbol")

            post(
                AgentRole.COORDINATOR,
                "⚔️ Round started. Mission: full-team analysis of **$symbol**, reach a trade decision." +
                    (if (userBrief.isNotBlank()) "\nFocus: $userBrief" else "")
            )

            val snapshot = tools.marketSnapshot()
            val candles = tools.klines(symbol, "1h", 48)
            val search = tools.webSearch("$symbol market news today")
            val history = tools.pastTradesSummary()
            val appLog = tools.appActivity()

            val dataPack = """
MARKET SNAPSHOT:
$snapshot

${symbol} 1H CANDLES (O H L C V):
$candles

WEB SEARCH for "$symbol":
$search

$history

$appLog
""".trim()

            _status.value = "📡 Data ready — 6 analysts ab report likh rahe hain"
            post(AgentRole.SCOUT, ask(AgentRole.SCOUT, "Search results for $symbol:\n$search"))
            post(AgentRole.NEWS, ask(AgentRole.NEWS, dataPack))
            post(AgentRole.SENTIMENT, ask(AgentRole.SENTIMENT, dataPack))
            post(AgentRole.TECHNICAL, ask(AgentRole.TECHNICAL, dataPack))
            post(AgentRole.CHART, ask(AgentRole.CHART, dataPack))
            post(AgentRole.FUNDAMENTAL, ask(AgentRole.FUNDAMENTAL, dataPack))

            val analystSummaries = _messages.value
                .filter { it.roundId == roundId && it.agent in listOf(
                    AgentRole.SCOUT, AgentRole.NEWS, AgentRole.SENTIMENT,
                    AgentRole.TECHNICAL, AgentRole.CHART, AgentRole.FUNDAMENTAL
                ) }
                .take(6).joinToString("\n") { "${it.agent.emoji} ${it.agent.displayName}: ${it.content}" }

            val debatePack = "$analystSummaries\n\nDATA:\n$dataPack"
            _status.value = "🐂 Bear vs Bull debate chal raha hai…"
            val bullCase = ask(AgentRole.BULL, debatePack)
            post(AgentRole.BULL, bullCase)
            val bearCase = ask(AgentRole.BEAR, "$debatePack\n\nBull just said:\n$bullCase")
            post(AgentRole.BEAR, bearCase)

            _status.value = "💰 Trader plan bana raha hai…"
            val traderPlan = ask(
                AgentRole.TRADER,                "$debatePack\n\nBULL: $bullCase\n\nBEAR: $bearCase\n\nRisk rules: max trade $${risk.maxTradeUsd}, " +
                    "max daily loss $${risk.maxDailyLossUsd}, kill switch=${risk.killSwitch}, today's PnL=$${"%.2f".format(risk.todayPnl())}"
            )
            post(AgentRole.TRADER, traderPlan)

            val riskOpinion = ask(
                AgentRole.RISK,
                "Trade plan:\n$traderPlan\n\n$history\n\nRules: max trade $${risk.maxTradeUsd}, " +
                    "daily loss limit -$${risk.maxDailyLossUsd}, killSwitch=${risk.killSwitch}, todayPnl=$${"%.2f".format(risk.todayPnl())}"
            )
            post(AgentRole.RISK, riskOpinion)

            _status.value = "🏆 PM final decision le raha hai…"
            val pmRaw = ask(
                AgentRole.PM,
                "Analysts:\n$analystSummaries\n\nBULL: $bullCase\n\nBEAR: $bearCase\n\nTRADER: $traderPlan\n\nRISK: $riskOpinion\n\n" +
                    "Symbol to judge: $symbol. Output the JSON decision only."
            )
            val plan = parsePlan(pmRaw, symbol)
            _lastPlan.value = plan
            post(
                AgentRole.PM,
                "🏁 DECISION: **${plan.action}** ${plan.symbol} (confidence ${plan.confidence}%) — ${plan.reasoning}",
                kind = "decision"
            )

            _status.value = "⚙️ Decision execute ho raha hai…"
            execute(plan)

            _status.value = "Round complete — last decision: ${plan.action} ${plan.symbol}"
            AppEvents.record("round", "finished with ${plan.action}")
        } catch (c: CancellationException) {
            _status.value = "Round cancelled"
            throw c
        } catch (e: Exception) {
            _status.value = "Round error: ${e.message}"
            AppEvents.record("error", "round failed: ${e.message}")
            runCatching { post(AgentRole.COORDINATOR, "⚠️ Round failed: ${e.message}", kind = "system") }
        } finally {
            _busy.value = false
        }
    }

    private fun parsePlan(raw: String, symbol: String): TradePlan {
        val candidate = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL).find(raw)?.value ?: raw
        return runCatching { json.decodeFromString<TradePlan>(candidate) }
            .getOrElse {
                runCatching { json.decodeFromString<TradePlan>(extractJson(candidate)) }
                    .getOrElse {
                        TradePlan(action = if (raw.contains("BUY", true)) "BUY" else if (raw.contains("SELL", true)) "SELL" else "HOLD",
                            symbol = symbol, confidence = 40, reasoning = raw.take(120))
                    }
            }.let { if (it.symbol.isBlank()) it.copy(symbol = symbol) else it }
    }

    private fun extractJson(s: String): String {
        val start = s.indexOf('{'); val end = s.lastIndexOf('}')
        return if (start in 0 until end) s.substring(start, end + 1) else s
    }

    private suspend fun execute(plan: TradePlan) {
        if (plan.action == "HOLD" || plan.action.isBlank()) return
        if (plan.symbol.isBlank()) return
        val live = prefs.getBool("live_trading", false)
        val mt5Symbol = plan.symbol.uppercase().replace("/", "") in setOf("XAUUSD", "XAUUSD.S", "XAUUSD.PRO", "EURUSD", "EURUSD.S", "EURUSD.PRO", "GBPUSD", "USDJPY")
        val marketType = if (mt5Symbol) "CFD" else prefs.getString("bitget_market_type", "SPOT").uppercase()
        val isLive = live && bitget.configured
        val size = risk.capSize(plan.sizeUsd ?: 10.0)
        val deny = risk.evaluate(size, isLive)
        if (deny != null) {
            post(AgentRole.RISK, "🛑 BLOCKED: $deny", kind = "decision")
            Notifier.post(context, "agent_army", "🛡️ Risk blocked a trade", deny, high = true)
            return
        }
        val price = tools.lastPrice(plan.symbol)
        if (live && !bitget.configured) {
            post(AgentRole.RISK, "🛑 LIVE blocked: Bitget API keys with CFD/UTA trade permission are missing", kind = "decision")
            return
        }
        val clientOid = "army_${plan.symbol}_${roundId}_${plan.action.lowercase()}"
        if (tradeDao.byClientOid(clientOid) != null) {
            post(AgentRole.RISK, "🛑 DUPLICATE BLOCKED: signal $clientOid already exists", kind = "decision")
            return
        }
        if (live && (plan.stopLoss == null || plan.takeProfit == null)) {
            post(AgentRole.RISK, "🛑 LIVE blocked: exchange SL and TP are required", kind = "decision")
            return
        }
        if (live && marketType == "SPOT" && plan.action == "SELL") {
            post(AgentRole.RISK, "🛑 LIVE spot SELL blocked until reconciled base quantity is available", kind = "decision")
            return
        }
        var exchangeOrderId = ""
        if (isLive) {
            val side = if (plan.action == "BUY") "buy" else "sell"
            val res = bitget.placeProtected(marketType, plan.symbol, side, size, price, plan.stopLoss, plan.takeProfit, clientOid)
            if (!res.ok) {
                post(AgentRole.RISK, "❌ ${if (marketType == "MT5") "MT5" else "Bitget"} order failed: ${res.message}", kind = "decision")
                Notifier.post(context, "agent_army", "❌ Order failed", res.message, high = true)
                return
            }
            if (res.orderId.isBlank()) {
                post(AgentRole.RISK, "🛑 LIVE blocked: exchange returned no order id", kind = "decision")
                return
            }
            exchangeOrderId = res.orderId
        }
        val localTradeId = tradeDao.insert(
            TradeEntity(
                symbol = plan.symbol, side = plan.action, entry = price, exit = null,
                pnl = 0.0, mode = if (isLive) "live" else "paper",
                botName = "AI Army", model = prefs.pinnedModel.ifBlank { "auto-chain" },
                timestamp = System.currentTimeMillis(), clientOid = clientOid,
                exchangeOrderId = exchangeOrderId,
                stopLoss = plan.stopLoss, takeProfit = plan.takeProfit,
                status = if (isLive) "OPEN" else "PAPER_OPEN", marketType = marketType,
                quantity = if (price > 0.0) size / price else 0.0
            )
        )
        if (isLive && marketType == "CFD") {
            val remote = bitget.cfdPositionList(plan.symbol).getOrDefault(emptyList())
                .lastOrNull { it.symbol.equals(plan.symbol, true) && it.side.equals(if (plan.action == "BUY") "BUY" else "SELL", true) }
            if (remote != null) {
                tradeDao.updateExchangeState(
                    localTradeId,
                    remote.positionId,
                    remote.openPrice,
                    remote.openPrice,
                    remote.quantity,
                    remote.quantity,
                    remote.stopLoss,
                    remote.takeProfit,
                    if (remote.stopLoss != null && remote.takeProfit != null) "VERIFIED" else "MISSING",
                    System.currentTimeMillis(),
                    remote.openPrice,
                    remote.totalProfit,
                    "OPEN"
                )
            }
        }
        val mode = if (isLive) "LIVE" else "PAPER"
        post(
            AgentRole.TRADER,
            "✅ $mode ${plan.action} ${plan.symbol} @ $price · size $$${"%.2f".format(size)} · SL ${plan.stopLoss ?: "-"} · TP ${plan.takeProfit ?: "-"}",
            kind = "decision"
        )
        Notifier.trade(context, "$mode ${plan.action} ${plan.symbol} @ $price ($${"%.0f".format(size)})")
        AppEvents.record("trade", "$mode ${plan.action} ${plan.symbol} @ $price")
    }

    suspend fun clearHistory() {
        warDao.clear()
        _messages.value = emptyList()
        _lastPlan.value = null
        _status.value = "History cleared"
    }
}
