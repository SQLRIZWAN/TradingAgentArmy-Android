package com.rizwan.tradingagentarmy.data.remote

import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class ProviderException(val code: Int, message: String) : Exception(message)

data class ChatTurn(val role: String, val content: String)

/**
 * Direct-model streaming clients with the Tier-1..3 fallback chain:
 * Gemini -> OpenAI -> Anthropic -> Ollama. Every call is a plain HTTPS
 * request so it works fully offline of any backend (Direct Mode).
 */
@Singleton
class AiProviders @Inject constructor(private val prefs: SecurePreferences) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val ollamaHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    sealed class Target(val model: String, val tier: Int) {
        class Gemini(model: String) : Target(model, 1)
        class DeepSeek(model: String) : Target(model, 1)
        class OpenAi(model: String) : Target(model, 2)
        class Anthropic(model: String) : Target(model, 2)
        class Ollama(model: String) : Target(model, 3)
    }

    /** Expand a chain entry (e.g. "gemini-2.0-flash-exp", "ollama:llama3:8b") into a Target. */
    fun resolve(entry: String): Target? {
        val e = entry.trim()
        if (e.isEmpty()) return null
        if (e.startsWith("ollama:")) return Target.Ollama(e.removePrefix("ollama:"))
        if (":" in e) return Target.Ollama(e)
        return when {
            e.startsWith("gemini") || e.startsWith("gemma") || e.startsWith("imagen-") ||
                e.startsWith("text-embedding") || e.startsWith("audio-embedding") -> Target.Gemini(e)
            e.startsWith("deepseek") -> Target.DeepSeek(e)
            e.startsWith("gpt") || e.startsWith("o1") || e.startsWith("o3") || e.startsWith("chatgpt") -> Target.OpenAi(e)
            e.startsWith("claude") -> Target.Anthropic(e)
            e.startsWith("llama") || e.startsWith("mistral") || e.startsWith("gemma") ||
                e.startsWith("phi") -> Target.Ollama(e)
            else -> null
        }
    }

    /**
     * Auto fallback chain:
     *  1. pinned model (if set)
     *  2. user's manual chain (only providers that actually have credentials)
     *  3. every other configured provider auto-appended (auto-fill)
     * Result: ek provider fail ho to agla turant try hota hai.
     */
    fun chain(): List<Target> {
        if (prefs.pinnedModel.isNotBlank()) {
            resolve(prefs.pinnedModel)?.filterConfigured()?.let { return listOf(it) }
        }
        val ordered = LinkedHashMap<String, Target>()
        prefs.fallbackChain.split(",").forEach { entry ->
            resolve(entry)?.filterConfigured()?.let { ordered[it.model] = it }
        }
        autoTargets().forEach { t -> if (!ordered.containsKey(t.model)) ordered[t.model] = t }
        return ordered.values.toList()
    }

    /** Providers the user has keys/endpoint for — in preferred priority order. */
    private fun autoTargets(): List<Target> {
        val list = mutableListOf<Target>()
        if (prefs.geminiKey.isNotBlank() && prefs.geminiModel.isNotBlank())
            list += Target.Gemini(prefs.geminiModel)
        if (prefs.deepseekKey.isNotBlank()) list += Target.DeepSeek(prefs.deepseekModel.ifBlank { "deepseek-chat" })
        if (prefs.openaiKey.isNotBlank() && prefs.openaiModel.isNotBlank()) list += Target.OpenAi(prefs.openaiModel)
        if (prefs.anthropicKey.isNotBlank() && prefs.anthropicModel.isNotBlank()) list += Target.Anthropic(prefs.anthropicModel)
        if (prefs.ollamaUrl != "http://10.0.2.2:11434" && prefs.ollamaModel.isNotBlank())
            list += Target.Ollama(prefs.ollamaModel)
        return list
    }

    private fun Target.filterConfigured(): Target? = when (this) {
        is Target.Gemini -> if (prefs.geminiKey.isNotBlank()) this else null
        is Target.DeepSeek -> if (prefs.deepseekKey.isNotBlank()) this else null
        is Target.OpenAi -> if (prefs.openaiKey.isNotBlank()) this else null
        is Target.Anthropic -> if (prefs.anthropicKey.isNotBlank()) this else null
        is Target.Ollama -> if (prefs.ollamaModel.isNotBlank()) this else null
    }

    fun labelOf(target: Target): String = when (target) {
        is Target.Gemini -> "Gemini"
        is Target.DeepSeek -> "DeepSeek"
        is Target.OpenAi -> "OpenAI"
        is Target.Anthropic -> "Claude"
        is Target.Ollama -> "Ollama"
    }

    /**
     * Streams tokens from [target]; returns the full accumulated text.
     * Throws [ProviderException] on failure so the chain can step down.
     */
    suspend fun stream(
        target: Target,
        system: String,
        history: List<ChatTurn>,
        userText: String,
        onDelta: suspend (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        when (target) {
            is Target.Gemini -> streamGemini(target.model, system, history, userText, onDelta)
            is Target.DeepSeek -> streamDeepSeek(target.model, system, history, userText, onDelta)
            is Target.OpenAi -> streamOpenAi(target.model, system, history, userText, onDelta)
            is Target.Anthropic -> streamAnthropic(target.model, system, history, userText, onDelta)
            is Target.Ollama -> streamOllama(target.model, system, history, userText, onDelta)
        }
    }

    // ---------------- Gemini ----------------
    private fun streamGemini(
        model: String, system: String, history: List<ChatTurn>, userText: String,
        onDelta: suspend (String) -> Unit
    ): String {
        val key = prefs.geminiKey
        if (key.isBlank()) throw ProviderException(401, "Gemini key missing")
        val body = buildJsonObject {
            put("systemInstruction", buildJsonObject {
                putJsonArray("parts") { add(buildJsonObject { put("text", system) }) }
            })
            putJsonArray("contents") {
                history.forEach { t ->
                    add(buildJsonObject {
                        put("role", if (t.role == "assistant") "model" else "user")
                        putJsonArray("parts") { add(buildJsonObject { put("text", t.content) }) }
                    })
                }
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { add(buildJsonObject { put("text", userText) }) }
                })
            }
            putJsonObject("generationConfig") { put("maxOutputTokens", 4096) }
        }
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return executeSse(req, onDelta) { payload ->
            val parts = Json.parseToJsonElement(payload).jsonObject["candidates"]?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray ?: return@executeSse null
            parts.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
                .ifEmpty { null }
        }
    }

    // ---------------- DeepSeek (OpenAI-compatible) ----------------
    private fun streamDeepSeek(
        model: String, system: String, history: List<ChatTurn>, userText: String,
        onDelta: suspend (String) -> Unit
    ): String {
        val key = prefs.deepseekKey
        if (key.isBlank()) throw ProviderException(401, "DeepSeek key missing")
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                history.forEach { t ->
                    add(buildJsonObject { put("role", t.role); put("content", t.content) })
                }
                add(buildJsonObject { put("role", "user"); put("content", userText) })
            }
        }
        val req = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return executeSse(req, onDelta) { payload ->
            if (payload.contains("[DONE]")) return@executeSse null
            runCatching {
                val delta = Json.parseToJsonElement(payload).jsonObject["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("delta")?.jsonObject
                delta?.get("content")?.jsonPrimitive?.content
            }.getOrNull()?.ifEmpty { null }
        }
    }

    // ---------------- OpenAI ----------------
    private fun streamOpenAi(
        model: String, system: String, history: List<ChatTurn>, userText: String,
        onDelta: suspend (String) -> Unit
    ): String {
        val key = prefs.openaiKey
        if (key.isBlank()) throw ProviderException(401, "OpenAI key missing")
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                history.forEach { t ->
                    add(buildJsonObject { put("role", t.role); put("content", t.content) })
                }
                add(buildJsonObject { put("role", "user"); put("content", userText) })
            }
        }
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return executeSse(req, onDelta) { payload ->
            if (payload.contains("[DONE]")) return@executeSse null
            runCatching {
                val delta = Json.parseToJsonElement(payload).jsonObject["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("delta")?.jsonObject
                delta?.get("content")?.jsonPrimitive?.content
            }.getOrNull()?.ifEmpty { null }
        }
    }

    // ---------------- Anthropic ----------------
    private fun streamAnthropic(
        model: String, system: String, history: List<ChatTurn>, userText: String,
        onDelta: suspend (String) -> Unit
    ): String {
        val key = prefs.anthropicKey
        if (key.isBlank()) throw ProviderException(401, "Anthropic key missing")
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 4096)
            put("system", system)
            putJsonArray("messages") {
                history.forEach { t ->
                    add(buildJsonObject { put("role", t.role); put("content", t.content) })
                }
                add(buildJsonObject { put("role", "user"); put("content", userText) })
            }
            put("stream", true)
        }
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return executeSse(req, onDelta) { payload ->
            runCatching {
                val obj = Json.parseToJsonElement(payload).jsonObject
                when (obj["type"]?.jsonPrimitive?.content) {
                    "content_block_delta" -> obj["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                    else -> null
                }
            }.getOrNull()?.ifEmpty { null }
        }
    }

    // ---------------- Ollama ----------------
    private fun streamOllama(
        model: String, system: String, history: List<ChatTurn>, userText: String,
        onDelta: suspend (String) -> Unit
    ): String {
        val base = prefs.ollamaUrl.trimEnd('/')
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                history.forEach { t ->
                    add(buildJsonObject { put("role", t.role); put("content", t.content) })
                }
                add(buildJsonObject { put("role", "user"); put("content", userText) })
            }
        }
        val req = Request.Builder()
            .url("$base/api/chat")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return executeNdjson(req, onDelta, ollamaHttp) { line ->
            runCatching {
                val obj = Json.parseToJsonElement(line).jsonObject
                if (obj["error"] != null) throw ProviderException(500, obj["error"]!!.jsonPrimitive.content)
                obj["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
            }.getOrNull()?.ifEmpty { null }
        }
    }

    // ---------------- helpers ----------------
    private fun executeSse(req: Request, onDelta: suspend (String) -> Unit, parse: (String) -> String?): String {
        val resp = http.newCall(req).execute()
        resp.use {
            if (!it.isSuccessful) {
                val err = it.body?.string() ?: ""
                throw ProviderException(it.code, cleanHttpError(it.code, err))
            }
            val body = it.body ?: throw ProviderException(500, "empty body")
            val source = body.source()
            val sb = StringBuilder()
            var error: String? = null
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data:")) {
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty()) continue
                    if (payload.contains("\"error\"") && payload.contains("quota")) {
                        error = "quota exceeded"
                    }
                    val piece = try {
                        parse(payload)
                    } catch (e: Exception) {
                        null
                    }
                    if (piece != null) {
                        sb.append(piece)
                        kotlinx.coroutines.runBlocking { onDelta(piece) }
                    }
                }
            }
            if (sb.isEmpty() && error != null) throw ProviderException(429, "quota exceeded")
            if (sb.isEmpty()) throw ProviderException(500, "no tokens streamed")
            return sb.toString()
        }
    }

    private fun executeNdjson(
        req: Request,
        onDelta: suspend (String) -> Unit,
        client: OkHttpClient = http,
        parse: (String) -> String?
    ): String {
        val resp = client.newCall(req).execute()
        resp.use {
            if (!it.isSuccessful) {
                val err = it.body?.string() ?: ""
                throw ProviderException(it.code, cleanHttpError(it.code, err))
            }
            val body = it.body ?: throw ProviderException(500, "empty body")
            val source = body.source()
            val sb = StringBuilder()
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                val piece = try {
                    parse(line)
                } catch (e: Exception) {
                    null
                }
                if (piece != null) {
                    sb.append(piece)
                    kotlinx.coroutines.runBlocking { onDelta(piece) }
                }
            }
            if (sb.isEmpty()) throw ProviderException(500, "no tokens streamed")
            return sb.toString()
        }
    }

    /** Short human error: extracts the useful part from provider JSON errors. */
    private fun cleanHttpError(code: Int, body: String): String {
        val msg = Regex(""""message"\s*:\s*"([^"]{1,160})"""").find(body)?.groupValues?.get(1)
            ?: Regex(""""error"\s*:\s*"([^"]{1,160})"""").find(body)?.groupValues?.get(1)
            ?: body.replace("\\s+".toRegex(), " ").take(140)
        val hint = when {
            "model" in body.lowercase() && (code == 404 || code == 400) -> " (model not found / retired)"
            "API key not valid" in body || "API_KEY_INVALID" in body -> " (key invalid)"
            code == 429 -> " (rate limit / quota)"
            else -> ""
        }
        return "HTTP $code: $msg$hint"
    }

    // ---------------- plain (non-stream) calls ----------------
    fun testGemini(): String {
        val key = prefs.geminiKey
        if (key.isBlank()) throw ProviderException(401, "Key not set")
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
            .get().build()
        http.newCall(req).execute().use {
            if (!it.isSuccessful) throw ProviderException(it.code, cleanHttpError(it.code, it.body?.string() ?: ""))
            val models = json.parseToJsonElement(it.body!!.string()).jsonObject["models"]?.jsonArray?.size ?: 0
            return "$models models available"
        }
    }

    fun testDeepseek(): String {
        val key = prefs.deepseekKey
        if (key.isBlank()) throw ProviderException(401, "Key not set")
        val req = Request.Builder().url("https://api.deepseek.com/models")
            .header("Authorization", "Bearer $key").get().build()
        http.newCall(req).execute().use {
            if (!it.isSuccessful) throw ProviderException(it.code, cleanHttpError(it.code, it.body?.string() ?: ""))
            return "Key valid"
        }
    }

    fun testOpenAi(): String {
        val key = prefs.openaiKey
        if (key.isBlank()) throw ProviderException(401, "Key not set")
        val req = Request.Builder().url("https://api.openai.com/v1/models")
            .header("Authorization", "Bearer $key").get().build()
        http.newCall(req).execute().use {
            if (!it.isSuccessful) throw ProviderException(it.code, "HTTP ${it.code}")
            return "Key valid"
        }
    }

    fun testAnthropic(): String {
        val key = prefs.anthropicKey
        if (key.isBlank()) throw ProviderException(401, "Key not set")
        val body = buildJsonObject {
            put("model", prefs.anthropicModel)
            put("max_tokens", 1)
            putJsonArray("messages") { add(buildJsonObject { put("role", "user"); put("content", "ping") }) }
        }
        val req = Request.Builder().url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", key).header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use {
            if (it.code == 401 || it.code == 403) throw ProviderException(it.code, "Key rejected")
            if (!it.isSuccessful && it.code != 400) throw ProviderException(it.code, "HTTP ${it.code}")
            return "Key valid"
        }
    }

    fun testOllama(): String {
        val base = prefs.ollamaUrl.trimEnd('/')
        val req = Request.Builder().url("$base/api/tags").get().build()
        http.newCall(req).execute().use {
            if (!it.isSuccessful) throw ProviderException(it.code, "HTTP ${it.code}")
            val tags = json.parseToJsonElement(it.body!!.string()).jsonObject["models"]?.jsonArray?.size ?: 0
            return "$tags models loaded"
        }
    }
}
