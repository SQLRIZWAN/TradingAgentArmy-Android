package com.rizwan.tradingagentarmy.trading

import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.data.local.TradeDao
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Execution guard. Default profile is a high-frequency SCALPING profile:
 * unlimited small trades, tiny per-trade target, only the manual kill switch
 * and the (configurable) daily loss ceiling can stop the army.
 */
@Singleton
class RiskGuard @Inject constructor(
    private val prefs: SecurePreferences,
    private val tradeDao: TradeDao
) {
    var killSwitch: Boolean
        get() = prefs.getBool("risk_kill", false)
        set(v) = prefs.putBool("risk_kill", v)

    var maxDailyLossUsd: Double
        get() = prefs.getString("risk_max_daily_loss", "10000").toDoubleOrNull() ?: 10000.0
        set(v) = prefs.putString("risk_max_daily_loss", v.toString())

    var maxTradeUsd: Double
        get() = prefs.getString("risk_max_trade", "1000").toDoubleOrNull() ?: 1000.0
        set(v) = prefs.putString("risk_max_trade", v.toString())

    /** Max entries per day — 20000 = essentially unlimited scalping. */
    var maxTradesPerDay: Int
        get() = prefs.getInt("risk_max_trades_day", 20000)
        set(v) = prefs.putInt("risk_max_trades_day", v)

    /** Legacy "3 losses in a row → pause" rule. OFF by default. */
    var blockOnLosingStreak: Boolean
        get() = prefs.getBool("risk_block_consec", false)
        set(v) = prefs.putBool("risk_block_consec", v)

    /** Scalp target in USD per closed trade (≈$1 × 20000 trades = $20k/day goal). */
    var scalpTargetUsd: Double
        get() = prefs.getString("scalp_target_usd", "1").toDoubleOrNull() ?: 1.0
        set(v) = prefs.putString("scalp_target_usd", v.toString())

    var scalpTpPct: Double
        get() = prefs.getString("scalp_tp_pct", "0.10").toDoubleOrNull() ?: 0.10
        set(v) = prefs.putString("scalp_tp_pct", v.toString())

    var scalpSlPct: Double
        get() = prefs.getString("scalp_sl_pct", "0.10").toDoubleOrNull() ?: 0.10
        set(v) = prefs.putString("scalp_sl_pct", v.toString())

    suspend fun todayPnl(): Double {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return tradeDao.pnlSince(cal.timeInMillis) ?: 0.0
    }

    suspend fun todayTradeCount(): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return tradeDao.all().count { it.timestamp >= cal.timeInMillis }
    }

    /** Returns null when the trade is allowed, otherwise the rejection reason. */
    suspend fun evaluate(proposedUsd: Double, live: Boolean = true): String? {
        if (killSwitch) return "KILL SWITCH is ON — all trading blocked"
        // Paper trades are always allowed: naya strategy test karne par koi rok nahi.
        if (!live) return null
        val pnl = todayPnl()
        if (pnl <= -maxDailyLossUsd)
            return "Daily loss limit hit (${"%.2f".format(pnl)} / -${"%.2f".format(maxDailyLossUsd)}) — trading halted for today"
        val count = todayTradeCount()
        if (count >= maxTradesPerDay)
            return "Daily trade cap reached ($count / $maxTradesPerDay)"
        if (blockOnLosingStreak) {
            val recent = tradeDao.all().take(5)
            if (recent.size >= 3 && recent.all { it.pnl < 0 })
                return "3+ consecutive losses — pausing new entries (Settings se off kar sakte hain)"
        }
        return null
    }

    fun capSize(proposedUsd: Double): Double = proposedUsd.coerceAtMost(maxTradeUsd)
}
