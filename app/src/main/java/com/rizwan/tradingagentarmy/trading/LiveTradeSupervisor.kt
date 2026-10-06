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
            while (isActive) {
                runCatching { reconcile() }
                    .onFailure { AppEvents.record("reconcile", "snapshot error: ${it.message}") }
                runCatching { monitorOnce() }
                    .onFailure { AppEvents.record("reconcile", "monitor error: ${it.message}") }
                delay(15_000)
            }
        }
    }

    private suspend fun reconcile() {
        // Sync existing exchange positions even while live order entry is disabled.
        if (!bitget.configured) return
        val syncAt = System.currentTimeMillis()
        trades.markPendingUnknown(syncAt)
        val local = trades.openTrades()
        val cfdLocal = local.filter {
            it.marketType == "CFD" && (it.mode.equals("live", true) || it.mode.equals("hft-live", true))
        }.toMutableList()
        val remote = bitget.cfdPositionList().getOrElse {
            AppEvents.record("reconcile", "cfd snapshot failed: ${it.message}")
            return
        }
        val remoteIds = remote.map { it.positionId }.toSet()
        remote.forEach { position ->
            val currentPrice = if (position.quantity > 0.0) {
                if (position.side == "BUY") position.openPrice + position.totalProfit / position.quantity
                else position.openPrice - position.totalProfit / position.quantity
            } else position.openPrice
            val existing = trades.byPositionId(position.positionId)
                ?: cfdLocal.firstOrNull {
                    it.positionId.isBlank() && it.symbol.equals(position.symbol, true) &&
                        normalizeSide(it.side) == normalizeSide(position.side)
                }
            if (existing == null) {
                trades.insert(
                    com.rizwan.tradingagentarmy.data.local.TradeEntity(
                        symbol = position.symbol,
                        side = position.side,
                        entry = position.openPrice,
                        actualEntry = position.openPrice,
                        exit = null,
                        pnl = position.unrealizedPnl,
                        mode = "live",
                        botName = "Recovered CFD",
                        model = "exchange-reconciliation",
                        timestamp = syncAt,
                        positionId = position.positionId,
                        status = "OPEN",
                        marketType = "CFD",
                        stopLoss = position.stopLoss,
                        takeProfit = position.takeProfit,
                        quantity = position.quantity,
                        filledQuantity = position.quantity,
                        protectionStatus = if (position.stopLoss != null && position.takeProfit != null) "VERIFIED" else "MISSING",
                        lastExchangeSync = syncAt,
                        currentPrice = currentPrice,
                        unrealizedPnl = position.totalProfit
                    )
                )
                AppEvents.record("reconcile", "recovered ${position.symbol}/${position.positionId}")
            } else {
                cfdLocal.removeAll { it.id == existing.id }
                trades.updateExchangeState(
                    existing.id, position.positionId, position.openPrice, position.openPrice,
                    position.quantity, position.quantity, position.stopLoss, position.takeProfit,
                    if (position.stopLoss != null && position.takeProfit != null) "VERIFIED" else "MISSING",
                    syncAt, currentPrice, position.totalProfit, "OPEN"
                )
            }
        }
        cfdLocal.filter { it.positionId.isNotBlank() && it.positionId !in remoteIds }.forEach { stale ->
            val isLong = normalizeSide(stale.side) == "BUY"
            val price = tools.lastPrice(stale.symbol, if (isLong) "sell" else "buy")
            if (price > 0.0) {
                val pnl = if (isLong) (price - stale.entry) * stale.quantity else (stale.entry - price) * stale.quantity
                trades.closeTrade(stale.id, price, pnl, "RECONCILED_CLOSED")
                AppEvents.record("reconcile", "closed local stale ${stale.symbol}/${stale.positionId}")
            }
        }
        val other = local.filter {
            (it.mode.equals("live", true) || it.mode.equals("hft-live", true)) && it.marketType != "CFD"
        }
        other.groupBy { it.marketType }.forEach { (market, rows) ->
            val result = if (market == "FUTURES") bitget.currentFuturesPositions()
            else bitget.currentSpotPlans(rows.firstOrNull()?.symbol ?: prefs.getString("army_symbol", "BTCUSDT"))
            AppEvents.record("reconcile", "${market.lowercase()} snapshot ok=${result.first}, localOpen=${rows.size}")
        }
    }

    private suspend fun monitorOnce() {
        // Turning off live entry must not turn off protection/reconciliation for open positions.
        if (!bitget.configured) return
        for (trade in trades.openTrades()) {
            if (!trade.mode.equals("live", true) && !trade.mode.equals("hft-live", true)) continue
            val isLong = normalizeSide(trade.side) == "BUY"
            val price = tools.lastPrice(trade.symbol, if (isLong) "sell" else "buy")
            if (price <= 0.0) continue
            val hit = when {
                prefs.getBool("risk_kill", false) -> "KILL_SWITCH"
                isLong && trade.stopLoss != null && price <= trade.stopLoss -> "SL"
                isLong && trade.takeProfit != null && price >= trade.takeProfit -> "TP"
                !isLong && trade.stopLoss != null && price >= trade.stopLoss -> "SL"
                !isLong && trade.takeProfit != null && price <= trade.takeProfit -> "TP"
                else -> null
            } ?: continue

            // State transition is persisted before the network call: a crash or
            // retry cannot submit the same exit twice from this process.
            trades.setStatus(trade.id, "EXIT_PENDING")
            val closeSide = if (isLong) "sell" else "buy"
            val oid = "exit_${trade.clientOid.ifBlank { trade.id.toString() }}_$hit"
            val result = if (trade.marketType == "CFD") {
                val positionId = trade.positionId.ifBlank {
                    bitget.cfdPositionList(trade.symbol).getOrDefault(emptyList())
                        .firstOrNull { it.symbol.equals(trade.symbol, true) && it.side.equals(if (trade.side == "BUY") "BUY" else "SELL", true) }
                        ?.positionId.orEmpty()
                }
                bitget.closeCfdPosition(positionId, trade.quantity)
            } else if (trade.marketType == "FUTURES") {
                bitget.closeFuturesMarket(trade.symbol, closeSide, "%.8f".format(trade.quantity), oid)
            } else {
                bitget.closeSpotMarket(trade.symbol, closeSide, trade.quantity, oid)
            }
            if (result.ok) {
                val pnl = if (isLong) (price - trade.entry) * trade.quantity else (trade.entry - price) * trade.quantity
                trades.closeTrade(trade.id, price, pnl, hit)
                AppEvents.record("exit", "${trade.symbol} $hit @ $price")
                Notifier.post(context, "agent_army", "Position closed: $hit", "${trade.symbol} @ $price", high = true)
            } else {
                trades.setStatus(trade.id, "OPEN")
                AppEvents.record("exit", "${trade.symbol} exit failed: ${result.message}")
            }
        }
    }

    private fun normalizeSide(side: String): String = when (side.uppercase()) {
        "BUY", "LONG" -> "BUY"
        "SELL", "SHORT" -> "SELL"
        else -> side.uppercase()
    }

    fun stop() = scope.cancel()
}
