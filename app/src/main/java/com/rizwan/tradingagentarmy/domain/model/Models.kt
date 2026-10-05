package com.rizwan.tradingagentarmy.domain.model

import kotlinx.serialization.Serializable

enum class MessageRole { USER, AI, SYSTEM, SIGNAL }

data class ChatMessage(
    val id: Long = 0,
    val role: MessageRole,
    val content: String,
    val model: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val streaming: Boolean = false
)

enum class BotStatus { RUNNING, DEMO, STOPPED, PENDING }

data class Bot(
    val id: String,
    val name: String,
    val market: String,
    val strategy: String,
    val status: BotStatus,
    val pnlToday: Double = 0.0,
    val pnlTotal: Double = 0.0,
    val gate1: Boolean = false,
    val gate2: Boolean = false,
    val gate3: Boolean = false,
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)

data class Trade(
    val id: Long = 0,
    val symbol: String,
    val side: String,
    val entry: Double,
    val exit: Double?,
    val pnl: Double,
    val mode: String,
    val botName: String,
    val model: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val clientOid: String = "",
    val exchangeOrderId: String = "",
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val slOrderId: String = "",
    val tpOrderId: String = "",
    val status: String = "CLOSED",
    val marketType: String = "SPOT",
    val exitReason: String = "",
    val quantity: Double = 0.0
)

data class MarketTicker(
    val symbol: String,
    val price: Double,
    val change24h: Double,
    val decimals: Int = 2
)

data class PortfolioSummary(
    val todayPnl: Double,
    val totalPnl: Double,
    val activeRunning: Int,
    val activeDemo: Int,
    val stopped: Int,
    val fromBackend: Boolean
)

@Serializable
data class TradeSignal(
    val timestamp: String = "",
    val symbol: String,
    val action: String,
    val order_type: String = "MARKET",
    val entry_price: Double = 0.0,
    val stop_loss: Double = 0.0,
    val take_profit_targets: List<Double> = emptyList(),
    val position_size_units: Double = 0.0,
    val leverage: Int = 1,
    val risk_percentage: Double = 1.0,
    val strategy_source: String = "",
    val confidence_score: Double = 0.0,
    val reasoning: String = "",
    val invalidation_condition: String = ""
) {
    val isValid: Boolean get() = symbol.isNotBlank() && action.isNotBlank() && entry_price > 0
}

sealed class WsEvent {
    data class Price(val symbol: String, val price: Double, val change: Double) : WsEvent()
    data class Trade(val payload: String) : WsEvent()
    data class Alert(val title: String, val body: String, val critical: Boolean = false) : WsEvent()
    data class BotStatus(val botId: String, val status: String) : WsEvent()
    data class CircuitBreaker(val active: Boolean, val resetIn: String) : WsEvent()
    data class ModelChanged(val model: String) : WsEvent()
    object Connected : WsEvent()
    object Disconnected : WsEvent()
}

data class AiResult(
    val text: String,
    val model: String,
    val tier: Int,
    val backendUsed: Boolean = false
)
