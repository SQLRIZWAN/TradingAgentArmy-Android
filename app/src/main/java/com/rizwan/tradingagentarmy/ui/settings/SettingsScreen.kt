package com.rizwan.tradingagentarmy.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rizwan.tradingagentarmy.BuildConfig
import com.rizwan.tradingagentarmy.ui.theme.AppFonts
import com.rizwan.tradingagentarmy.ui.theme.Tokens

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    val tests by vm.tests.collectAsState()
    val saved by vm.saved.collectAsState()
    val showAbout by vm.showAbout.collectAsState()

    Scaffold(
        containerColor = Tokens.BackgroundBase,
        topBar = {
            Surface(color = Tokens.Surface) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Settings", color = Tokens.TextPrimary, style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (saved) Text("✅ Saved", color = Tokens.AccentPrimary, style = MaterialTheme.typography.labelMedium)
                        Button(
                            onClick = { vm.save() },
                            modifier = Modifier.padding(start = 8.dp)
                        ) { Text("Save All") }
                    }
                }
            }
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

            // ================= SECTION 1: EXCHANGES =================
            Section("Exchange API Keys") {
                SecretField("Bitget API Key", s.bitgetKey) { v -> vm.update { it.copy(bitgetKey = v) } }
                SecretField("Bitget Secret", s.bitgetSecret) { v -> vm.update { it.copy(bitgetSecret = v) } }
                SecretField("Bitget Passphrase", s.bitgetPassphrase) { v -> vm.update { it.copy(bitgetPassphrase = v) } }
                TestRow("bitget", "Test Connection", tests["bitget"]) { vm.testBitget() }
                HorizontalDivider(color = Tokens.BorderSubtle)

                SecretField("Binance API Key", s.binanceKey) { v -> vm.update { it.copy(binanceKey = v) } }
                SecretField("Binance Secret", s.binanceSecret) { v -> vm.update { it.copy(binanceSecret = v) } }
                TestRow("binance", "Test Connection", tests["binance"]) { vm.testBinance() }
                HorizontalDivider(color = Tokens.BorderSubtle)

                SecretField("Bybit API Key", s.bybitKey) { v -> vm.update { it.copy(bybitKey = v) } }
                SecretField("Bybit Secret", s.bybitSecret) { v -> vm.update { it.copy(bybitSecret = v) } }
                TestRow("bybit", "Test Connection", tests["bybit"]) { vm.testBybit() }
                HorizontalDivider(color = Tokens.BorderSubtle)

                PlainField("MT5 Login ID", s.mt5Login) { v -> vm.update { it.copy(mt5Login = v) } }
                SecretField("MT5 Password", s.mt5Password) { v -> vm.update { it.copy(mt5Password = v) } }
                PlainField("MT5 Server (e.g. ICMarkets-Demo)", s.mt5Server) { v -> vm.update { it.copy(mt5Server = v) } }
                TestRow("mt5", "Test Connection", tests["mt5"]) { vm.testMt5() }
            }

            // ================= SECTION 2: AI KEYS =================
            Section("AI Model API Keys") {
                SecretField("Google Gemini API Key (PRIMARY)", s.geminiKey) { v ->
                    vm.update { it.copy(geminiKey = v) }
                }
                DropdownField("Gemini Model", s.geminiModel, s.geminiModels) { v ->
                    vm.update { it.copy(geminiModel = v) }
                }
                TestRow("gemini", "Test API Key", tests["gemini"]) { vm.testGemini() }
                HorizontalDivider(color = Tokens.BorderSubtle)

                SecretField("OpenAI API Key", s.openaiKey) { v -> vm.update { it.copy(openaiKey = v) } }
                DropdownField("OpenAI Model", s.openaiModel, SettingsViewModel.OPENAI_MODELS) { v ->
                    vm.update { it.copy(openaiModel = v) }
                }
                TestRow("openai", "Test API Key", tests["openai"]) { vm.testOpenAi() }
                HorizontalDivider(color = Tokens.BorderSubtle)

                SecretField("Anthropic API Key", s.anthropicKey) { v -> vm.update { it.copy(anthropicKey = v) } }
                DropdownField("Claude Model", s.anthropicModel, SettingsViewModel.ANTHROPIC_MODELS) { v ->
                    vm.update { it.copy(anthropicModel = v) }
                }
                TestRow("anthropic", "Test API Key", tests["anthropic"]) { vm.testAnthropic() }
                HorizontalDivider(color = Tokens.BorderSubtle)

                PlainField("Ollama Endpoint URL", s.ollamaUrl) { v -> vm.update { it.copy(ollamaUrl = v) } }
                DropdownField("Ollama Model", s.ollamaModel, s.ollamaModels) { v ->
                    vm.update { it.copy(ollamaModel = v) }
                }
                TestRow("ollama", "Test Connection", tests["ollama"]) { vm.testOllama() }
            }

            // ================= SECTION 3: FALLBACK CHAIN =================
            Section("AI Fallback Chain (↑↓ se reorder)") {
                s.fallbackChain.forEachIndexed { i, entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${i + 1}.",
                            color = Tokens.TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            entry,
                            color = if (i == 0) Tokens.AccentPrimary else Tokens.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = AppFonts.Mono),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { vm.moveChain(i, up = true) }, enabled = i > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, "Up", tint = Tokens.TextSecondary)
                        }
                        IconButton(onClick = { vm.moveChain(i, up = false) }, enabled = i < s.fallbackChain.size - 1) {
                            Icon(Icons.Filled.KeyboardArrowDown, "Down", tint = Tokens.TextSecondary)
                        }
                    }
                }
                Text(
                    "Pinned model Chat top-bar me dikhta hai. Chain me pehle wala model Tier-1 (Gemini) hai.",
                    color = Tokens.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // ================= SECTION 4: BACKEND =================
            Section("Backend Connection") {
                PlainField("Backend REST URL (e.g. http://192.168.1.100:8000)", s.backendUrl) { v ->
                    vm.update { it.copy(backendUrl = v) }
                }
                PlainField("WebSocket URL (e.g. ws://192.168.1.100:8000/ws)", s.wsUrl) { v ->
                    vm.update { it.copy(wsUrl = v) }
                }
                SecretField("Auth Bearer Token", s.bearerToken) { v -> vm.update { it.copy(bearerToken = v) } }
                TestRow("backend", "Test Connection", tests["backend"]) { vm.testBackend() }
            }

            // ================= SECTION 5: PREFERENCES =================
            Section("App Preferences") {
                SwitchRow("AMOLED Black theme", s.amoled) { v -> vm.update { it.copy(amoled = v) } }
                SwitchRow("Trade executed alerts", s.notifyTrades) { v -> vm.update { it.copy(notifyTrades = v) } }
                SwitchRow("Bot crash alerts", s.notifyCrash) { v -> vm.update { it.copy(notifyCrash = v) } }
                SwitchRow("Daily P&L summary", s.notifyDaily) { v -> vm.update { it.copy(notifyDaily = v) } }
                SwitchRow("Circuit breaker alerts", s.notifyCircuit) { v -> vm.update { it.copy(notifyCircuit = v) } }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Summary time: ", color = Tokens.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    listOf(8, 12, 17, 20, 22).forEach { h ->
                        TextButton(onClick = { vm.update { it.copy(dailyHour = h) } }) {
                            Text(
                                "${h}:00",
                                color = if (s.dailyHour == h) Tokens.AccentPrimary else Tokens.TextSecondary
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dashboard refresh: ", color = Tokens.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    listOf(5, 10, 30, 60).forEach { sec ->
                        TextButton(onClick = { vm.update { it.copy(refreshInterval = sec) } }) {
                            Text(
                                if (sec == 60) "1m" else "${sec}s",
                                color = if (s.refreshInterval == sec) Tokens.AccentPrimary else Tokens.TextSecondary
                            )
                        }
                    }
                }
            }

            // ================= FOOTER =================
            Surface(color = Tokens.Surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                        color = Tokens.TextSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = AppFonts.Mono)
                    )
                    TextButton(onClick = { vm.askAbout() }) { Text("About") }
                }
            }

            // ================= DANGER ZONE =================
            Surface(
                color = Tokens.AccentDanger.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("⚠️ Danger Zone", color = Tokens.AccentDanger, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Sabhi saved API keys device se delete ho jayengi (EncryptedSharedPreferences clear).",
                        color = Tokens.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = { vm.clearAllKeys() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear All Keys", color = Tokens.AccentDanger)
                    }
                }
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { vm.dismissAbout() },
            containerColor = Tokens.Surface,
            title = { Text("AI Trading Army", color = Tokens.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                        color = Tokens.TextSecondary,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = AppFonts.Mono)
                    )
                    Text("Build date: ${BuildConfig.BUILD_DATE}", color = Tokens.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("Package: ${BuildConfig.APPLICATION_ID}", color = Tokens.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("Model layer: Gemini → OpenAI → Claude → Ollama", color = Tokens.AccentPrimary, style = MaterialTheme.typography.bodySmall)
                    Text("Firebase project: sqlrrr · Firestore synced", color = Tokens.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissAbout() }) { Text("Close", color = Tokens.AccentPrimary) }
            }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(color = Tokens.Surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Tokens.AccentPrimary, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = Tokens.TextSecondary) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = "Toggle",
                    tint = Tokens.TextSecondary
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = fieldColors()
    )
}

@Composable
private fun PlainField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = Tokens.TextSecondary) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = fieldColors()
    )
}

@Composable
private fun DropdownField(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label, color = Tokens.TextSecondary) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors()
        )
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(4.dp))
                .clickable { expanded = true }
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onChange(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Tokens.AccentPrimary,
    unfocusedBorderColor = Tokens.BorderSubtle,
    focusedTextColor = Tokens.TextPrimary,
    unfocusedTextColor = Tokens.TextPrimary,
    cursorColor = Tokens.AccentPrimary,
    focusedLabelColor = Tokens.AccentPrimary,
    unfocusedLabelColor = Tokens.TextSecondary
)

@Composable
private fun TestRow(key: String, label: String, result: String?, onTest: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            result ?: "",
            color = when {
                result?.startsWith("✅") == true -> Tokens.AccentPrimary
                result?.startsWith("❌") == true -> Tokens.AccentDanger
                else -> Tokens.AccentWarning
            },
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = onTest) {
            Text("🔌 $label", color = Tokens.AccentPrimary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Tokens.TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Tokens.AccentPrimary,
                checkedThumbColor = Tokens.BackgroundBase
            )
        )
    }
}
