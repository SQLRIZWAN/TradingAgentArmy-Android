package com.rizwan.tradingagentarmy.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "war_messages")
data class WarMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roundId: Long,
    val agentId: String,
    val agentName: String,
    val emoji: String,
    val content: String,
    val kind: String = "talk",
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface WarDao {
    @Query("SELECT * FROM war_messages ORDER BY id DESC LIMIT 300")
    fun observeRecent(): Flow<List<WarMessageEntity>>

    @Query("SELECT * FROM war_messages ORDER BY id DESC LIMIT 60")
    suspend fun recent(): List<WarMessageEntity>

    @Query("SELECT * FROM war_messages WHERE roundId = :r ORDER BY id ASC")
    suspend fun byRound(r: Long): List<WarMessageEntity>

    @Query("SELECT * FROM war_messages WHERE kind = 'decision' ORDER BY id DESC LIMIT 10")
    suspend fun decisions(): List<WarMessageEntity>

    @Insert
    suspend fun insert(m: WarMessageEntity): Long

    @Query("DELETE FROM war_messages WHERE id NOT IN (SELECT id FROM war_messages ORDER BY id DESC LIMIT 1000)")
    suspend fun trim()

    @Query("DELETE FROM war_messages")
    suspend fun clear()
}
