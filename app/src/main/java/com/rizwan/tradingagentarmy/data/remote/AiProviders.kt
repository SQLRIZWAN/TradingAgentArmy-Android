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

    sealed class Target(val model: String, val tier: Int) {
        class Gemini(model: String) : Target(model, 1)
        class OpenAi(model: String) : Target(model, 2)
        class Anthropic(model: String) : Target(model, 2)
        class Ollama(model: String) : Target(model, 3)
    }

    /** Expand a chain entry (e.g. "gemini-2.0-flash-exp", "ollama:llama3:8b") into a Target. */
    fun resolve(entry: String): Target? {
        val e = entry.trim()
        if (e.isEmpty()) return null
        if (e.startsWith("ollama:")) return Target.Ollama(e.removePrefix("ollama:"))
        return when {
            e.startsWith("gemini") -> Target.Gemini(e)
            e.startsWith("gpt") || e.startsWith("o1") -> Target.OpenAi(e)
            e.startsWith("claude") -> Target.Anthropic(e)
            e.startsWith("llama") || e.startsWith("mistral") || e.startsWith("gemma") ||
                e.startsWith("phi") -> Target.Ollama(e)
            else -> null
        }
    }

    fun chain(): List<Target> {
        if (prefs.pinnedModel.isNotBlank()) {
            resolve(prefs.pinnedModel)?.let { return listOf(it) }
        }
        return prefs.fallbackChain.split(",").mapNotNull { resolve(it) }
    }

    fun labelOf(target: Target): String = when (target) {
        is Target.Gemini -> "Gemini ${target.model}"
        is Target.OpenAi -> "OpenAI ${target.model}"
        is Target.Anthropic -> "Claude ${target.model}"
        is Target.Ollama -> "Ollama ${target.model}"
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
        return executeNdjson(req, onDelta) { line ->
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
                val err = it.body?.string()?.take(300) ?: ""
                throw ProviderException(it.code, "HTTP ${it.code}: $err")
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

    private fun executeNdjson(req: Request, onDelta: suspend (String) -> Unit, parse: (String) -> String?): String {
        val resp = http.newCall(req).execute()
        resp.use {
            if (!it.isSuccessful) {
                val err = it.body?.string()?.take(300) ?: ""
                throw ProviderException(it.code, "HTTP ${it.code}: $err")
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

    // ---------------- plain (non-stream) calls ----------------
    fun testGemini(): String {
        val key = prefs.geminiKey
        if (key.isBlank()) throw ProviderException(401, "Key not set")
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
            .get().build()
        http.newCall(req).execute().use {
            if (!it.isSuccessful) throw ProviderException(it.code, "HTTP ${it.code}")
            val models = json.parseToJsonElement(it.body!!.string()).jsonObject["models"]?.jsonArray?.size ?: 0
            return "$models models available"
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
