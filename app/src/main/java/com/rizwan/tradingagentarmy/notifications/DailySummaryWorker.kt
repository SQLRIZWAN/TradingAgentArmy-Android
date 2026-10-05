package com.rizwan.tradingagentarmy.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rizwan.tradingagentarmy.data.local.AppDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.ZoneId

@HiltWorker
class DailySummaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val db: AppDatabase
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val todayPnl = db.tradeDao().pnlSince(start) ?: 0.0
            val trades = db.tradeDao().all().count { it.timestamp >= start }
            Notifier.dailySummary(
                applicationContext,
                "Today P&L: ${"%.2f".format(todayPnl)} | $trades trades logged"
            )
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
