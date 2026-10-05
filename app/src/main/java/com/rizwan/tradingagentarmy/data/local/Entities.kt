package com.rizwan.tradingagentarmy.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val model: String?,
    val timestamp: Long,
    val sessionId: String = "default"
)

@Entity(tableName = "trades")
data class TradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val side: String,
    val entry: Double,
    val exit: Double?,
    val pnl: Double,
    val mode: String,
    val botName: String,
    val model: String?,
    val timestamp: Long
)

@Entity(tableName = "bots")
data class BotEntity(
    @PrimaryKey val id: String,
    val name: String,
    val market: String,
    val strategy: String,
    val status: String,
    val pnlToday: Double,
    val pnlTotal: Double,
    val gate1: Boolean,
    val gate2: Boolean,
    val gate3: Boolean,
    val version: Int,
    val createdAt: Long
)
