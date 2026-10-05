package com.rizwan.tradingagentarmy.ui.bots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rizwan.tradingagentarmy.domain.model.Bot
import com.rizwan.tradingagentarmy.domain.model.BotStatus
import com.rizwan.tradingagentarmy.ui.theme.AppFonts
import com.rizwan.tradingagentarmy.ui.theme.Tokens

@Composable
fun BotsScreen(
    onOpen: (String) -> Unit,
    viewModel: BotsViewModel = hiltViewModel()
) {
    val bots by viewModel.bots.collectAsState()

    Scaffold(
        containerColor = Tokens.BackgroundBase,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.offerCreateCommand() },
                containerColor = Tokens.AccentPrimary,
                contentColor = Tokens.BackgroundBase
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New Bot")
            }
        }
    ) { padding ->
        if (bots.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No bots yet", color = Tokens.TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "FAB dabao — Chat me command pre-fill ho jayega",
                        color = Tokens.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(bots, key = { it.id }) { bot ->
                    BotCard(
                        bot = bot,
                        onOpen = { onOpen(bot.id) },
                        onPause = { viewModel.setStatus(bot.id, BotStatus.DEMO) },
                        onStart = { viewModel.setStatus(bot.id, BotStatus.RUNNING) },
                        onStop = { viewModel.setStatus(bot.id, BotStatus.STOPPED) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BotCard(
    bot: Bot,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Tokens.Surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(bot.name, color = Tokens.TextPrimary, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${bot.market} · ${bot.strategy}",
                        color = Tokens.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                StatusChip(bot.status)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (if (bot.pnlToday >= 0) "+" else "") + "%.2f".format(bot.pnlToday) + " today",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = AppFonts.Mono),
                    color = if (bot.pnlToday >= 0) Tokens.AccentPrimary else Tokens.AccentDanger
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GateBadge("G1", bot.gate1)
                    GateBadge("G2", bot.gate2)
                    GateBadge("G3", bot.gate3)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onPause) {
                    Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = Tokens.AccentWarning)
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Filled.Delete, contentDescription = "Stop", tint = Tokens.AccentDanger)
                }
                if (bot.status != BotStatus.RUNNING) {
                    IconButton(onClick = onStart) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Start", tint = Tokens.AccentPrimary)
                    }
                }
                IconButton(onClick = onOpen) {
                    Icon(Icons.Filled.Visibility, contentDescription = "Details", tint = Tokens.TextSecondary)
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: BotStatus) {
    val (color, label) = when (status) {
        BotStatus.RUNNING -> Tokens.AccentPrimary to "🟢 Running"
        BotStatus.DEMO -> Tokens.AccentWarning to "🟡 Demo"
        BotStatus.STOPPED -> Tokens.AccentDanger to "🔴 Stopped"
        BotStatus.PENDING -> Tokens.TextSecondary to "⚪ Pending"
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun GateBadge(label: String, passed: Boolean) {
    Box(
        Modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (passed) Tokens.AccentPrimary.copy(alpha = 0.15f) else Tokens.BorderSubtle)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            "$label${if (passed) "✅" else "⏳"}",
            color = if (passed) Tokens.AccentPrimary else Tokens.TextSecondary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private val UnusedColor: Color = Color.Transparent
