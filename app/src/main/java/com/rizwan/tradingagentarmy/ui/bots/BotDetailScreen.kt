package com.rizwan.tradingagentarmy.ui.bots

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizwan.tradingagentarmy.data.repository.BotRepository
import com.rizwan.tradingagentarmy.domain.model.Bot
import com.rizwan.tradingagentarmy.domain.model.BotStatus
import com.rizwan.tradingagentarmy.domain.model.Trade
import com.rizwan.tradingagentarmy.ui.theme.AppFonts
import com.rizwan.tradingagentarmy.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class BotDetailViewModel @Inject constructor(private val repo: BotRepository) : ViewModel() {

    private val _bot = MutableStateFlow<Bot?>(null)
    val bot: StateFlow<Bot?> = _bot.asStateFlow()

    private val _trades = MutableStateFlow<List<Trade>>(emptyList())
    val trades: StateFlow<List<Trade>> = _trades.asStateFlow()

    private val _deployed = MutableStateFlow(false)
    val deployed: StateFlow<Boolean> = _deployed.asStateFlow()

    fun load(id: String) = viewModelScope.launch {
        _bot.value = repo.bot(id)
        _trades.value = repo.tradesOf(_bot.value?.name ?: return@launch)
    }

    fun forceDeploy(id: String) = viewModelScope.launch {
        repo.promoteGates(id, true, true, true)
        repo.setStatus(id, BotStatus.RUNNING)
        _deployed.value = true
        _bot.value = repo.bot(id)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotDetailScreen(
    botId: String,
    onBack: () -> Unit,
    vm: BotDetailViewModel = hiltViewModel()
) {
    val bot by vm.bot.collectAsState()
    val trades by vm.trades.collectAsState()
    val deployed by vm.deployed.collectAsState()
    var confirmText by remember { mutableStateOf("") }
    var showForce by remember { mutableStateOf(false) }

    LaunchedEffect(botId) { vm.load(botId) }

    Scaffold(
        containerColor = Tokens.BackgroundBase,
        topBar = {
            TopAppBar(
                title = { Text(bot?.name ?: "Bot", color = Tokens.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Tokens.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Tokens.Surface)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val b = bot ?: return@Column

            Surface(color = Tokens.Surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(b.market, color = Tokens.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        StatusChip(b.status)
                    }
                    Text(b.strategy, color = Tokens.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "v${b.version} · created ${SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(b.createdAt))}",
                        color = Tokens.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Gates
            Surface(color = Tokens.Surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Demo → Live Gates", color = Tokens.TextPrimary, style = MaterialTheme.typography.titleSmall)
                    GateRow("Gate 1 — Backtest (6m OHLCV, WR≥50%, Sharpe≥1.0, DD<20%)", b.gate1)
                    GateRow("Gate 2 — Paper trading 72h (WR≥45%)", b.gate2)
                    GateRow("Gate 3 — Micro live 5% capital 48h (P&L ≥ -1%)", b.gate3)
                }
            }

            // P&L chart
            Surface(color = Tokens.Surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cumulative P&L (local trades)", color = Tokens.TextPrimary, style = MaterialTheme.typography.titleSmall)
                    val points = remember(trades) {
                        var acc = 0.0
                        trades.sortedBy { it.timestamp }.map { acc += it.pnl; acc }
                    }
                    if (points.isEmpty()) {
                        Text("No trades yet", color = Tokens.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    } else {
                        PnlChart(points, Modifier
                            .fillMaxWidth()
                            .height(160.dp))
                        val last = points.last()
                        Text(
                            (if (last >= 0) "+" else "") + "%.2f".format(last),
                            color = if (last >= 0) Tokens.AccentPrimary else Tokens.AccentDanger,
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = AppFonts.Mono)
                        )
                    }
                }
            }

            // Trades table
            Surface(color = Tokens.Surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("All Trades (${trades.size})", color = Tokens.TextPrimary, style = MaterialTheme.typography.titleSmall)
                    trades.forEach { t ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${t.symbol} ${t.side}",
                                color = Tokens.TextSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                (if (t.pnl >= 0) "+" else "") + "%.2f".format(t.pnl),
                                color = if (t.pnl >= 0) Tokens.AccentPrimary else Tokens.AccentDanger,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = AppFonts.Mono)
                            )
                        }
                    }
                }
            }

            if (!deployed || b.status != BotStatus.RUNNING) {
                Button(
                    onClick = { showForce = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Tokens.AccentDanger),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Force Deploy (bypass gates)")
                }
            }
        }
    }

    if (showForce) {
        AlertDialog(
            onDismissRequest = { showForce = false; confirmText = "" },
            containerColor = Tokens.Surface,
            title = { Text("Type CONFIRM to force deploy", color = Tokens.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Gate 3 tak pass kiye bina live capital lagayega. Risk 2%/trade limit yaad rakhein.",
                        color = Tokens.AccentWarning,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Tokens.AccentDanger,
                            unfocusedBorderColor = Tokens.BorderSubtle,
                            focusedTextColor = Tokens.TextPrimary,
                            unfocusedTextColor = Tokens.TextPrimary,
                            cursorColor = Tokens.AccentDanger
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = confirmText == "CONFIRM",
                    onClick = {
                        vm.forceDeploy(botId)
                        showForce = false
                        confirmText = ""
                    }
                ) { Text("Deploy", color = Tokens.AccentDanger) }
            },
            dismissButton = {
                TextButton(onClick = { showForce = false; confirmText = "" }) {
                    Text("Cancel", color = Tokens.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun GateRow(label: String, passed: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = Tokens.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(if (passed) "✅ PASS" else "⏳ PENDING", color = if (passed) Tokens.AccentPrimary else Tokens.AccentWarning)
    }
}

@Composable
private fun PnlChart(points: List<Double>, modifier: Modifier) {
    val line = if (points.last() >= 0) Tokens.AccentPrimary else Tokens.AccentDanger
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val min = points.min()
        val max = points.max()
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (points.size - 1)
        fun y(v: Double) = size.height - (((v - min) / range) * size.height).toFloat()

        val path = Path()
        points.forEachIndexed { i, v ->
            val x = i * stepX
            if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
        }
        // baseline at 0 if visible
        if (min < 0 && max > 0) {
            drawLine(
                color = Tokens.BorderSubtle,
                start = Offset(0f, y(0.0)),
                end = Offset(size.width, y(0.0)),
                strokeWidth = 1f
            )
        }
        // area fill
        val area = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, brush = Brush.verticalGradient(listOf(line.copy(alpha = 0.25f), Color.Transparent)))
        drawPath(path, color = line, style = Stroke(width = 3f, cap = StrokeCap.Round))
    }
}
