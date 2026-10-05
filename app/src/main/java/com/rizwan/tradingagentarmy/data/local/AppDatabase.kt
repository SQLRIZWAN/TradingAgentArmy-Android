package com.rizwan.tradingagentarmy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ChatMessageEntity::class, TradeEntity::class, BotEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun tradeDao(): TradeDao
    abstract fun botDao(): BotDao
}
