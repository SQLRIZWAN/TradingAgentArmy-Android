package com.rizwan.tradingagentarmy.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rizwan.tradingagentarmy.data.local.AppDatabase
import com.rizwan.tradingagentarmy.data.local.BotDao
import com.rizwan.tradingagentarmy.data.local.ChatDao
import com.rizwan.tradingagentarmy.data.local.TradeDao
import com.rizwan.tradingagentarmy.data.local.WarDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `war_messages` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`roundId` INTEGER NOT NULL, `agentId` TEXT NOT NULL, " +
                    "`agentName` TEXT NOT NULL, `emoji` TEXT NOT NULL, " +
                    "`content` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL)"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "trading-army.db")
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun chatDao(db: AppDatabase): ChatDao = db.chatDao()
    @Provides fun tradeDao(db: AppDatabase): TradeDao = db.tradeDao()
    @Provides fun botDao(db: AppDatabase): BotDao = db.botDao()
    @Provides fun warDao(db: AppDatabase): WarDao = db.warDao()
}
