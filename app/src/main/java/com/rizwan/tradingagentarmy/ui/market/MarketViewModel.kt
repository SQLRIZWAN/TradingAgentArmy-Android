package com.rizwan.tradingagentarmy.ui.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizwan.tradingagentarmy.data.remote.MarketApi
import com.rizwan.tradingagentarmy.domain.model.MarketTicker
import com.rizwan.tradingagentarmy.trading.BitgetClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class MarketTab(val label: String) {
    CRYPTO("Crypto"), FOREX("Forex"), METAL("Metal")
}

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val api: MarketApi,
    private val bitget: BitgetClient
) : ViewModel() {

    private val _tab = MutableStateFlow(MarketTab.CRYPTO)
    val tab: StateFlow<MarketTab> = _tab.asStateFlow()

    private val _coins = MutableStateFlow<List<MarketApi.CoinRow>>(emptyList())
    val coins: StateFlow<List<MarketApi.CoinRow>> = _coins.asStateFlow()

    private val _forex = MutableStateFlow<List<MarketTicker>>(emptyList())
    val forex: StateFlow<List<MarketTicker>> = _forex.asStateFlow()

    private val _metals = MutableStateFlow<List<MarketTicker>>(emptyList())
    val metals: StateFlow<List<MarketTicker>> = _metals.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _lastSync = MutableStateFlow(0L)
    val lastSync: StateFlow<Long> = _lastSync.asStateFlow()

    private val _selected = MutableStateFlow("BTC/USDT")
    val selected: StateFlow<String> = _selected.asStateFlow()

    private var refresher: Job? = null

    init {
        load(_tab.value)
        refresher = viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                load(_tab.value, silent = true)
            }
        }
    }

    fun selectTab(tab: MarketTab) {
        if (_tab.value == tab) return
        _tab.value = tab
        load(tab)
    }

    fun select(symbol: String) {
        _selected.value = symbol
    }

    fun refresh() = load(_tab.value)

    private fun load(tab: MarketTab, silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _loading.value = true
            val failure = runCatching {
                withContext(Dispatchers.IO) {
                    when (tab) {
                        MarketTab.CRYPTO -> {
                            val rows = api.topCoins(100)
                            if (rows.isNotEmpty()) _coins.value = rows
                            if (_coins.value.isEmpty()) error("Coin list nahi mili")
                        }
                        MarketTab.FOREX -> {
                            if (bitget.configured) {
                                val cfd = loadCfdForex()
                                if (cfd.isNotEmpty()) _forex.value = cfd
                            }
                            if (_forex.value.isEmpty()) {
                                val rows = api.forexTickersFull()
                                if (rows.isNotEmpty()) _forex.value = rows else error("FX rates nahi mili")
                            } else Unit
                        }
                        MarketTab.METAL -> {
                            val rows = api.metalTickers()
                            if (rows.isNotEmpty()) _metals.value = rows else error("Metal rates nahi mili")
                        }
                    }
                }
            }.exceptionOrNull()
            _error.value = failure?.message?.take(90)
            _lastSync.value = System.currentTimeMillis()
            _loading.value = false
        }
    }

    private suspend fun loadCfdForex(): List<MarketTicker> {
        val symbols = listOf(
            "EURUSD", "GBPUSD", "USDJPY", "USDCHF", "AUDUSD", "USDCAD", "NZDUSD",
            "EURGBP", "EURJPY", "GBPJPY"
        )
        return symbols.mapNotNull { s ->
            bitget.cfdQuote(s).getOrNull()?.let { q ->
                val mid = (q.bid + q.ask) / 2.0
                MarketTicker("${s.substring(0, 3)}/${s.substring(3)}", mid, 0.0, if (mid < 10) 5 else 2)
            }
        }
    }

    override fun onCleared() {
        refresher?.cancel()
        super.onCleared()
    }
}
