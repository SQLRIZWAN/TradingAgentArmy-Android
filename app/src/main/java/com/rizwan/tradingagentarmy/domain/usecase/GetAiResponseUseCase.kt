package com.rizwan.tradingagentarmy.domain.usecase

import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.data.remote.AiProviders
import com.rizwan.tradingagentarmy.data.remote.ChatTurn
import com.rizwan.tradingagentarmy.data.remote.ProviderException
import com.rizwan.tradingagentarmy.domain.model.AiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Order of resolution:
 *  1. Backend REST /api/chat  (if configured and alive)  -> backendUsed = true
 *  2. Direct fallback chain: Gemini -> OpenAI -> Anthropic -> Ollama
 *  3. Offline rule-based reply (critical decisions never come back empty)
 */
@Singleton
class GetAiResponseUseCase @Inject constructor(
    private val prefs: SecurePreferences,
    private val providers: AiProviders
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    val systemPrompt = """
        You are the UI Commander agent of an autonomous AI Trading Agent Army.
        You manage bots, risk and trade signals across crypto (Binance/Bybit/Bitget)
        and forex/metals (MT5: XAUUSD, EURUSD, GBPUSD).
        Hard rules: max 2% account risk per trade, min RR 1:2.5, max 10% portfolio drawdown.
        When you emit a trade signal use exactly this JSON and nothing else around it:
        {"timestamp":"","symbol":"BTC/USDT","action":"BUY|SELL|CLOSE|HOLD","order_type":"LIMIT|MARKET",
         "entry_price":0.0,"stop_loss":0.0,"take_profit_targets":[0.0],"position_size_units":0.0,
         "leverage":1,"risk_percentage":1.0,"strategy_source":"","confidence_score":0.0,
         "reasoning":"","invalidation_condition":""}
        Answer briefly in the user's language (Hindi/Urdu/English). Never invent prices:
        say "no live data" instead of guessing.
    """.trimIndent()

    suspend fun stream(
        userText: String,
        history: List<ChatTurn>,
        onDelta: suspend (String) -> Unit
    ): AiResult = withContext(Dispatchers.IO) {
        val attempts = mutableListOf<String>()
        // 1 - backend first (only when configured)
        if (prefs.backendUrl.isNotBlank()) {
            val backendResult = runCatching { backendChat(userText, history) }
            backendResult.onSuccess { reply ->
                reply.chunked(4).forEach { piece -> onDelta(piece) }
                return@withContext AiResult(reply, "backend", 0, backendUsed = true)
            }
            backendResult.onFailure { e ->
                attempts += "Backend: ${e.message?.take(60) ?: "offline"}"
            }
        }
        // 2 - direct model chain (auto fallback: ek fail -> agla)
        for (target in providers.chain()) {
            val label = providers.labelOf(target)
            try {
                val full = providers.stream(target, systemPrompt, history, userText, onDelta)
                return@withContext AiResult(full, "$label ${target.model}", target.tier, backendUsed = false)
            } catch (e: ProviderException) {
                attempts += "$label: ${e.message?.take(90)?.replace('\n', ' ') ?: "failed"}"
            } catch (e: Exception) {
                attempts += "$label: ${e.javaClass.simpleName}"
            }
        }
        if (attempts.isEmpty()) attempts += "koi AI key save nahi hai (Settings -> AI Model Keys)"
        // 3 - offline rule-based fallback
        val fallback = offlineReply(userText, attempts)
        onDelta(fallback)
        AiResult(fallback, "offline-rules", 4, backendUsed = false)
    }

    private fun backendChat(userText: String, history: List<ChatTurn>): String {
        val base = prefs.backendUrl.trimEnd('/')
        val body = buildJsonObject {
            put("message", userText)
            putJsonArray("history") {
                history.takeLast(20).forEach { t ->
                    add(buildJsonObject { put("role", t.role); put("content", t.content) })
                }
            }
        }
        val builder = Request.Builder().url("$base/api/chat")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        if (prefs.bearerToken.isNotBlank()) builder.header("Authorization", "Bearer ${prefs.bearerToken}")
        http.newCall(builder.build()).execute().use {
            if (!it.isSuccessful) throw ProviderException(it.code, "backend HTTP ${it.code}")
            val obj = json.parseToJsonElement(it.body!!.string()).jsonObject
            return obj["reply"]?.jsonPrimitive?.content
                ?: obj["response"]?.jsonPrimitive?.content
                ?: throw ProviderException(500, "no reply field")
        }
    }

    private fun offlineReply(userText: String, attempts: List<String>): String {
        val q = userText.lowercase()
        return when {
            listOf("p&l", "pnl", "profit").any { it in q } ->
                "⚠️ Direct Mode — backend offline. Apna aaj ka P&L Dashboard tab me dekhein (local trades se calculate hota hai)."
            "stop" in q && "bot" in q ->
                "🛑 Sab bots ko stop karne ke liye Bots tab me har bot par ⏹ Stop dabayein. Backend connect hone par ye command yahin se chal jayegi."
            "create" in q || "banaye" in q || "naya bot" in q ->
                "🤖 Bot banane ke liye strategy bata dein, jaise: 'Create a BTC 15m scalper with RSI+EMA'. Backend online hone par Bot Factory Agent khud banayega; filhaal Bots tab ka [+ New Bot] use kar sakte hain."
            listOf("buy", "sell", "signal", "entry").any { it in q } ->
                "⚠️ Live signal ke liye AI model key chahiye (Settings → AI Model Keys) ya backend connect karna hoga. Bina live data ke main signal nahi de sakta — zero hallucination rule."
            else ->
                "⚠️ Direct Mode — koi AI model available nahi hai.\n\n" +
                    "Check karein:\n• Settings → ① AI Model Keys (kam se kam ek key — Gemini best hai)\n" +
                    "• Key daalne ke baad ⌁ auto-save hoti hai, phir dobara bhejein\n" +
                    "• Ya Backend URL set karein\n\n" +
                    "Attempts:\n" + attempts.joinToString("\n") { "• $it" }
        }
    }
}
