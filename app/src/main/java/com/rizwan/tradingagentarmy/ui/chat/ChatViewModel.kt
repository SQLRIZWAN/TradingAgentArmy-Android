package com.rizwan.tradingagentarmy.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.data.repository.ChatRepository
import com.rizwan.tradingagentarmy.agents.AgentArmy
import com.rizwan.tradingagentarmy.agents.AgentArmyService
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
    val bus: CommandBus,
    private val army: AgentArmy
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
                when (operatorCommand(text)) {
                    OperatorCommand.START_ARMY -> {
                        AgentArmyService.start(context)
                        saveImmediateReply(
                            "Agent Army service start requested. Current trading mode: ${if (prefs.getBool("live_trading", false)) "LIVE" else "PAPER"}. Starting the service does not confirm an exchange connection; check Settings → Exchange → Test and watch the Army status."
                        )
                    }
                    OperatorCommand.STOP_ARMY -> {
                        AgentArmyService.stop(context)
                        saveImmediateReply("Agent Army service stop requested. Existing exchange positions remain open and continue to be synchronized; use the dashboard to review or close them.")
                    }
                    OperatorCommand.RESEARCH -> runResearch(text)
                    OperatorCommand.TRADE -> runTrading(text)
                    null -> streamIntoNewRow(text)
                }
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

    private suspend fun saveImmediateReply(text: String) {
        val id = repo.addPendingAi()
        repo.completeAssistantMessage(id, text, "app-command")
        _modelChip.value = "app-command"
    }

    private suspend fun runResearch(userText: String) {
        val id = repo.addPendingAi()
        _streamingId.value = id
        _streamText.value = "Army team researching…"
        try {
            val plan = army.runRound(userBrief = userText, executeOrders = false)
            val answer = if (plan == null) {
                "Army research did not complete: ${army.status.value}. No order was submitted. Check the status and AI/provider settings, then retry."
            } else {
                buildString {
                    appendLine("Army research complete · research only; no order was submitted.")
                    appendLine("Decision: ${plan.action} ${plan.symbol} · confidence ${plan.confidence}%")
                    if (plan.action != "HOLD") appendLine("Entry: ${plan.entry ?: "market quote"} · SL: ${plan.stopLoss ?: "not supplied"} · TP: ${plan.takeProfit ?: "not supplied"} · size: ${plan.sizeUsd?.let { "$$it" } ?: "not supplied"}")
                    append("Reason: ${plan.reasoning.ifBlank { "No explanation returned by the model." }}")
                }.trim()
            }
            repo.completeAssistantMessage(id, answer, "agent-army-research")
            _modelChip.value = "agent-army"
        } finally {
            _streamingId.value = null
            _streamText.value = ""
        }
    }

    private suspend fun runTrading(userText: String) {
        val id = repo.addPendingAi()
        _streamingId.value = id
        _streamText.value = "Army team checking account, risk limits, and market signals…"
        try {
            val plan = army.runRound(userBrief = userText, executeOrders = true)
            val answer = if (plan == null) {
                "Trade request did not produce an executable plan. Army status: ${army.status.value}. No trade is confirmed; check account connection and agent/provider status."
            } else {
                val orderActivity = army.messages.value.firstOrNull { it.content.contains("order", true) || it.content.contains("trade", true) || it.content.contains("blocked", true) }?.content
                buildString {
                    appendLine("Trade request processed · ${plan.action} ${plan.symbol} · confidence ${plan.confidence}%")
                    appendLine("Entry: ${plan.entry ?: "not supplied"} · SL: ${plan.stopLoss ?: "not supplied"} · TP: ${plan.takeProfit ?: "not supplied"}")
                    appendLine("Execution: ${orderActivity ?: if (plan.action.equals("HOLD", true)) "No order submitted; the army chose HOLD." else "Check dashboard positions and exchange order history for the confirmed result."}")
                    append("Reason: ${plan.reasoning.ifBlank { "No explanation returned by the model." }}")
                }.trim()
            }
            repo.completeAssistantMessage(id, answer, "agent-army-trade")
            _modelChip.value = "agent-army"
        } finally {
            _streamingId.value = null
            _streamText.value = ""
        }
    }

    private fun operatorCommand(text: String): OperatorCommand? {
        val q = text.lowercase().replace(Regex("\\s+"), " ").trim()
        val startCommand = listOf(
            "start army", "start the army", "army start", "army chalu", "army shuru",
            "start agents", "start the agents", "agents start", "agent team start", "start agent team",
            "start 24/7", "24/7 start", "run army"
        ).any { it in q }
        val stopCommand = listOf(
            "stop army", "stop the army", "army stop", "army band", "army pause",
            "stop agents", "stop the agents", "agents stop", "agents band", "agent team stop",
            "stop agent team", "stop 24/7", "24/7 stop", "pause army"
        ).any { it in q }
        if (startCommand) return OperatorCommand.START_ARMY
        if (stopCommand) return OperatorCommand.STOP_ARMY
        if (listOf("research", "analyse", "analyze", "analysis", "market report", "market check", "research karo", "research kar").any { it in q }) {
            return OperatorCommand.RESEARCH
        }
        if (listOf("trade", "place order", "buy", "sell", "entry lo", "trade lagao", "trade le", "order lagao", "execute trade").any { it in q }) {
            return OperatorCommand.TRADE
        }
        return null
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

    private enum class OperatorCommand { START_ARMY, STOP_ARMY, RESEARCH, TRADE }
}
