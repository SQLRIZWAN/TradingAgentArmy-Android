package com.rizwan.tradingagentarmy.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.rizwan.tradingagentarmy.ui.theme.Tokens

val QUICK_COMMANDS = listOf(
    "📈 Show P&L",
    "🤖 Create Bot",
    "🛑 Stop All Bots",
    "🔍 Analyze BTC",
    "📰 Market News",
    "📊 Run Backtest",
    "💰 Portfolio",
    "⚡ Bot Status"
)

@Composable
fun QuickChips(onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QUICK_COMMANDS.forEach { label ->
            TextButton(
                onClick = { onPick(label.substringAfter(' ')) },
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Tokens.SurfaceElevated)
            ) {
                Text(label, color = Tokens.TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            }
        }
    }
}
