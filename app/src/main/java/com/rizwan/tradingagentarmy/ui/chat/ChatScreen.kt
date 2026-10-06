package com.rizwan.tradingagentarmy.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rizwan.tradingagentarmy.domain.model.MessageRole
import com.rizwan.tradingagentarmy.ui.chat.components.MessageBubble
import com.rizwan.tradingagentarmy.ui.chat.components.QuickChips
import com.rizwan.tradingagentarmy.ui.chat.components.TypingIndicator
import com.rizwan.tradingagentarmy.ui.theme.Tokens
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val streamingId by viewModel.streamingId.collectAsState()
    val streamText by viewModel.streamText.collectAsState()
    val armyActivity by viewModel.armyActivity.collectAsState()
    val modelChip by viewModel.modelChip.collectAsState()
    val banner by viewModel.banner.collectAsState()
    val input by viewModel.input.collectAsState()
    val showClear by viewModel.showClearDialog.collectAsState()
    val prefill by viewModel.bus.prefill.collectAsState()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var menuMessage by remember { mutableStateOf<com.rizwan.tradingagentarmy.domain.model.ChatMessage?>(null) }

    LaunchedEffect(prefill) {
        if (prefill.isNotBlank()) viewModel.consumePrefill()
    }

    // Auto-scroll: only when the user is pinned to (or near) the bottom.
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(listState, messages.size, streamText) {
        if (atBottom && messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("AI Trading Army", style = MaterialTheme.typography.titleMedium)
                    Text(
                        modelChip,
                        style = MaterialTheme.typography.labelSmall,
                        color = Tokens.AccentPrimary
                    )
                }
            },
            actions = {
                IconButton(onClick = { viewModel.askClear() }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear chat", tint = Tokens.TextSecondary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Tokens.Surface,
                titleContentColor = Tokens.TextPrimary
            )
        )

        if (banner != null) {
            Surface(
                color = Tokens.AccentWarning.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    banner ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = Tokens.AccentWarning,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Tokens.AccentPrimary)
        if (busy && streamText.startsWith("Army team")) {
            Surface(color=Tokens.Surface,modifier=Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=4.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(streamText,style=MaterialTheme.typography.labelMedium,color=Tokens.AccentPrimary)
                    armyActivity.take(3).forEach { event ->
                        Text("${event.agent.displayName}: ${event.content.take(120)}",style=MaterialTheme.typography.labelSmall,color=Tokens.TextSecondary,maxLines=2)
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom
        ) {
            if (messages.isEmpty() && !busy) {
                item { EmptyState() }
            }
            items(messages, key = { it.id }) { m ->
                val streaming = streamingId == m.id
                MessageBubble(
                    message = if (streaming && streamText.isEmpty()) m.copy(content = "") else m,
                    streamed = if (streaming) streamText else null,
                    onLongPress = { menuMessage = it }
                )
                if (streaming && streamText.isEmpty()) {
                    Box(Modifier.padding(start = 14.dp)) { TypingIndicator() }
                }
            }
        }

        QuickChips(onPick = { cmd -> viewModel.onInput(cmd) })

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = viewModel::onInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Command… e.g. Create a BTC scalper", color = Tokens.TextSecondary) },
                maxLines = 4,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSend = { viewModel.send() }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Tokens.AccentPrimary,
                    unfocusedBorderColor = Tokens.BorderSubtle,
                    focusedTextColor = Tokens.TextPrimary,
                    unfocusedTextColor = Tokens.TextPrimary,
                    cursorColor = Tokens.AccentPrimary,
                    focusedContainerColor = Tokens.Surface,
                    unfocusedContainerColor = Tokens.Surface
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            )
            IconButton(
                onClick = { viewModel.send() },
                enabled = input.isNotBlank() && !busy
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (input.isNotBlank() && !busy) Tokens.AccentPrimary else Tokens.TextSecondary
                )
            }
        }
    }

    // message actions
    menuMessage?.let { m ->
        DropdownMenu(
            expanded = true,
            onDismissRequest = { menuMessage = null }
        ) {
            DropdownMenuItem(text = { Text("Copy") }, onClick = {
                viewModel.copy(if (streamingId == m.id) m.content + streamText else m.content)
                menuMessage = null
            })
            DropdownMenuItem(text = { Text("Regenerate") }, onClick = {
                menuMessage = null
                viewModel.regenerate()
            })
            DropdownMenuItem(text = { Text("Share") }, onClick = {
                viewModel.share(m.content)
                menuMessage = null
            })
            DropdownMenuItem(
                text = { Text("Delete", color = Tokens.AccentDanger) },
                onClick = {
                    viewModel.delete(m.id)
                    menuMessage = null
                }
            )
        }
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClear() },
            containerColor = Tokens.Surface,
            title = { Text("Clear chat history?", color = Tokens.TextPrimary) },
            text = { Text("Pura local chat history delete ho jayega.", color = Tokens.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmClear() }) {
                    Text("Clear", color = Tokens.AccentDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClear() }) {
                    Text("Cancel", color = Tokens.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("🤖 AI Trading Army", style = MaterialTheme.typography.titleLarge, color = Tokens.TextPrimary)
        Text(
            "Backend se baat karega, warna direct Gemini → OpenAI → Claude → Ollama chain.\nQuick chips se command chunein ya seedha type karein.",
            style = MaterialTheme.typography.bodySmall,
            color = Tokens.TextSecondary
        )
    }
}
