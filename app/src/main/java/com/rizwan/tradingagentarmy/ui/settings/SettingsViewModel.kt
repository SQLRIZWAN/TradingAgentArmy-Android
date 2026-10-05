package com.rizwan.tradingagentarmy.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizwan.tradingagentarmy.App
import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.data.remote.AiProviders
import com.rizwan.tradingagentarmy.data.remote.WebSocketManager
import com.rizwan.tradingagentarmy.data.repository.MarketRepository
import com.rizwan.tradingagentarmy.ui.theme.ThemeController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

data class SettingsUi(
    val bitgetKey: String = "",
    val bitgetSecret: String = "",
    val bitgetPassphrase: String = "",
    val binanceKey: String = "",
    val binanceSecret: String = "",
    val bybitKey: String = "",
    val bybitSecret: String = "",
    val mt5Login: String = "",
    val mt5Password: String = "",
    val mt5Server: String = "",
    val mt5BridgeUrl: String = "",
    val marketType: String = "SPOT",
    val futuresProduct: String = "USDT-FUTURES",
    val futuresMargin: String = "isolated",
    val geminiKey: String = "",
    val geminiModel: String = "gemini-2.5-flash",
    val openaiKey: String = "",
    val openaiModel: String = "gpt-4o",
    val anthropicKey: String = "",
    val anthropicModel: String = "claude-sonnet-4-5",
    val deepseekKey: String = "",
    val deepseekModel: String = "deepseek-chat",
    val ollamaUrl: String = "http://10.0.2.2:11434",
    val ollamaModel: String = "llama3:8b",
    val fallbackChain: List<String> = listOf(),
    val autoChain: List<String> = listOf(),
    val geminiModels: List<String> = SettingsViewModel.GEMINI_MODELS,
    val deepseekModels: List<String> = SettingsViewModel.DEEPSEEK_MODELS,
    val ollamaModels: List<String> = SettingsViewModel.OLLAMA_MODELS,
    val backendUrl: String = "",
    val wsUrl: String = "",
    val bearerToken: String = "",
    val themeMode: String = "light",
    val localModelPath: String = "",
    val localModelEnabled: Boolean = false,
    val armySymbol: String = "BTCUSDT",
    val agentsEnabled: Boolean = true,
    val roundMinutes: Int = 15,
    val hftEnabled: Boolean = false,
    val liveTrading: Boolean = false,
    val notifyTrades: Boolean = true,
    val notifyCrash: Boolean = true,
    val notifyDaily: Boolean = true,
    val notifyCircuit: Boolean = true,
    val dailyHour: Int = 20,
    val refreshInterval: Int = 10
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SecurePreferences,
    private val ai: AiProviders,
    private val marketRepo: MarketRepository,
    private val ws: WebSocketManager,
    private val bitgetClient: com.rizwan.tradingagentarmy.trading.BitgetClient
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUi())
    val state: StateFlow<SettingsUi> = _state.asStateFlow()

    private val _tests = MutableStateFlow<Map<String, String>>(emptyMap())
    val tests: StateFlow<Map<String, String>> = _tests.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _showAbout = MutableStateFlow(false)
    val showAbout: StateFlow<Boolean> = _showAbout.asStateFlow()

    init {
        _state.value = SettingsUi(
            bitgetKey = prefs.bitgetKey,
            bitgetSecret = prefs.bitgetSecret,
            bitgetPassphrase = prefs.bitgetPassphrase,
            binanceKey = prefs.binanceKey,
            binanceSecret = prefs.binanceSecret,
            bybitKey = prefs.bybitKey,
            bybitSecret = prefs.bybitSecret,
            mt5Login = prefs.mt5Login,
            mt5Password = prefs.mt5Password,
            mt5Server = prefs.mt5Server,
            mt5BridgeUrl = prefs.mt5BridgeUrl,
            marketType = prefs.getString("bitget_market_type", "SPOT"),
            futuresProduct = prefs.getString("bitget_product_type", "USDT-FUTURES"),
            futuresMargin = prefs.getString("bitget_margin_mode", "isolated"),
            geminiKey = prefs.geminiKey,
            geminiModel = prefs.geminiModel,
            openaiKey = prefs.openaiKey,
            openaiModel = prefs.openaiModel,
            anthropicKey = prefs.anthropicKey,
            anthropicModel = prefs.anthropicModel,
            deepseekKey = prefs.deepseekKey,
            deepseekModel = prefs.deepseekModel,
            ollamaUrl = prefs.ollamaUrl,
            ollamaModel = prefs.ollamaModel,
            fallbackChain = prefs.fallbackChain.split(",").filter { it.isNotBlank() },
            backendUrl = prefs.backendUrl,
            wsUrl = prefs.wsUrl,
            bearerToken = prefs.bearerToken,
            themeMode = prefs.themeMode,
            localModelPath = prefs.localModelPath,
            localModelEnabled = prefs.localModelEnabled,
            armySymbol = prefs.getString("army_symbol", "BTCUSDT"),
            agentsEnabled = prefs.getBool("agents_enabled", true),
            roundMinutes = prefs.getInt("army_round_minutes", 15),
            hftEnabled = prefs.getBool("hft_enabled", false),
            liveTrading = prefs.getBool("live_trading", false),
            notifyTrades = prefs.notifyTrades,
            notifyCrash = prefs.notifyCrash,
            notifyDaily = prefs.notifyDaily,
            notifyCircuit = prefs.notifyCircuit,
            dailyHour = prefs.dailyHour,
            refreshInterval = prefs.refreshInterval
        )
        _state.value = _state.value.copy(
            autoChain = computeAutoChain(),
            fallbackChain = _state.value.fallbackChain
        )
        refreshGeminiModels()
        refreshOllamaModels()
    }

    /** Auto chain: jo bhi keys configured hain, unka preferred order. */
    fun computeAutoChain(): List<String> {
        val s = _state.value
        val list = mutableListOf<String>()
        if (s.geminiKey.isNotBlank() && s.geminiModel.isNotBlank()) list += s.geminiModel
        if (s.deepseekKey.isNotBlank()) list += s.deepseekModel.ifBlank { "deepseek-chat" }
        if (s.openaiKey.isNotBlank() && s.openaiModel.isNotBlank()) list += s.openaiModel
        if (s.anthropicKey.isNotBlank() && s.anthropicModel.isNotBlank()) list += s.anthropicModel
        return list
    }

    private var autoSaveJob: kotlinx.coroutines.Job? = null

    /** Debounced auto-save: key/type karte hi 700ms me save ho jata hai. */
    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(700)
            save(silent = true)
        }
    }

    fun update(transform: (SettingsUi) -> SettingsUi) {
        val next = transform(_state.value)
        val auto = computeAutoChainFrom(next)
        val chain = if (next.fallbackChain.isEmpty()) auto else next.fallbackChain
        _state.value = next.copy(autoChain = auto, fallbackChain = chain)
        _saved.value = false
        scheduleAutoSave()
    }

    private fun computeAutoChainFrom(s: SettingsUi): List<String> {
        val list = mutableListOf<String>()
        if (s.geminiKey.isNotBlank() && s.geminiModel.isNotBlank()) list += s.geminiModel
        if (s.deepseekKey.isNotBlank()) list += s.deepseekModel.ifBlank { "deepseek-chat" }
        if (s.openaiKey.isNotBlank() && s.openaiModel.isNotBlank()) list += s.openaiModel
        if (s.anthropicKey.isNotBlank() && s.anthropicModel.isNotBlank()) list += s.anthropicModel
        return list
    }

    fun moveChain(index: Int, up: Boolean) {
        val list = _state.value.fallbackChain.toMutableList()
        val target = if (up) index - 1 else index + 1
        if (target !in list.indices) return
        val item = list.removeAt(index)
        list.add(target, item)
        update { it.copy(fallbackChain = list) }
    }

    fun refreshGeminiModels() {
        if (_state.value.geminiKey.isBlank()) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models?key=${_state.value.geminiKey}"
                    val client = okhttp3.OkHttpClient()
                    val resp = client.newCall(okhttp3.Request.Builder().url(url).build()).execute()
                    resp.use {
                        if (it.isSuccessful) {
                            val body = it.body!!.string()
                            val names = Regex("\"name\":\\s*\"models/([^\"]+)\"")
                                .findAll(body).map { m -> m.groupValues[1] }.toList()
                            if (names.isNotEmpty()) {
                                _state.value = _state.value.copy(geminiModels = names)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun refreshOllamaModels() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val t = ai.testOllama()
                    val url = prefs.ollamaUrl.trimEnd('/') + "/api/tags"
                    val resp = okhttp3.OkHttpClient().newCall(okhttp3.Request.Builder().url(url).build()).execute()
                    resp.use {
                        if (it.isSuccessful) {
                            val names = Regex("\"name\":\\s*\"([^\"]+)\"")
                                .findAll(it.body!!.string()).map { m -> m.groupValues[1] }.toList()
                            if (names.isNotEmpty()) _state.value = _state.value.copy(ollamaModels = names)
                        }
                    }
                    t
                }
            }
        }
    }

    fun save(silent: Boolean = false) {
        val s = _state.value
        val geminiChanged = prefs.geminiKey != s.geminiKey.trim()
        val wsChanged = prefs.backendUrl != s.backendUrl ||
            prefs.wsUrl != s.wsUrl ||
            prefs.bearerToken != s.bearerToken.trim()
        prefs.bitgetKey = s.bitgetKey.trim()
        prefs.bitgetSecret = s.bitgetSecret.trim()
        prefs.bitgetPassphrase = s.bitgetPassphrase.trim()
        prefs.binanceKey = s.binanceKey.trim()
        prefs.binanceSecret = s.binanceSecret.trim()
        prefs.bybitKey = s.bybitKey.trim()
        prefs.bybitSecret = s.bybitSecret.trim()
        prefs.mt5Login = s.mt5Login.trim()
        prefs.mt5Password = s.mt5Password.trim()
        prefs.mt5Server = s.mt5Server.trim()
        prefs.mt5BridgeUrl = s.mt5BridgeUrl.trim()
        prefs.putString("bitget_market_type", s.marketType)
        prefs.putString("bitget_product_type", s.futuresProduct.trim().ifBlank { "USDT-FUTURES" })
        prefs.putString("bitget_margin_mode", s.futuresMargin.trim().ifBlank { "isolated" })
        prefs.geminiKey = s.geminiKey.trim()
        prefs.geminiModel = s.geminiModel.trim()
        prefs.openaiKey = s.openaiKey.trim()
        prefs.openaiModel = s.openaiModel.trim()
        prefs.anthropicKey = s.anthropicKey.trim()
        prefs.anthropicModel = s.anthropicModel.trim()
        prefs.ollamaUrl = s.ollamaUrl
        prefs.ollamaModel = s.ollamaModel.trim()
        prefs.deepseekKey = s.deepseekKey.trim()
        prefs.deepseekModel = s.deepseekModel.trim()
        prefs.fallbackChain = s.fallbackChain.joinToString(",")
        prefs.backendUrl = s.backendUrl
        prefs.wsUrl = s.wsUrl
        prefs.bearerToken = s.bearerToken.trim()
        prefs.themeMode = s.themeMode
        prefs.localModelPath = s.localModelPath
        prefs.localModelEnabled = s.localModelEnabled
        prefs.putString("army_symbol", s.armySymbol.uppercase().trim().ifBlank { "BTCUSDT" })
        prefs.putBool("agents_enabled", s.agentsEnabled)
        prefs.putInt("army_round_minutes", s.roundMinutes)
        prefs.putBool("hft_enabled", s.hftEnabled)
        prefs.putBool("live_trading", s.liveTrading)
        prefs.notifyTrades = s.notifyTrades
        prefs.notifyCrash = s.notifyCrash
        prefs.notifyDaily = s.notifyDaily
        prefs.notifyCircuit = s.notifyCircuit
        prefs.dailyHour = s.dailyHour
        prefs.refreshInterval = s.refreshInterval

        ThemeController.applyTheme(s.themeMode)
        runCatching { (context as? App)?.scheduleDailySummary() }
        if (geminiChanged && prefs.geminiKey.isNotBlank()) refreshGeminiModels()
        if (wsChanged) {
            ws.disconnect()
            ws.connect()
        }
        if (!silent) _saved.value = true
    }

    fun clearAllKeys() {
        prefs.clearAll()
        ThemeController.applyTheme("light")
        _state.value = SettingsUi()
        _tests.value = emptyMap()
        _saved.value = false
    }

    fun test(key: String, block: suspend () -> String) {
        _tests.value = _tests.value + (key to "⏳ Testing…")
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { block() } }
                .fold(
                    onSuccess = { "✅ $it" },
                    onFailure = { e -> "❌ ${e.message?.take(100) ?: e.javaClass.simpleName}" }
                )
            _tests.value = _tests.value + (key to result)
        }
    }

    fun testBackend() = test("backend") {
        if (marketRepo.backendAlive()) "Connected" else throw IllegalStateException("Offline / unreachable")
    }

    fun testBinance() = test("binance") {
        val s = _state.value
        if (s.binanceKey.isBlank() || s.binanceSecret.isBlank()) throw IllegalStateException("Key/Secret set karein")
        val ts = System.currentTimeMillis().toString()
        val query = "timestamp=$ts&recvWindow=5000"
        val sig = hmacSha256(s.binanceSecret, query)
        val url = "https://api.binance.com/api/v3/account?$query&signature=$sig"
        val resp = okhttp3.OkHttpClient().newCall(
            okhttp3.Request.Builder().url(url).header("X-MBX-APIKEY", s.binanceKey).get().build()
        ).execute()
        resp.use {
            if (it.isSuccessful) "Connected · spot account OK"
            else throw IllegalStateException("HTTP ${it.code} ${it.body?.string()?.take(120)}")
        }
    }

    fun testBybit() = test("bybit") {
        val s = _state.value
        if (s.bybitKey.isBlank() || s.bybitSecret.isBlank()) throw IllegalStateException("Key/Secret set karein")
        val ts = System.currentTimeMillis().toString()
        val recv = "5000"
        val query = "accountType=UNIFIED"
        val sign = hmacSha256(s.bybitSecret, "$ts${s.bybitKey}$recv$query")
        val url = "https://api.bybit.com/v5/account/wallet-balance?$query"
        val resp = okhttp3.OkHttpClient().newCall(
            okhttp3.Request.Builder().url(url)
                .header("X-BAPI-API-KEY", s.bybitKey)
                .header("X-BAPI-TIMESTAMP", ts)
                .header("X-BAPI-RECV-WINDOW", recv)
                .header("X-BAPI-SIGN", sign)
                .get().build()
        ).execute()
        resp.use {
            if (it.isSuccessful && it.body!!.string().contains("\"retCode\":0")) "Connected · wallet OK"
            else throw IllegalStateException("HTTP ${it.code}")
        }
    }

    fun testBitget() = test("bitget") {
        val r = bitgetClient.testConnection()
        if (r.ok) r.message else throw IllegalStateException(r.message)
    }

    fun testMt5() = test("mt5") {
        val s = _state.value
        if (s.mt5Login.isBlank() || s.mt5Password.isBlank()) throw IllegalStateException("Login/Password set karein")
        // MT5 has no public REST ping; validated end-to-end by the backend connector.
        "Saved · MT5 validation backend par hoga (${s.mt5Server.ifBlank { "server?" }})"
    }

    fun testGemini() = test("gemini") { ai.testGemini() }
    fun testDeepseek() = test("deepseek") { ai.testDeepseek() }
    fun testOpenAi() = test("openai") { ai.testOpenAi() }
    fun testAnthropic() = test("anthropic") { ai.testAnthropic() }
    fun testOllama() = test("ollama") { ai.testOllama() }

    fun importLocalModel(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching {
                val dir = java.io.File(context.filesDir, "models").apply { mkdirs() }
                val out = java.io.File(dir, "model.task")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                } ?: error("File open failed")
                update { it.copy(localModelPath = out.absolutePath, localModelEnabled = true) }
                save()
                _tests.value = _tests.value + ("local" to "✅ Model imported (${out.length() / 1024 / 1024} MB)")
            }.onFailure {
                _tests.value = _tests.value + ("local" to "❌ ${it.message}")
            }
        }
    }

    fun askAbout() { _showAbout.value = true }
    fun dismissAbout() { _showAbout.value = false }

    private fun hmacSha256(secret: String, payload: String): String =
        hmacSha256Bytes(secret, payload).joinToString("") { "%02x".format(it) }

    private fun hmacSha256Bytes(secret: String, payload: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray())
    }

    private fun base64(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    companion object {
        /** Static fallback list — key save hote hi API se live list fetch ho jati hai (auto-fill). */
        val GEMINI_MODELS = listOf(
            "gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite",
            "gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-2.0-flash-exp",
            "gemini-2.0-flash-thinking-exp-0121", "gemini-2.0-pro-exp",
            "gemini-1.5-pro", "gemini-1.5-flash", "gemini-1.5-flash-8b", "gemini-1.0-pro",
            "gemma-3-27b-it", "gemma-3-12b-it", "gemma-2-27b-it",
            "text-embedding-004", "audio-embedding-001",
            "imagen-3.0-generate-002", "gemini-2.0-flash-live-001"
        )
        val DEEPSEEK_MODELS = listOf("deepseek-chat", "deepseek-reasoner")
        val OPENAI_MODELS = listOf(
            "gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini",
            "o3-mini", "o1-mini", "gpt-4-turbo"
        )
        val ANTHROPIC_MODELS = listOf(
            "claude-sonnet-4-5", "claude-opus-4", "claude-haiku-4-5",
            "claude-3-7-sonnet-latest", "claude-3-5-sonnet-latest"
        )
        val OLLAMA_MODELS = listOf("llama3:8b", "mistral:7b", "gemma2:9b", "phi3:mini", "qwen2.5:7b")
    }
}
