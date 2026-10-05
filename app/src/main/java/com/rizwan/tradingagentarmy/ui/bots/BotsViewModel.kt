package com.rizwan.tradingagentarmy.ui.bots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizwan.tradingagentarmy.domain.model.Bot
import com.rizwan.tradingagentarmy.domain.model.BotStatus
import com.rizwan.tradingagentarmy.data.repository.BotRepository
import com.rizwan.tradingagentarmy.ui.navigation.CommandBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BotsViewModel @Inject constructor(
    private val repo: BotRepository,
    private val bus: CommandBus
) : ViewModel() {

    val bots: StateFlow<List<Bot>> =
        repo.bots().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun offerCreateCommand() {
        bus.offer("Create a new trading bot for BTC/USDT. Strategy: RSI + EMA trend scalper")
    }

    fun setStatus(id: String, status: BotStatus) = viewModelScope.launch {
        repo.setStatus(id, status)
    }

    fun delete(id: String) = viewModelScope.launch { repo.remove(id) }
}
