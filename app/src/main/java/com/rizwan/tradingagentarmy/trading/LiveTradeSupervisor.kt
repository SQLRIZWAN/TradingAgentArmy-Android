package com.rizwan.tradingagentarmy.trading

import android.content.Context
import com.rizwan.tradingagentarmy.agents.AgentTools
import com.rizwan.tradingagentarmy.agents.AppEvents
import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.data.local.TradeDao
import com.rizwan.tradingagentarmy.notifications.Notifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crash-safe local watchdog. Exchange TP/SL remains the primary protection;
 * this is a secondary monitor for kill-switch/manual exit and reconciliation.
 */
@Singleton
class LiveTradeSupervisor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SecurePreferences,
    private val trades: TradeDao,
    private val tools: AgentTools,
    private val bitget: BitgetClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            reconcile()
            while (isActive) {
                runCatching { monitorOnce() }
                    .onFailure { AppEvents.record("reconcile", "monitor error: ${it.message}") }
                delay(5_000)
            }
        }
    }

    private suspend fun reconcile() {
        if (!prefs.getBool("live_trading", false) || !bitget.configured) return
        val open = trades.openTrades()
        open.groupBy { it.marketType }.forEach { (market, rows) ->
            val result = if (market == "FUTURES") bitget.currentFuturesPositions()
            else if (market == "CFD") bitget.currentCfdPositions(rows.firstOrNull()?.symbol ?: "")
            else bitget.currentSpotPlans(rows.firstOrNull()?.symbol ?: prefs.getString("army_symbol", "BTCUSDT"))
            AppEvents.record("reconcile", "${market.lowercase()} exchange snapshot ok=${result.first}, localOpen=${rows.size}")
        }
    }

    private suspend fun monitorOnce() {
        if (!prefs.getBool("live_trading", false) || !bitget.configured) return
        for (trade in trades.openTrades()) {
            val price = tools.lastPrice(trade.symbol)
            if (price <= 0.0) continue
            val hit = when {
                prefs.getBool("risk_kill", false) -> "KILL_SWITCH"
                trade.side == "BUY" && trade.stopLoss != null && price <= trade.stopLoss -> "SL"
                trade.side == "BUY" && trade.takeProfit != null && price >= trade.takeProfit -> "TP"
                trade.side == "SELL" && trade.stopLoss != null && price >= trade.stopLoss -> "SL"
                trade.side == "SELL" && trade.takeProfit != null && price <= trade.takeProfit -> "TP"
                else -> null
            } ?: continue

            // State transition is persisted before the network call: a crash or
            // retry cannot submit the same exit twice from this process.
            trades.setStatus(trade.id, "EXIT_PENDING")
            val closeSide = if (trade.side == "BUY") "sell" else "buy"
            val oid = "exit_${trade.clientOid.ifBlank { trade.id.toString() }}_$hit"
            val result = if (trade.marketType == "CFD") {
                bitget.closeCfd(trade.symbol)
            } else if (trade.marketType == "FUTURES") {
                bitget.closeFuturesMarket(trade.symbol, closeSide, "%.8f".format(trade.quantity), oid)
            } else {
                bitget.closeSpotMarket(trade.symbol, closeSide, trade.quantity, oid)
            }
            if (result.ok) {
                val pnl = if (trade.side == "BUY") (price - trade.entry) * trade.quantity else (trade.entry - price) * trade.quantity
                trades.closeTrade(trade.id, price, pnl, hit)
                AppEvents.record("exit", "${trade.symbol} $hit @ $price")
                Notifier.post(context, "agent_army", "Position closed: $hit", "${trade.symbol} @ $price", high = true)
            } else {
                trades.setStatus(trade.id, "OPEN")
                AppEvents.record("exit", "${trade.symbol} exit failed: ${result.message}")
            }
        }
    }

    fun stop() = scope.cancel()
}
