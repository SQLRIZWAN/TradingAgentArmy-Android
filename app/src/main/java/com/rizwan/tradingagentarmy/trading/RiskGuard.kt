package com.rizwan.tradingagentarmy.trading

import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.data.local.TradeDao
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RiskGuard @Inject constructor(
    private val prefs: SecurePreferences,
    private val tradeDao: TradeDao
) {
    var killSwitch: Boolean
        get() = prefs.getBool("risk_kill", false)
        set(v) = prefs.putBool("risk_kill", v)

    var maxDailyLossUsd: Double
        get() = prefs.getString("risk_max_daily_loss", "50").toDoubleOrNull() ?: 50.0
        set(v) = prefs.putString("risk_max_daily_loss", v.toString())

    var maxTradeUsd: Double
        get() = prefs.getString("risk_max_trade", "25").toDoubleOrNull() ?: 25.0
        set(v) = prefs.putString("risk_max_trade", v.toString())

    suspend fun todayPnl(): Double {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return tradeDao.pnlSince(cal.timeInMillis) ?: 0.0
    }

    /** Returns null when the trade is allowed, otherwise the rejection reason. */
    suspend fun evaluate(proposedUsd: Double): String? {
        if (killSwitch) return "KILL SWITCH is ON — all trading blocked"
        val pnl = todayPnl()
        if (pnl <= -maxDailyLossUsd)
            return "Daily loss limit hit (${"%.2f".format(pnl)} / -${"%.2f".format(maxDailyLossUsd)}) — trading halted for today"
        val recent = tradeDao.all().take(5)
        if (recent.size >= 3 && recent.all { it.pnl < 0 })
            return "3+ consecutive losses — pausing new entries"
        return null
    }

    fun capSize(proposedUsd: Double): Double = proposedUsd.coerceAtMost(maxTradeUsd)
}
