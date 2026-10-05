package com.rizwan.tradingagentarmy.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rizwan.tradingagentarmy.domain.model.ChatMessage
import com.rizwan.tradingagentarmy.domain.model.MessageRole
import com.rizwan.tradingagentarmy.domain.model.TradeSignal
import com.rizwan.tradingagentarmy.ui.theme.AppFonts
import com.rizwan.tradingagentarmy.ui.theme.Tokens
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    streamed: String?,
    onLongPress: (ChatMessage) -> Unit
) {
    val isUser = message.role == MessageRole.USER
    val isSignal = message.role == MessageRole.SIGNAL ||
        (message.role == MessageRole.AI && runCatching {
            json.decodeFromString<TradeSignal>(message.content).isValid
        }.getOrDefault(false))

    var menuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(if (isUser) 14.dp else 14.dp, if (isUser) 14.dp else 4.dp, if (isUser) 4.dp else 14.dp, if (isUser) 14.dp else 14.dp))
                .background(if (isUser) Tokens.UserBubble else Tokens.Surface)
                .combinedClickable(onClick = {}, onLongClick = {
                    menuOpen = true
                    onLongPress(message)
                })
                .padding(10.dp)
        ) {
            if (!isUser && message.model != null) {
                Text(
                    text = message.model,
                    style = MaterialTheme.typography.labelSmall,
                    color = Tokens.AccentPrimary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            val body = streamed?.let { message.content + it } ?: message.content
            if (isSignal && body.isNotBlank()) {
                SignalCard(body)
            } else {
                Text(
                    text = body.ifEmpty { if (streamed != null) "" else "…" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tokens.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun SignalCard(raw: String) {
    val sig = runCatching { json.decodeFromString<TradeSignal>(raw) }.getOrNull() ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Tokens.SurfaceElevated)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = sig.symbol,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Tokens.TextPrimary
            )
            Text(
                text = sig.action,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (sig.action.equals("BUY", true)) Tokens.AccentPrimary else Tokens.AccentDanger
            )
        }
        MonoRow("Entry", "%.4f".format(sig.entry_price))
        MonoRow("SL", "%.4f".format(sig.stop_loss))
        MonoRow("TP", sig.take_profit_targets.joinToString(" → ") { "%.4f".format(it) })
        MonoRow("Risk", "${sig.risk_percentage}% · RR conf ${(sig.confidence_score * 100).toInt()}%")
        MonoRow("Strategy", sig.strategy_source)
        if (sig.reasoning.isNotBlank()) {
            Text(
                text = sig.reasoning,
                style = MaterialTheme.typography.bodySmall,
                color = Tokens.TextSecondary
            )
        }
    }
}

@Composable
private fun MonoRow(k: String, v: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(k, style = MaterialTheme.typography.bodySmall, color = Tokens.TextSecondary)
        Text(
            v,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = AppFonts.Mono),
            color = Tokens.TextPrimary
        )
    }
}
