package com.rizwan.tradingagentarmy.data.repository

import com.rizwan.tradingagentarmy.data.local.BotDao
import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.data.remote.BackendApiService
import com.rizwan.tradingagentarmy.data.remote.MarketApi
import com.rizwan.tradingagentarmy.domain.model.MarketTicker
import com.rizwan.tradingagentarmy.domain.model.PortfolioSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketRepository @Inject constructor(
    private val marketApi: MarketApi,
    private val backend: BackendApiService,
    private val botDao: BotDao,
    private val prefs: SecurePreferences
) {
    suspend fun watchlist(): List<MarketTicker> = coroutineScope {
        val crypto = async { marketApi.cryptoTickers() }
        val fx = async { marketApi.fxTickers() }
        val gold = async { marketApi.goldTicker() }
        crypto.await() + fx.await() + listOfNotNull(gold.await())
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
