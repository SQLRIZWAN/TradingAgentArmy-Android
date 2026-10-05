package com.rizwan.tradingagentarmy.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePreferences @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getString(key: String, default: String = ""): String =
        prefs.getString(key, default) ?: default

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getBool(key: String, default: Boolean = false): Boolean =
        prefs.getBoolean(key, default)

    fun putBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun contains(key: String): Boolean = prefs.contains(key)

    // ---- Exchange keys ----
    var bitgetKey: String
        get() = getString(K_BITGET_KEY)
        set(v) = putString(K_BITGET_KEY, v)
    var bitgetSecret: String
        get() = getString(K_BITGET_SECRET)
        set(v) = putString(K_BITGET_SECRET, v)
    var bitgetPassphrase: String
        get() = getString(K_BITGET_PASSPHRASE)
        set(v) = putString(K_BITGET_PASSPHRASE, v)
    var binanceKey: String
        get() = getString(K_BINANCE_KEY)
        set(v) = putString(K_BINANCE_KEY, v)
    var binanceSecret: String
        get() = getString(K_BINANCE_SECRET)
        set(v) = putString(K_BINANCE_SECRET, v)
    var bybitKey: String
        get() = getString(K_BYBIT_KEY)
        set(v) = putString(K_BYBIT_KEY, v)
    var bybitSecret: String
        get() = getString(K_BYBIT_SECRET)
        set(v) = putString(K_BYBIT_SECRET, v)
    var mt5Login: String
        get() = getString(K_MT5_LOGIN)
        set(v) = putString(K_MT5_LOGIN, v)
    var mt5Password: String
        get() = getString(K_MT5_PASSWORD)
        set(v) = putString(K_MT5_PASSWORD, v)
    var mt5Server: String
        get() = getString(K_MT5_SERVER)
        set(v) = putString(K_MT5_SERVER, v)


    // ---- AI keys ----
    var geminiKey: String
        get() = getString(K_GEMINI_KEY)
        set(v) = putString(K_GEMINI_KEY, v)
    var geminiModel: String
        get() = getString(K_GEMINI_MODEL, "gemini-2.5-flash")
        set(v) = putString(K_GEMINI_MODEL, v)
    var openaiKey: String
        get() = getString(K_OPENAI_KEY)
        set(v) = putString(K_OPENAI_KEY, v)
    var openaiModel: String
        get() = getString(K_OPENAI_MODEL, "gpt-4o")
        set(v) = putString(K_OPENAI_MODEL, v)
    var anthropicKey: String
        get() = getString(K_ANTHROPIC_KEY)
        set(v) = putString(K_ANTHROPIC_KEY, v)
    var anthropicModel: String
        get() = getString(K_ANTHROPIC_MODEL, "claude-sonnet-4-5")
        set(v) = putString(K_ANTHROPIC_MODEL, v)
    var ollamaUrl: String
        get() = getString(K_OLLAMA_URL, "http://10.0.2.2:11434")
        set(v) = putString(K_OLLAMA_URL, v)
    var ollamaModel: String
        get() = getString(K_OLLAMA_MODEL, "llama3:8b")
        set(v) = putString(K_OLLAMA_MODEL, v)

    var deepseekKey: String
        get() = getString(K_DEEPSEEK_KEY)
        set(v) = putString(K_DEEPSEEK_KEY, v)
    var deepseekModel: String
        get() = getString(K_DEEPSEEK_MODEL, "deepseek-chat")
        set(v) = putString(K_DEEPSEEK_MODEL, v)

    var localModelPath: String
        get() = getString(K_LOCAL_MODEL_PATH)
        set(v) = putString(K_LOCAL_MODEL_PATH, v)
    var localModelEnabled: Boolean
        get() = getBool(K_LOCAL_MODEL_ENABLED, false)
        set(v) = putBool(K_LOCAL_MODEL_ENABLED, v)

    var fallbackChain: String
        get() = migrateChain(getString(K_FALLBACK_CHAIN, DEFAULT_CHAIN))
        set(v) = putString(K_FALLBACK_CHAIN, v)

    // retired Gemini model ids -> current ones (silent upgrade for old installs)
    private fun migrateChain(raw: String): String {
        var c = raw
        retiredModels.forEach { (old, new) ->
            c = c.split(old).joinToString(new)
        }
        return c
    }

    var pinnedModel: String
        get() = getString(K_PINNED_MODEL)
        set(v) = putString(K_PINNED_MODEL, v)

    // ---- Backend ----
    var backendUrl: String
        get() = getString(K_BACKEND_URL)
        set(v) = putString(K_BACKEND_URL, v)
    var wsUrl: String
        get() = getString(K_WS_URL)
        set(v) = putString(K_WS_URL, v)
    var bearerToken: String
        get() = getString(K_BEARER)
        set(v) = putString(K_BEARER, v)

    // ---- Preferences ----
    var amoled: Boolean
        get() = getBool(K_AMOLED, false)
        set(v) = putBool(K_AMOLED, v)
    var themeMode: String
        get() = getString(K_THEME_MODE, "light")
        set(v) = putString(K_THEME_MODE, v)
    var notifyTrades: Boolean
        get() = getBool(K_NOTIFY_TRADES, true)
        set(v) = putBool(K_NOTIFY_TRADES, v)
    var notifyCrash: Boolean
        get() = getBool(K_NOTIFY_CRASH, true)
        set(v) = putBool(K_NOTIFY_CRASH, v)
    var notifyDaily: Boolean
        get() = getBool(K_NOTIFY_DAILY, true)
        set(v) = putBool(K_NOTIFY_DAILY, v)
    var notifyCircuit: Boolean
        get() = getBool(K_NOTIFY_CIRCUIT, true)
        set(v) = putBool(K_NOTIFY_CIRCUIT, v)
    var dailyHour: Int
        get() = prefs.getInt(K_DAILY_HOUR, 20)
        set(v) = prefs.edit().putInt(K_DAILY_HOUR, v).apply()
    var refreshInterval: Int
        get() = prefs.getInt(K_REFRESH, 10)
        set(v) = prefs.edit().putInt(K_REFRESH, v).apply()

    companion object {
        const val K_BITGET_KEY = "bitget_key"
        const val K_BITGET_SECRET = "bitget_secret"
        const val K_BITGET_PASSPHRASE = "bitget_passphrase"
        const val K_BINANCE_KEY = "binance_key"
        const val K_BINANCE_SECRET = "binance_secret"
        const val K_BYBIT_KEY = "bybit_key"
        const val K_BYBIT_SECRET = "bybit_secret"
        const val K_MT5_LOGIN = "mt5_login"
        const val K_MT5_PASSWORD = "mt5_password"
        const val K_MT5_SERVER = "mt5_server"
        const val K_GEMINI_KEY = "gemini_key"
        const val K_GEMINI_MODEL = "gemini_model"
        const val K_DEEPSEEK_KEY = "deepseek_key"
        const val K_DEEPSEEK_MODEL = "deepseek_model"
        const val K_THEME_MODE = "theme_mode"
        const val K_OPENAI_KEY = "openai_key"
        const val K_OPENAI_MODEL = "openai_model"
        const val K_ANTHROPIC_KEY = "anthropic_key"
        const val K_ANTHROPIC_MODEL = "anthropic_model"
        const val K_OLLAMA_URL = "ollama_url"
        const val K_OLLAMA_MODEL = "ollama_model"
        const val K_LOCAL_MODEL_PATH = "local_model_path"
        const val K_LOCAL_MODEL_ENABLED = "local_model_enabled"
        const val K_FALLBACK_CHAIN = "fallback_chain"
        const val K_PINNED_MODEL = "pinned_model"
        const val K_BACKEND_URL = "backend_url"
        const val K_WS_URL = "ws_url"
        const val K_BEARER = "bearer_token"
        const val K_AMOLED = "amoled"
        const val K_NOTIFY_TRADES = "notify_trades"
        const val K_NOTIFY_CRASH = "notify_crash"
        const val K_NOTIFY_DAILY = "notify_daily"
        const val K_NOTIFY_CIRCUIT = "notify_circuit"
        const val K_DAILY_HOUR = "daily_hour"
        const val K_REFRESH = "refresh_interval"

        const val DEFAULT_CHAIN =
            "gemini-2.5-flash,gemini-2.5-pro,deepseek-chat,gpt-4o,gpt-4o-mini,claude-sonnet-4-5"

        private val retiredModels = mapOf(
            "gemini-2.0-flash-exp" to "gemini-2.5-flash",
            "gemini-exp-1206" to "gemini-2.5-flash",
            "gemini-1.5-pro" to "gemini-2.5-pro",
            "gemini-1.5-flash-8b" to "gemini-2.5-flash",
            "gemini-1.5-flash" to "gemini-2.5-flash",
            "gemini-2.0-pro-exp" to "gemini-2.5-pro",
            "gemini-1.0-pro" to "gemini-2.5-pro"
        )
    }
}
