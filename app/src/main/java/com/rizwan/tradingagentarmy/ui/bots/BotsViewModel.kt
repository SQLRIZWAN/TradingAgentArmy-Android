package com.rizwan.tradingagentarmy.ui.bots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizwan.tradingagentarmy.domain.model.Bot
import com.rizwan.tradingagentarmy.domain.model.BotStatus
import com.rizwan.tradingagentarmy.data.local.TradeDao
import com.rizwan.tradingagentarmy.domain.model.Trade
import com.rizwan.tradingagentarmy.data.repository.BotRepository
import com.rizwan.tradingagentarmy.ui.navigation.CommandBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BotsViewModel @Inject constructor(
    private val repo: BotRepository,
    private val bus: CommandBus,
    private val tradeDao: TradeDao
) : ViewModel() {

    val bots: StateFlow<List<Bot>> =
        repo.bots().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val trades: StateFlow<List<Trade>> = tradeDao.observeRecent().map { rows -> rows.map { row ->
        Trade(id=row.id, symbol=row.symbol, side=row.side, entry=row.entry, exit=row.exit, pnl=row.pnl,
            mode=row.mode, botName=row.botName, model=row.model, timestamp=row.timestamp, status=row.status,
            marketType=row.marketType, quantity=row.quantity, stopLoss=row.stopLoss, takeProfit=row.takeProfit,
            unrealizedPnl=row.unrealizedPnl, currentPrice=row.currentPrice)
    } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun offerCreateCommand() {
        bus.offer("Create a new trading bot for BTC/USDT. Strategy: RSI + EMA trend scalper")
    }

    fun setStatus(id: String, status: BotStatus) = viewModelScope.launch {
        repo.setStatus(id, status)
    }

    fun delete(id: String) = viewModelScope.launch { repo.remove(id) }
}
