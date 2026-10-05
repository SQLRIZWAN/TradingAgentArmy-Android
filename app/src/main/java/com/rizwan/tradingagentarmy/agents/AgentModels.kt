package com.rizwan.tradingagentarmy.agents

import kotlinx.serialization.Serializable

enum class AgentRole(
    val id: String,
    val displayName: String,
    val emoji: String,
    val specialty: String
) {
    COORDINATOR("coordinator", "Commander", "👑", "Runs the round, keeps the team aligned"),
    SCOUT("scout", "Web Scout", "🔍", "Google/DDG search, live info"),
    NEWS("news", "News Analyst", "📰", "Global news & macro events"),
    SENTIMENT("sentiment", "Sentiment Analyst", "🎭", "Social sentiment & fear/greed"),
    TECHNICAL("technical", "Technical Analyst", "📐", "Indicators, structure, levels"),
    FUNDAMENTAL("fundamental", "On-Chain Analyst", "🧮", "On-chain flows & fundamentals"),
    CHART("chart", "Chart Analyst", "🕯️", "Candle-by-candle price action"),
    BULL("bull", "Bull Researcher", "🐂", "Argues the buy case"),
    BEAR("bear", "Bear Researcher", "🐻", "Argues the sell case"),
    TRADER("trader", "Trader", "💰", "Turns debate into an order plan"),
    RISK("risk", "Risk Manager", "🛡️", "Exposure, drawdown, kill-switch"),
    PM("pm", "Portfolio Manager", "🏆", "Final go / no-go"),
    HFT("hft", "HFT Scalper", "⚡", "High-frequency scalping engine"),
    APP_WATCH("appwatch", "App Watcher", "👁️", "Watches everything inside the app")
}

data class WarMessage(
    val id: Long = 0,
    val roundId: Long,
    val agent: AgentRole,
    val content: String,
    val kind: String = "talk",
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class TradePlan(
    val action: String = "HOLD",
    val symbol: String = "",
    val confidence: Int = 0,
    val entry: Double? = null,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val sizeUsd: Double? = null,
    val reasoning: String = ""
)
