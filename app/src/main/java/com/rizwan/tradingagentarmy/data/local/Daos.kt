package com.rizwan.tradingagentarmy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY id ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY id DESC LIMIT 200")
    suspend fun recent(): List<ChatMessageEntity>

    @Insert
    suspend fun insert(entity: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET content = :content, model = :model WHERE id = :id")
    suspend fun updateContent(id: Long, content: String, model: String?)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun count(): Int
}

@Dao
interface TradeDao {
    @Query("SELECT * FROM trades ORDER BY timestamp DESC LIMIT 200")
    fun observeRecent(): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades ORDER BY timestamp DESC")
    suspend fun all(): List<TradeEntity>

    @Query("SELECT * FROM trades WHERE botName = :bot ORDER BY timestamp DESC")
    suspend fun byBot(bot: String): List<TradeEntity>

    @Query("SELECT SUM(pnl) FROM trades WHERE timestamp >= :since")
    suspend fun pnlSince(since: Long): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TradeEntity): Long

    @Query("SELECT * FROM trades WHERE status IN ('OPEN', 'PAPER_OPEN', 'EXIT_PENDING', 'UNKNOWN') ORDER BY timestamp ASC")
    suspend fun openTrades(): List<TradeEntity>

    @Query("SELECT * FROM trades WHERE clientOid = :clientOid LIMIT 1")
    suspend fun byClientOid(clientOid: String): TradeEntity?

    @Query("SELECT * FROM trades WHERE positionId = :positionId LIMIT 1")
    suspend fun byPositionId(positionId: String): TradeEntity?

    @Query("SELECT * FROM trades WHERE status IN ('OPEN', 'PAPER_OPEN', 'EXIT_PENDING', 'UNKNOWN') AND symbol = :symbol ORDER BY timestamp ASC")
    suspend fun openBySymbol(symbol: String): List<TradeEntity>

    @Query("UPDATE trades SET exchangeOrderId = :orderId, slOrderId = :slOrderId, tpOrderId = :tpOrderId, status = :status WHERE id = :id")
    suspend fun updateExchangeProtection(id: Long, orderId: String, slOrderId: String, tpOrderId: String, status: String)

    @Query("UPDATE trades SET positionId = :positionId, entry = :entry, actualEntry = :actualEntry, quantity = :quantity, filledQuantity = :filledQuantity, stopLoss = :stopLoss, takeProfit = :takeProfit, protectionStatus = :protectionStatus, lastExchangeSync = :syncAt, currentPrice = :currentPrice, unrealizedPnl = :unrealizedPnl, pnl = :unrealizedPnl, status = :status WHERE id = :id")
    suspend fun updateExchangeState(id: Long, positionId: String, entry: Double, actualEntry: Double?, quantity: Double, filledQuantity: Double, stopLoss: Double?, takeProfit: Double?, protectionStatus: String, syncAt: Long, currentPrice: Double, unrealizedPnl: Double, status: String)

    @Query("UPDATE trades SET status = 'UNKNOWN', lastExchangeSync = :syncAt WHERE status = 'EXIT_PENDING'")
    suspend fun markPendingUnknown(syncAt: Long)

    @Query("UPDATE trades SET exit = :exit, pnl = :pnl, status = 'CLOSED', exitReason = :reason WHERE id = :id")
    suspend fun closeTrade(id: Long, exit: Double, pnl: Double, reason: String)

    @Query("UPDATE trades SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)
}

@Dao
interface BotDao {
    @Query("SELECT * FROM bots ORDER BY createdAt DESC LIMIT 100")
    suspend fun all(): List<BotEntity>

    @Query("SELECT * FROM bots ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BotEntity>>

    @Query("SELECT * FROM bots WHERE id = :id")
    suspend fun byId(id: String): BotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BotEntity)

    @Query("UPDATE bots SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("DELETE FROM bots WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM bots WHERE status = :status")
    suspend fun countByStatus(status: String): Int
}
