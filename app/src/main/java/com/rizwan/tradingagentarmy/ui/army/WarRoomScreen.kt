package com.rizwan.tradingagentarmy.ui.army

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizwan.tradingagentarmy.agents.AgentArmy
import com.rizwan.tradingagentarmy.agents.AgentArmyService
import com.rizwan.tradingagentarmy.agents.AgentRole
import com.rizwan.tradingagentarmy.agents.WarMessage
import com.rizwan.tradingagentarmy.data.local.SecurePreferences
import com.rizwan.tradingagentarmy.trading.HftEngine
import com.rizwan.tradingagentarmy.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WarRoomViewModel @Inject constructor(
    val army: AgentArmy,
    val prefs: SecurePreferences,
    val hft: HftEngine
) : ViewModel() {
    val messages = army.messages
    val busy = army.busy
    val lastPlan = army.lastPlan
    val status = army.status
    val hftActive = hft.active
    val hftSignal = hft.lastSignal
    val hftPosition = hft.position
    val liveTrading = MutableStateFlow(false)
    val serviceOn = MutableStateFlow(false)

    init {
        liveTrading.value = prefs.getBool("live_trading", false)
    }

    fun runRound() = viewModelScope.launch { army.runRound() }
    fun send(text: String) = viewModelScope.launch { army.sendUserMessage(text) }
    fun clear() = viewModelScope.launch { army.clearHistory() }
    fun toggleLive(on: Boolean) {
        prefs.putBool("live_trading", on)
        liveTrading.value = on
    }
    fun setSymbol(s: String) = prefs.putString("army_symbol", s.uppercase().replace("/", "").trim())
    fun setRoundMinutes(m: Int) = prefs.putInt("army_round_minutes", m)
    fun toggleAgents(on: Boolean) = prefs.putBool("agents_enabled", on)
    fun toggleHft(on: Boolean) = prefs.putBool("hft_enabled", on)
    fun startService(context: android.content.Context) {
        AgentArmyService.start(context)
        serviceOn.value = true
    }
    fun stopService(context: android.content.Context) {
        AgentArmyService.stop(context)
        serviceOn.value = false
    }
}

