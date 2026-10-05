package com.rizwan.tradingagentarmy.data.repository

import com.rizwan.tradingagentarmy.data.firebase.FirebaseSync
import com.rizwan.tradingagentarmy.data.local.BotDao
import com.rizwan.tradingagentarmy.data.local.BotEntity
import com.rizwan.tradingagentarmy.data.local.TradeDao
import com.rizwan.tradingagentarmy.data.local.TradeEntity
import com.rizwan.tradingagentarmy.data.remote.BackendApiService
import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.domain.model.Bot
import com.rizwan.tradingagentarmy.domain.model.BotStatus
import com.rizwan.tradingagentarmy.domain.model.Trade
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BotRepository @Inject constructor(
    private val botDao: BotDao,
    private val tradeDao: TradeDao,
    private val backend: BackendApiService,
    private val prefs: SecurePreferences,
    private val firebase: FirebaseSync
) {
    fun bots(): Flow<List<Bot>> = botDao.observeAll().map { it.map(::mapBot) }

    suspend fun bot(id: String): Bot? = botDao.byId(id)?.let(::mapBot)

    suspend fun tradesOf(botName: String): List<Trade> = tradeDao.byBot(botName).map(::mapTrade)

    fun recentTrades(): Flow<List<Trade>> = tradeDao.observeRecent().map { it.map(::mapTrade) }

    suspend fun create(
        name: String,
        market: String,
        strategy: String
    ): Bot {
        val entity = BotEntity(
            id = UUID.randomUUID().toString().take(8),
            name = name,
            market = market,
            strategy = strategy,
            status = "PENDING",
            pnlToday = 0.0,
            pnlTotal = 0.0,
            gate1 = false, gate2 = false, gate3 = false,
            version = 1,
            createdAt = System.currentTimeMillis()
        )
        botDao.upsert(entity)
        firebase.pushBot(entity)
        syncToBackend(entity)
        return mapBot(entity)
    }

    suspend fun setStatus(id: String, status: BotStatus) {
        botDao.setStatus(id, status.name)
        botDao.byId(id)?.let { firebase.pushBot(it) }
        runCatching {
            if (status == BotStatus.RUNNING && prefs.backendUrl.isNotBlank()) {
                val auth = if (prefs.bearerToken.isBlank()) null else "Bearer ${prefs.bearerToken}"
                backend.deployBot(prefs.backendUrl.trimEnd('/') + "/api/bots/$id/deploy", auth)
            }
        }
    }

    suspend fun promoteGates(id: String, g1: Boolean, g2: Boolean, g3: Boolean) {
        val bot = botDao.byId(id) ?: return
        botDao.upsert(bot.copy(gate1 = g1, gate2 = g2, gate3 = g3))
        botDao.byId(id)?.let { firebase.pushBot(it) }
    }

    suspend fun recordTrade(
        symbol: String, side: String, entry: Double, exit: Double?, pnl: Double,
        mode: String, botName: String, model: String?
    ): TradeEntity {
        val e = TradeEntity(
            symbol = symbol, side = side, entry = entry, exit = exit, pnl = pnl,
            mode = mode, botName = botName, model = model, timestamp = System.currentTimeMillis()
        )
        tradeDao.insert(e)
        firebase.pushTrade(e)
        return e
    }

    suspend fun allTrades(): List<Trade> = tradeDao.all().map(::mapTrade)

    suspend fun pnlSince(since: Long): Double = tradeDao.pnlSince(since) ?: 0.0

    suspend fun remove(id: String) = botDao.delete(id)

    private suspend fun syncToBackend(entity: BotEntity) {
        runCatching {
            if (prefs.backendUrl.isBlank()) return
            val json = kotlinx.serialization.json.buildJsonObject {
                put("id", entity.id)
                put("name", entity.name)
                put("market", entity.market)
                put("strategy", entity.strategy)
            }.toString()
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder()
                .url("${prefs.backendUrl.trimEnd('/')}/api/bots")
                .post(json.toRequestBody())
                .apply {
                    if (prefs.bearerToken.isNotBlank()) header("Authorization", "Bearer ${prefs.bearerToken}")
                }
                .build()
            client.newCall(req).execute().close()
        }
    }

    private fun String.toRequestBody() = toRequestBody("application/json".toMediaType())

    private fun mapBot(e: BotEntity) = Bot(
        id = e.id, name = e.name, market = e.market, strategy = e.strategy,
        status = runCatching { BotStatus.valueOf(e.status) }.getOrDefault(BotStatus.PENDING),
        pnlToday = e.pnlToday, pnlTotal = e.pnlTotal,
        gate1 = e.gate1, gate2 = e.gate2, gate3 = e.gate3,
        version = e.version, createdAt = e.createdAt
    )

    private fun mapTrade(e: TradeEntity) = Trade(
        id = e.id, symbol = e.symbol, side = e.side, entry = e.entry, exit = e.exit,
        pnl = e.pnl, mode = e.mode, botName = e.botName, model = e.model, timestamp = e.timestamp,
        clientOid = e.clientOid, exchangeOrderId = e.exchangeOrderId,
        stopLoss = e.stopLoss, takeProfit = e.takeProfit,
        slOrderId = e.slOrderId, tpOrderId = e.tpOrderId,
        status = e.status, marketType = e.marketType, exitReason = e.exitReason,
        quantity = e.quantity
    )
}
