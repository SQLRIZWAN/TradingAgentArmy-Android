package com.rizwan.tradingagentarmy.localmodel

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLlm @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SecurePreferences
) {
    private var inference: LlmInference? = null
    private var loadedPath: String = ""

    val ready: Boolean get() = inference != null

    @Synchronized
    fun ensure(modelPath: String = prefs.localModelPath) {
        if (modelPath.isBlank()) return
        if (inference != null && loadedPath == modelPath) return
        runCatching { inference?.close() }
        inference = null
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTopK(64)
            .setMaxTokens(2048)
            .build()
        inference = LlmInference.createFromOptions(context, options)
        loadedPath = modelPath
    }

    @Synchronized
    fun unload() {
        runCatching { inference?.close() }
        inference = null
        loadedPath = ""
    }

    suspend fun generate(system: String, prompt: String): String = withContext(Dispatchers.Default) {
        if (prefs.localModelPath.isBlank())
            throw IllegalStateException("No local model selected — Settings → Local Model")
        ensure()
        val llm = inference ?: throw IllegalStateException("Local model failed to load")
        val full = if (system.isBlank()) prompt else "$system\n\n$prompt"
        llm.generateResponse(full)
    }
}
