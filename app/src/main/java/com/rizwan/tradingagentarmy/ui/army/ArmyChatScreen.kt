package com.rizwan.tradingagentarmy.ui.army

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArmyChatViewModel @Inject constructor(
    val army: AgentArmy,
    val prefs: SecurePreferences,
    val hft: HftEngine
) : ViewModel() {
    val messages: StateFlow<List<WarMessage>> = army.messages
    val busy: StateFlow<Boolean> = army.busy
    val lastPlan = army.lastPlan
    val status: StateFlow<String> = army.status
    val serviceOn: StateFlow<Boolean> = AgentArmyService.running

    private val _live = MutableStateFlow(prefs.getBool("live_trading", false))
    val live: StateFlow<Boolean> = _live

    private val _agentsOn = MutableStateFlow(prefs.getBool("agents_enabled", true))
    val agentsOn: StateFlow<Boolean> = _agentsOn

    private val _scalp = MutableStateFlow(prefs.getBool("scalp_enabled", prefs.getBool("hft_enabled", false)))
    val scalp: StateFlow<Boolean> = _scalp

    val hftActive = hft.active
    val hftSignal = hft.lastSignal

    fun runRound() = viewModelScope.launch { army.runRound() }
    fun send(text: String) = viewModelScope.launch { army.sendUserMessage(text) }
    fun clear() = viewModelScope.launch { army.clearHistory() }
    fun setSymbol(s: String) = prefs.putString("army_symbol", s.uppercase().trim())
    fun setRoundMinutes(m: Int) = prefs.putInt("army_round_minutes", m)

    fun toggleLive(on: Boolean) {
        prefs.putBool("live_trading", on); _live.value = on
    }

    fun toggleAgents(on: Boolean) {
        prefs.putBool("agents_enabled", on); _agentsOn.value = on
    }

    fun toggleScalp(on: Boolean) {
        prefs.putBool("scalp_enabled", on); prefs.putBool("hft_enabled", on); _scalp.value = on
        if (on) hft.start() else hft.stop()
    }

    fun startService(context: android.content.Context) = AgentArmyService.start(context)
    fun stopService(context: android.content.Context) = AgentArmyService.stop(context)
}

private val QUICK_COMMANDS = listOf(
    "status", "run round", "scalp on", "scalp off", "positions",
    "risk status", "bots", "symbol ETHUSDT", "kill on", "kill off"
)

