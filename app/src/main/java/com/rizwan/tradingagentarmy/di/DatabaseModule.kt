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

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE trades ADD COLUMN clientOid TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE trades ADD COLUMN exchangeOrderId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE trades ADD COLUMN stopLoss REAL")
            db.execSQL("ALTER TABLE trades ADD COLUMN takeProfit REAL")
            db.execSQL("ALTER TABLE trades ADD COLUMN slOrderId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE trades ADD COLUMN tpOrderId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE trades ADD COLUMN status TEXT NOT NULL DEFAULT 'CLOSED'")
            db.execSQL("ALTER TABLE trades ADD COLUMN marketType TEXT NOT NULL DEFAULT 'SPOT'")
            db.execSQL("ALTER TABLE trades ADD COLUMN exitReason TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE trades ADD COLUMN quantity REAL NOT NULL DEFAULT 0")
        }
    }

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "trading-army.db")
            .addMigrations(MIGRATION_1_2)
            .addMigrations(MIGRATION_2_3)
            .addMigrations(MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun chatDao(db: AppDatabase): ChatDao = db.chatDao()
    @Provides fun tradeDao(db: AppDatabase): TradeDao = db.tradeDao()
    @Provides fun botDao(db: AppDatabase): BotDao = db.botDao()
    @Provides fun warDao(db: AppDatabase): WarDao = db.warDao()
}
