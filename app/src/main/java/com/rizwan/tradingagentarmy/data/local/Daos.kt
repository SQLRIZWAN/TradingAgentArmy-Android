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
}

@Dao
interface BotDao {
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