@Composable
fun ArmyChatScreen(vm: ArmyChatViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messages by vm.messages.collectAsState()
    val busy by vm.busy.collectAsState()
    val status by vm.status.collectAsState()
    val live by vm.live.collectAsState()
    val agentsOn by vm.agentsOn.collectAsState()
    val scalp by vm.scalp.collectAsState()
    val serviceOn by vm.serviceOn.collectAsState()
    val plan by vm.lastPlan.collectAsState()
    val hftOn by vm.hftActive.collectAsState()
    val hftSig by vm.hftSignal.collectAsState()

    var input by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf(vm.prefs.getString("army_symbol", "BTCUSDT")) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚔️ Army Chat", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tokens.TextPrimary)
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(
                onClick = { vm.clear() },
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Tokens.Surface)
            ) { Icon(Icons.Filled.DeleteSweep, "clear", tint = Tokens.TextSecondary) }
        }
        Text(
            "14 agents · 1-min autonomous scan · round: data → debate → trade → risk → PM",
            fontSize = 11.sp, color = Tokens.TextSecondary
        )

        // status strip
        Surface(color = Tokens.Surface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(8.dp).background(
                        if (serviceOn && agentsOn) Tokens.SuccessGreen else Tokens.TextTertiary,
                        RoundedCornerShape(4.dp)
                    )
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    if (!serviceOn) "STOPPED — 24/7 band hai"
                    else if (!agentsOn) "PAUSED — agent rounds off"
                    else status,
                    fontSize = 11.sp, color = Tokens.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (scalp) {
                    Spacer(Modifier.width(6.dp))
                    Text(if (hftOn) "⚡ LIVE" else "⚡ ready", fontSize = 10.sp, color = Tokens.AccentPrimary)
                }
            }
        }

        // controls
        Surface(color = Tokens.Surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(
                        value = symbol, onValueChange = { symbol = it.uppercase() },
                        label = { Text("Symbol") }, singleLine = true,
                        modifier = Modifier.weight(1f), textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        colors = chatFieldColors()
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
                        Icon(if (serviceOn) Icons.Filled.Stop else Icons.Filled.PlayArrow, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (serviceOn) "STOP" else "START 24/7", fontSize = 12.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(
                        onClick = { vm.runRound() }, enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Tokens.AccentSecondary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(13.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Bolt, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (busy) "Round…" else "Run round", fontSize = 12.sp)
                    }
                    FilterChip(
                        selected = live, onClick = { vm.toggleLive(!live) },
                        label = { Text(if (live) "🔴 LIVE" else "🟢 PAPER", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = scalp, onClick = { vm.toggleScalp(!scalp) },
                        label = { Text("⚡ Scalp", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = agentsOn, onClick = { vm.toggleAgents(!agentsOn) },
                        label = { Text("24/7", fontSize = 12.sp) }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Round every", fontSize = 11.sp, color = Tokens.TextSecondary)
                    val mins = vm.prefs.getInt("army_round_minutes", 15)
                    Slider(
                        value = mins.toFloat(), onValueChange = { vm.setRoundMinutes(it.toInt()) },
                        valueRange = 2f..60f, modifier = Modifier.weight(1f)
                    )
                    Text("${mins}m", fontSize = 11.sp, color = Tokens.TextSecondary, modifier = Modifier.width(30.dp))
                    Text("· tick 1m", fontSize = 11.sp, color = Tokens.TextTertiary)
                }
            }
        }

        // last PM decision
        plan?.let { p ->
            val color = when {
                p.action == "HOLD" -> Tokens.WarningAmber
                p.action == "BUY" -> Tokens.SuccessGreen
                else -> Tokens.ErrorRed
            }
            Surface(
                color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, color), modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🏁 ${p.action} ${p.symbol}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
                    Spacer(Modifier.width(8.dp))
                    Text("conf ${p.confidence}%", fontSize = 11.sp, color = Tokens.TextSecondary)
                    Spacer(Modifier.weight(1f))
                    Text(p.reasoning.take(60), fontSize = 10.sp, color = Tokens.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // chat
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            reverseLayout = true
        ) {
            items(messages) { m -> ChatBubble(m) }
        }

        // quick commands
        Row(
            Modifier.fillMaxWidth().padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            QUICK_COMMANDS.take(5).forEach { c ->
                AssistChip(
                    onClick = { vm.send(c) },
                    label = { Text(c, fontSize = 10.sp, maxLines = 1) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Tokens.SurfaceElevated, labelColor = Tokens.TextSecondary
                    ),
                    border = BorderStroke(1.dp, Tokens.BorderSubtle)
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("Command ya message…", fontSize = 13.sp) },
                modifier = Modifier.weight(1f), singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                colors = chatFieldColors()
            )
            FilledIconButton(
                onClick = { if (input.isNotBlank()) { vm.send(input); input = "" } },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Tokens.AccentPrimary)
            ) { Icon(Icons.Filled.Send, "send") }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ChatBubble(m: WarMessage) {
    val isDecision = m.kind == "decision"
    val isUser = m.kind == "user"
    val isSystem = m.kind == "system"
    val accent = when {
        isUser -> Tokens.AccentSecondary
        isSystem -> Tokens.TextTertiary
        m.agent == AgentRole.RISK || m.agent == AgentRole.PM -> Tokens.WarningAmber
        m.agent == AgentRole.BULL -> Tokens.SuccessGreen
        m.agent == AgentRole.BEAR -> Tokens.ErrorRed
        else -> Tokens.AccentPrimary
    }
    Surface(
        color = if (isDecision) accent.copy(alpha = 0.12f) else Tokens.Surface,
        shape = RoundedCornerShape(11.dp),
        border = if (isDecision) BorderStroke(1.dp, accent) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isUser) {
                    Text(if (isSystem) "ℹ️" else m.agent.emoji, fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    if (isUser) "You" else if (isSystem) "System" else m.agent.displayName,
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent
                )
                Spacer(Modifier.weight(1f))
                Text(
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(m.timestamp)),
                    fontSize = 9.sp, color = Tokens.TextTertiary
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(m.content, fontSize = 13.sp, color = Tokens.TextPrimary, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun chatFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Tokens.AccentPrimary,
    unfocusedBorderColor = Tokens.BorderSubtle,
    focusedTextColor = Tokens.TextPrimary,
    unfocusedTextColor = Tokens.TextPrimary,
    cursorColor = Tokens.AccentPrimary,
    focusedLabelColor = Tokens.AccentPrimary,
    unfocusedLabelColor = Tokens.TextSecondary
)
