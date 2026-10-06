package com.rizwan.tradingagentarmy.ui.fleet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.data.repository.BotRepository
import com.rizwan.tradingagentarmy.domain.model.Bot
import com.rizwan.tradingagentarmy.domain.model.BotStatus
import com.rizwan.tradingagentarmy.domain.model.Trade
import com.rizwan.tradingagentarmy.trading.HftEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FleetTab { BOTS, FUTURES, FOREX }
enum class BotFilter { ALL, FUTURE_BOT, FOREX_BOT }

@HiltViewModel
class FleetViewModel @Inject constructor(
    private val botRepo: BotRepository,
    private val prefs: SecurePreferences,
    private val hft: HftEngine
) : ViewModel() {

    val bots: StateFlow<List<Bot>> =
        botRepo.bots().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentTrades: StateFlow<List<Trade>> =
        botRepo.recentTrades().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val tab = MutableStateFlow(FleetTab.BOTS)
    val botFilter = MutableStateFlow(BotFilter.ALL)

    val hftActive = hft.active
    val hftSignal = hft.lastSignal
    val hftPosition = hft.position

    private val _scalpOn = MutableStateFlow(prefs.getBool("scalp_enabled", prefs.getBool("hft_enabled", false)))
    val scalpOn: StateFlow<Boolean> = _scalpOn

    val filteredBots: StateFlow<List<Bot>> =
        combine(bots, botFilter) { list, filter ->
            when (filter) {
                BotFilter.ALL -> list
                BotFilter.FUTURE_BOT -> list.filter {
                    it.market.contains("FUT", true) || it.market.contains("PERP", true) ||
                        it.market.contains("USD", true) && it.market.contains("USDT", true)
                }
                BotFilter.FOREX_BOT -> list.filter {
                    it.market.contains("FOREX", true) || it.market.contains("XAU", true) ||
                        it.market.contains("CFD", true) || it.market.contains("GOLD", true) ||
                        it.strategy.contains("forex", true) || it.strategy.contains("gold", true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val futuresTrades: StateFlow<List<Trade>> =
        recentTrades.map { list -> list.filter { it.marketType.equals("FUTURES", true) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val forexTrades: StateFlow<List<Trade>> =
        recentTrades.map { list -> list.filter { it.marketType.equals("CFD", true) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setTab(t: FleetTab) { tab.value = t }
    fun setBotFilter(f: BotFilter) { botFilter.value = f }

    fun setStatus(id: String, status: BotStatus) = viewModelScope.launch { botRepo.setStatus(id, status) }
    fun delete(id: String) = viewModelScope.launch { botRepo.remove(id) }

    fun toggleScalp(on: Boolean) {
        prefs.putBool("scalp_enabled", on); prefs.putBool("hft_enabled", on); _scalpOn.value = on
        if (on) hft.start() else hft.stop()
    }
}