@Composable
fun WarRoomScreen(vm: WarRoomViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messages by vm.messages.collectAsState()
    val busy by vm.busy.collectAsState()
    val plan by vm.lastPlan.collectAsState()
    val status by vm.status.collectAsState()
    val live by vm.liveTrading.collectAsState()
    val hftOn by vm.hftActive.collectAsState()
    val hftSig by vm.hftSignal.collectAsState()
    val hftPos by vm.hftPosition.collectAsState()
    val serviceOn by AgentArmyService.running.collectAsState()
    var input by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf(vm.prefs.getString("army_symbol", "BTCUSDT")) }
    var agentsOn by remember { mutableStateOf(vm.prefs.getBool("agents_enabled", true)) }
    var hftEnabled by remember { mutableStateOf(vm.prefs.getBool("hft_enabled", false)) }
    var confirmLive by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Text("🤖 AI Agent Army", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tokens.TextPrimary)
        Text("14 agents · analyst team → bull/bear debate → trader → risk → PM decision", fontSize = 12.sp, color = Tokens.TextSecondary)

        // controls
        Surface(
            color = Tokens.Surface, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = symbol, onValueChange = { symbol = it.uppercase() },
                        label = { Text("Symbol") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = outlinedColors()
                    )
                    Button(
                        onClick = {
                            vm.setSymbol(symbol)
                            if (serviceOn) vm.stopService(context) else vm.startService(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (serviceOn) Tokens.ErrorRed else Tokens.AccentPrimary
                        )
                    ) {
                        Icon(if (serviceOn) Icons.Filled.Stop else Icons.Filled.PlayArrow, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (serviceOn) "STOP" else "START 24/7")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { vm.runRound() }, enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Tokens.AccentSecondary)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(14.dp), color = TokenWhite())
                        else Icon(Icons.Filled.Bolt, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (busy) "Round live…" else "Run round now")
                    }
                    FilterChip(selected = live, onClick = { if (live) vm.toggleLive(false) else confirmLive = true },
                        label = { Text(if (live) "🔴 ${if (vm.prefs.bitgetDemo) "DEMO" else "LIVE"} orders" else "🟢 PAPER mode") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = agentsOn, onClick = { agentsOn = !agentsOn; vm.toggleAgents(agentsOn) },
                        label = { Text("24/7 rounds") })
                    FilterChip(selected = hftEnabled, onClick = { hftEnabled = !hftEnabled; vm.toggleHft(hftEnabled) },
                        label = { Text("⚡ HFT") })
                    Text(status, fontSize = 11.sp, color = Tokens.TextSecondary, maxLines = 2)
                }
                if (hftEnabled) {
                    Text(
                        "HFT: ${if (hftOn) "RUNNING" else "idle"} · pos $hftPos · $hftSig",
                        fontSize = 11.sp, color = Tokens.TextTertiary
                    )
                }
                val mins = vm.prefs.getInt("army_round_minutes", 15)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Round every", fontSize = 11.sp, color = Tokens.TextSecondary)
                    Slider(
                        value = mins.toFloat(), onValueChange = { vm.setRoundMinutes(it.toInt()) },
                        valueRange = 2f..60f, modifier = Modifier.weight(1f)
                    )
                    Text("${mins}m", fontSize = 11.sp, color = Tokens.TextSecondary)
                }
            }
        }

        // last decision
        plan?.let { p ->
            val buy = p.action == "BUY"
            val hold = p.action == "HOLD"
            val color = when {
                hold -> Tokens.WarningAmber
                buy -> Tokens.SuccessGreen
                else -> Tokens.ErrorRed
            }
            Surface(color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("PM DECISION: ${p.action}", fontWeight = FontWeight.Bold, color = color, fontSize = 15.sp)
                        Spacer(Modifier.weight(1f))
                        Text("conf ${p.confidence}%", color = color, fontSize = 12.sp)
                    }
                    if (p.action != "HOLD") {
                        Text(
                            "${p.symbol} · entry ${p.entry ?: "-"} · SL ${p.stopLoss ?: "-"} · TP ${p.takeProfit ?: "-"} · size $${p.sizeUsd ?: "-"}",
                            fontSize = 12.sp, color = Tokens.TextSecondary
                        )
                    }
                    Text(p.reasoning, fontSize = 12.sp, color = Tokens.TextSecondary)
                }
            }
        }

        // messages
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = true
        ) {
            items(messages) { m -> WarBubble(m) }
        }

        // input
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("Message the army…") },
                modifier = Modifier.weight(1f), singleLine = true,
                colors = outlinedColors()
            )
            FilledIconButton(
                onClick = { if (input.isNotBlank()) { vm.send(input); input = "" } },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Tokens.AccentPrimary)
            ) { Icon(Icons.Filled.Send, "send") }
        }
        Spacer(Modifier.height(6.dp))
    }

    if (confirmLive) {
        AlertDialog(
            onDismissRequest = { confirmLive = false },
            title = { Text(if (vm.prefs.bitgetDemo) "Enable Demo order entry?" else "Enable live order entry?", color = Tokens.ErrorRed) },
            text = { Text(if (vm.prefs.bitgetDemo) "Army and HFT can send orders to the configured Bitget Demo API account while the service is running. Test the Demo API key and virtual balance first. PAPER mode sends no exchange orders." else "Army and HFT can send real orders to the configured exchange account while the service is running. Test the account first. PAPER mode sends no exchange orders.", color = Tokens.TextPrimary) },
            confirmButton = {
                TextButton(onClick = { confirmLive = false; vm.toggleLive(true) }) {
                    Text(if (vm.prefs.bitgetDemo) "Enable DEMO orders" else "Enable LIVE", color = Tokens.ErrorRed)
                }
            },
            dismissButton = { TextButton(onClick = { confirmLive = false }) { Text("Stay in PAPER", color = Tokens.AccentPrimary) } }
        )
    }
}

@Composable
private fun WarBubble(m: WarMessage) {
    val isDecision = m.kind == "decision"
    val isUser = m.kind == "user"
    val accent = when {
        isUser -> Tokens.AccentSecondary
        m.agent == AgentRole.RISK || m.agent == AgentRole.PM -> Tokens.WarningAmber
        m.agent == AgentRole.BULL -> Tokens.SuccessGreen
        m.agent == AgentRole.BEAR -> Tokens.ErrorRed
        else -> Tokens.AccentPrimary
    }
    Surface(
        color = if (isDecision) accent.copy(alpha = 0.12f) else Tokens.Surface,
        shape = RoundedCornerShape(12.dp),
        border = if (isDecision) androidx.compose.foundation.BorderStroke(1.dp, accent) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.agent.emoji, fontSize = 13.sp)
                Spacer(Modifier.width(5.dp))
                Text(m.agent.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accent)
                Spacer(Modifier.weight(1f))
                Text(
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(m.timestamp)),
                    fontSize = 10.sp, color = Tokens.TextTertiary
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(m.content, fontSize = 13.sp, color = Tokens.TextPrimary)
        }
    }
}

@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Tokens.AccentPrimary,
    unfocusedBorderColor = Tokens.BorderSubtle,
    focusedTextColor = Tokens.TextPrimary,
    unfocusedTextColor = Tokens.TextPrimary,
    cursorColor = Tokens.AccentPrimary,
    focusedLabelColor = Tokens.AccentPrimary,
    unfocusedLabelColor = Tokens.TextSecondary
)

@Composable
private fun TokenWhite() = androidx.compose.ui.graphics.Color.White
