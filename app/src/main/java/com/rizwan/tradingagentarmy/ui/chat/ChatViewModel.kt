package com.rizwan.tradingagentarmy.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.data.repository.ChatRepository
import com.rizwan.tradingagentarmy.domain.model.ChatMessage
import com.rizwan.tradingagentarmy.ui.navigation.CommandBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: ChatRepository,
    private val prefs: SecurePreferences,
    val bus: CommandBus
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> =
        repo.messages().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _streamingId = MutableStateFlow<Long?>(null)
    val streamingId: StateFlow<Long?> = _streamingId.asStateFlow()

    private val _streamText = MutableStateFlow("")
    val streamText: StateFlow<String> = _streamText.asStateFlow()

    private val _modelChip = MutableStateFlow("auto")
    val modelChip: StateFlow<String> = _modelChip.asStateFlow()

    private val _banner = MutableStateFlow<String?>(null)
    val banner: StateFlow<String?> = _banner.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _showClearDialog = MutableStateFlow(false)
    val showClearDialog: StateFlow<Boolean> = _showClearDialog.asStateFlow()

    fun onInput(v: String) {
        _input.value = v
    }

    fun consumePrefill(): Boolean {
        val text = bus.prefill.value
        if (text.isNotBlank()) {
            _input.value = text
            bus.consume()
            return true
        }
        return false
    }

    fun send(raw: String? = null) {
        val text = (raw ?: _input.value).trim()
        if (text.isBlank() || _busy.value) return
        _input.value = ""
        _banner.value = null
        viewModelScope.launch {
            _busy.value = true
            runCatching {
                repo.addUser(text)
                streamIntoNewRow(text)
            }.onFailure { e ->
                _banner.value = "Error: ${e.message?.take(120)}"
            }
            _busy.value = false
        }
    }

    fun regenerate() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            runCatching {
                val turns = repo.historyTurns()
                val lastUser = turns.lastOrNull { it.role == "user" }?.content ?: return@runCatching
                // drop trailing assistant rows so the regenerated answer replaces them
                val all = messages.value
                all.lastOrNull { it.role == com.rizwan.tradingagentarmy.domain.model.MessageRole.AI }
                    ?.let { repo.delete(it.id) }
                streamIntoNewRow(lastUser)
            }.onFailure { _banner.value = "Regenerate failed: ${it.message?.take(100)}" }
            _busy.value = false
        }
    }

    private suspend fun streamIntoNewRow(userText: String) {
        val aiId = repo.addPendingAi()
        _streamingId.value = aiId
        _streamText.value = ""
        // tail = [.., userText, pendingAi] -> history must exclude both
        val tail = repo.historyTurns()
        val history = if (tail.size >= 2) tail.dropLast(2) else emptyList()
        val result = repo.streamReply(userText, history, aiId) { delta ->
            _streamText.value += delta
        }
        _modelChip.value = result.model
        _banner.value = when {
            result.backendUsed -> null
            result.tier >= 4 -> "⚠️ Offline mode — koi AI model available nahi (Settings me key daalein)"
            else -> null
        }
        _streamingId.value = null
        _streamText.value = ""
    }

    fun copy(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("chat", text))
    }

    fun share(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }

    fun askClear() { _showClearDialog.value = true }
    fun dismissClear() { _showClearDialog.value = false }
    fun confirmClear() {
        _showClearDialog.value = false
        viewModelScope.launch { repo.clear() }
    }
}
