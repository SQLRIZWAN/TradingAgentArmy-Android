package com.rizwan.tradingagentarmy.di

import android.content.Context
import androidx.room.Room
import com.rizwan.tradingagentarmy.data.local.AppDatabase
import com.rizwan.tradingagentarmy.data.local.BotDao
import com.rizwan.tradingagentarmy.data.local.ChatDao
import com.rizwan.tradingagentarmy.data.local.TradeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "trading-army.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun chatDao(db: AppDatabase): ChatDao = db.chatDao()
    @Provides fun tradeDao(db: AppDatabase): TradeDao = db.tradeDao()
    @Provides fun botDao(db: AppDatabase): BotDao = db.botDao()
}
