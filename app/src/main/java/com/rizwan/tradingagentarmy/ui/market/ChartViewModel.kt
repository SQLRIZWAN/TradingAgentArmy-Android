package com.rizwan.tradingagentarmy.ui.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizwan.tradingagentarmy.data.remote.CandleBar
import com.rizwan.tradingagentarmy.data.remote.CandleSource
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

@HiltViewModel
class ChartViewModel @Inject constructor(
    private val bitget: BitgetClient
) : ViewModel() {

    private val _symbol = MutableStateFlow("BTC/USDT")
    val symbol: StateFlow<String> = _symbol.asStateFlow()

    private val _timeframe = MutableStateFlow("15m")
    val timeframe: StateFlow<String> = _timeframe.asStateFlow()

    private val _candles = MutableStateFlow<List<CandleBar>>(emptyList())
    val candles: StateFlow<List<CandleBar>> = _candles.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    private var poller: Job? = null

    fun open(symbol: String, timeframe: String) {
        _symbol.value = symbol
        _timeframe.value = timeframe
        _offline.value = false
        load()
    }

    fun setTimeframe(tf: String) {
        _timeframe.value = tf
        load()
    }

    fun useOfflineChart() {
        _offline.value = true
        load()
    }

    fun retry() {
        _offline.value = false
        _error.value = null
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            runCatching {
                withContext(Dispatchers.IO) {
                    CandleSource.fetch(_symbol.value, _timeframe.value, bitget)
                }
            }.onSuccess { bars ->
                _candles.value = bars
                if (bars.isEmpty()) _error.value = "Is symbol ke liye data nahi mila"
            }.onFailure {
                _error.value = "Data load fail: ${it.message?.take(80)}"
            }
            _loading.value = false
        }
    }

    /** Periodic refresh while the offline chart is on screen. */
    fun watchOffline() {
        poller?.cancel()
        poller = viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                if (_offline.value) load()
            }
        }
    }

    override fun onCleared() {
        poller?.cancel()
        super.onCleared()
    }

    companion object {
        /** App symbol → TradingView symbol id (their own feed/WS pipeline). */
        fun tvSymbol(appSymbol: String): String {
            val s = appSymbol.uppercase().replace(" ", "")
            val compact = s.replace("/", "")
            return when {
                compact == "COPPER" -> "COMEX:HG1!"
                compact.startsWith("XAU") -> "OANDA:XAUUSD"
                compact.startsWith("XAG") -> "OANDA:XAGUSD"
                compact.startsWith("XPT") -> "TVC:XPTUSD"
                compact.startsWith("XPD") -> "TVC:XPDUSD"
                compact.endsWith("USDT") -> "BINANCE:$compact"
                s.contains("/") && s.length == 7 -> {
                    val (b, q) = s.split("/")
                    if (b.length == 3 && q.length == 3) "FX_IDC:$b$q" else "BINANCE:$compact"
                }
                s.length == 6 && s.all { it.isLetter() } -> "FX_IDC:$s"
                else -> "BINANCE:$compact"
            }
        }

        fun tvInterval(timeframe: String): String = when (timeframe) {
            "1m" -> "1"
            "5m" -> "5"
            "15m" -> "15"
            "1h" -> "60"
            "4h" -> "240"
            "1D" -> "D"
            else -> "15"
        }
    }
}
