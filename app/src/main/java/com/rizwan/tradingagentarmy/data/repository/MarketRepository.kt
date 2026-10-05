package com.rizwan.tradingagentarmy.data.repository

import com.rizwan.tradingagentarmy.data.local.BotDao
import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.data.remote.BackendApiService
import com.rizwan.tradingagentarmy.data.remote.MarketApi
import com.rizwan.tradingagentarmy.domain.model.MarketTicker
import com.rizwan.tradingagentarmy.domain.model.PortfolioSummary
import com.rizwan.tradingagentarmy.trading.BitgetClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketRepository @Inject constructor(
    private val marketApi: MarketApi,
    private val backend: BackendApiService,
    private val botDao: BotDao,
    private val prefs: SecurePreferences,
    private val bitget: BitgetClient
) {
    suspend fun watchlist(): List<MarketTicker> = coroutineScope {
        val crypto = async { marketApi.cryptoTickers() }
        val cfd = async { cfdWatchlist() }
        crypto.await() + cfd.await()
    }

    private suspend fun cfdWatchlist(): List<MarketTicker> {
        val symbols = listOf("XAUUSD", "XAGUSD", "EURUSD", "GBPUSD", "USDJPY")
        if (!bitget.configured) {
            return marketApi.fxTickers() + listOfNotNull(marketApi.goldTicker())
        }
        return symbols.mapNotNull { symbol ->
            bitget.cfdQuote(symbol).getOrNull()?.let { q ->
                val mid = (q.bid + q.ask) / 2.0
                MarketTicker(symbol, mid, 0.0, if (symbol.contains("USD") && !symbol.startsWith("XAU") && !symbol.startsWith("XAG")) 5 else 2)
            }
        }
    }

    suspend fun portfolio(todayPnlLocal: Double, totalPnlLocal: Double): PortfolioSummary {
        if (prefs.backendUrl.isNotBlank()) {
            runCatching {
                val auth = bearer()
                val p = backend.portfolio(u("api/portfolio"), auth)
                return PortfolioSummary(p.todayPnl, p.totalPnl, p.activeBots, 0, 0, fromBackend = true)
            }
        }
        return PortfolioSummary(
            todayPnl = todayPnlLocal,
            totalPnl = totalPnlLocal,
            activeRunning = botDao.countByStatus("RUNNING"),
            activeDemo = botDao.countByStatus("DEMO"),
            stopped = botDao.countByStatus("STOPPED") + botDao.countByStatus("PENDING"),
            fromBackend = false
        )
    }

    suspend fun backendAlive(): Boolean {
        if (prefs.backendUrl.isBlank()) return false
        return runCatching {
            backend.health(u("health"), bearer())
            true
        }.getOrDefault(false)
    }

    private fun bearer(): String? =
        if (prefs.bearerToken.isBlank()) null else "Bearer ${prefs.bearerToken}"

    private fun u(path: String) = prefs.backendUrl.trimEnd('/') + "/" + path
}
