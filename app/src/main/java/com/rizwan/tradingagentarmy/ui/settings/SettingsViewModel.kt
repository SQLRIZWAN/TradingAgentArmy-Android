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
    val geminiKey: String = "",
    val geminiModel: String = "gemini-2.0-flash-exp",
    val openaiKey: String = "",
    val openaiModel: String = "gpt-4o",
    val anthropicKey: String = "",
    val anthropicModel: String = "claude-sonnet-4-5",
    val ollamaUrl: String = "http://10.0.2.2:11434",
    val ollamaModel: String = "llama3:8b",
    val fallbackChain: List<String> = listOf(),
    val geminiModels: List<String> = SettingsViewModel.GEMINI_MODELS,
    val ollamaModels: List<String> = SettingsViewModel.OLLAMA_MODELS,
    val backendUrl: String = "",
    val wsUrl: String = "",
    val bearerToken: String = "",
    val amoled: Boolean = false,
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
    private val ws: WebSocketManager
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
            geminiKey = prefs.geminiKey,
            geminiModel = prefs.geminiModel,
            openaiKey = prefs.openaiKey,
            openaiModel = prefs.openaiModel,
            anthropicKey = prefs.anthropicKey,
            anthropicModel = prefs.anthropicModel,
            ollamaUrl = prefs.ollamaUrl,
            ollamaModel = prefs.ollamaModel,
            fallbackChain = prefs.fallbackChain.split(",").filter { it.isNotBlank() },
            backendUrl = prefs.backendUrl,
            wsUrl = prefs.wsUrl,
            bearerToken = prefs.bearerToken,
            amoled = prefs.amoled,
            notifyTrades = prefs.notifyTrades,
            notifyCrash = prefs.notifyCrash,
            notifyDaily = prefs.notifyDaily,
            notifyCircuit = prefs.notifyCircuit,
            dailyHour = prefs.dailyHour,
            refreshInterval = prefs.refreshInterval
        )
        refreshGeminiModels()
        refreshOllamaModels()
    }

    fun update(transform: (SettingsUi) -> SettingsUi) {
        _state.value = transform(_state.value)
        _saved.value = false
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

    fun save() {
        val s = _state.value
        prefs.bitgetKey = s.bitgetKey
        prefs.bitgetSecret = s.bitgetSecret
        prefs.bitgetPassphrase = s.bitgetPassphrase
        prefs.binanceKey = s.binanceKey
        prefs.binanceSecret = s.binanceSecret
        prefs.bybitKey = s.bybitKey
        prefs.bybitSecret = s.bybitSecret
        prefs.mt5Login = s.mt5Login
        prefs.mt5Password = s.mt5Password
        prefs.mt5Server = s.mt5Server
        prefs.geminiKey = s.geminiKey
        prefs.geminiModel = s.geminiModel
        prefs.openaiKey = s.openaiKey
        prefs.openaiModel = s.openaiModel
        prefs.anthropicKey = s.anthropicKey
        prefs.anthropicModel = s.anthropicModel
        prefs.ollamaUrl = s.ollamaUrl
        prefs.ollamaModel = s.ollamaModel
        prefs.fallbackChain = s.fallbackChain.joinToString(",")
        prefs.backendUrl = s.backendUrl
        prefs.wsUrl = s.wsUrl
        prefs.bearerToken = s.bearerToken
        prefs.amoled = s.amoled
        prefs.notifyTrades = s.notifyTrades
        prefs.notifyCrash = s.notifyCrash
        prefs.notifyDaily = s.notifyDaily
        prefs.notifyCircuit = s.notifyCircuit
        prefs.dailyHour = s.dailyHour
        prefs.refreshInterval = s.refreshInterval

        ThemeController.amoled = s.amoled
        runCatching { (context as? App)?.scheduleDailySummary() }
        ws.disconnect()
        ws.connect()
        _saved.value = true
    }

    fun clearAllKeys() {
        prefs.clearAll()
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
        val s = _state.value
        if (s.bitgetKey.isBlank() || s.bitgetSecret.isBlank() || s.bitgetPassphrase.isBlank())
            throw IllegalStateException("Key/Secret/Passphrase set karein")
        val ts = System.currentTimeMillis().toString()
        val method = "GET"
        val path = "/api/v2/spot/account/info"
        val sign = base64(hmacSha256Bytes(s.bitgetSecret, ts + method + path + ""))
        val resp = okhttp3.OkHttpClient().newCall(
            okhttp3.Request.Builder().url("https://api.bitget.com$path")
                .header("X-CODEX-API-KEY", s.bitgetKey)
                .header("X-CODEX-API-SIGN", sign)
                .header("X-CODEX-API-SIGN-TYPE", "HMACSHA256")
                .header("X-CODEX-TIMESTAMP", ts)
                .header("X-CODEX-PASSPHRASE", s.bitgetPassphrase)
                .get().build()
        ).execute()
        resp.use {
            val body = it.body?.string() ?: ""
            when {
                it.isSuccessful && body.contains("00000") -> "Connected · account OK"
                it.isSuccessful -> "⚠️ HTTP 200 but: ${body.take(120)}"
                else -> throw IllegalStateException("HTTP ${it.code} ${body.take(120)}")
            }
        }
    }

    fun testMt5() = test("mt5") {
        val s = _state.value
        if (s.mt5Login.isBlank() || s.mt5Password.isBlank()) throw IllegalStateException("Login/Password set karein")
        // MT5 has no public REST ping; validated end-to-end by the backend connector.
        "Saved · MT5 validation backend par hoga (${s.mt5Server.ifBlank { "server?" }})"
    }

    fun testGemini() = test("gemini") { ai.testGemini() }
    fun testOpenAi() = test("openai") { ai.testOpenAi() }
    fun testAnthropic() = test("anthropic") { ai.testAnthropic() }
    fun testOllama() = test("ollama") { ai.testOllama() }

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
        val GEMINI_MODELS = listOf(
            "gemini-2.0-flash-exp", "gemini-2.0-flash", "gemini-2.0-pro-exp",
            "gemini-1.5-pro", "gemini-1.5-flash", "gemini-1.5-flash-8b", "gemini-exp-1206"
        )
        val OPENAI_MODELS = listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "o1-preview", "o1-mini")
        val ANTHROPIC_MODELS = listOf(
            "claude-sonnet-4-5", "claude-opus-4", "claude-haiku-4-5", "claude-3-5-sonnet-20241022"
        )
        val OLLAMA_MODELS = listOf("llama3:8b", "mistral:7b", "gemma2:9b", "phi3:mini")
    }
}
