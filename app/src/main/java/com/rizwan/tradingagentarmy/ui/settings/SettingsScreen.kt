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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    var settingsTab by remember { mutableStateOf(0) }
    val modelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importLocalModel(it) } }

    Scaffold(
        containerColor = Tokens.BackgroundBase,
        topBar = {
            Surface(color = Tokens.Surface) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Settings", color = Tokens.TextPrimary, style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (saved) "✓ Saved — ab chat bhej kar check karein" else "Auto-save ON · type karte hi save",
                            color = if (saved) Tokens.AccentPrimary else Tokens.TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (saved) Tokens.AccentPrimary else Tokens.AccentWarning)
                        )
                        OutlinedButton(onClick = { vm.save() }, modifier = Modifier.padding(start = 10.dp)) {
                            Text("💾 Save Now", color = Tokens.AccentPrimary)
                        }
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            ScrollableTabRow(
                selectedTabIndex = settingsTab,
                containerColor = Tokens.Surface,
                contentColor = Tokens.AccentPrimary,
                edgePadding = 0.dp
            ) {
                listOf("🔑 AI Keys", "📱 Local Model", "🏦 Exchange", "🌐 Backend", "🤖 Army", "🎨 Look").forEachIndexed { i, label ->
                    Tab(
                        selected = settingsTab == i,
                        onClick = { settingsTab = i },
                        text = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            if (settingsTab == 0) {
            // ================= ① AI MODELS =================
            SectionCard(
                step = "①",
                title = "AI Model Keys (Chat ke liye)",
                subtitle = "Sirf ek key kaafi hai — Gemini best + free. Key daalo, auto-save, bas!"
            ) {
                ProviderCard(
                    name = "Google Gemini  ★ Recommended",
                    hint = "aistudio.google.com → Get API key",
                    key = s.geminiKey,
                    onKey = { v -> vm.update { it.copy(geminiKey = v) } },
                    model = s.geminiModel,
                    models = s.geminiModels,
                    onModel = { v -> vm.update { it.copy(geminiModel = v) } },
                    testResult = tests["gemini"],
                    onTest = { vm.testGemini() }
                )
                HorizontalDivider(color = Tokens.BorderSubtle)

                ProviderCard(
                    name = "DeepSeek (sasta + strong)",
                    hint = "platform.deepseek.com → API keys",
                    key = s.deepseekKey,
                    onKey = { v -> vm.update { it.copy(deepseekKey = v) } },
                    model = s.deepseekModel,
                    models = SettingsViewModel.DEEPSEEK_MODELS,
                    onModel = { v -> vm.update { it.copy(deepseekModel = v) } },
                    testResult = tests["deepseek"],
                    onTest = { vm.testDeepseek() }
                )
                HorizontalDivider(color = Tokens.BorderSubtle)

                ProviderCard(
                    name = "OpenAI (GPT)",
                    hint = "platform.openai.com → API keys",
                    key = s.openaiKey,
                    onKey = { v -> vm.update { it.copy(openaiKey = v) } },
                    model = s.openaiModel,
                    models = SettingsViewModel.OPENAI_MODELS,
                    onModel = { v -> vm.update { it.copy(openaiModel = v) } },
                    testResult = tests["openai"],
                    onTest = { vm.testOpenAi() }
                )
                HorizontalDivider(color = Tokens.BorderSubtle)

                ProviderCard(
                    name = "Anthropic (Claude)",
                    hint = "console.anthropic.com → API keys",
                    key = s.anthropicKey,
                    onKey = { v -> vm.update { it.copy(anthropicKey = v) } },
                    model = s.anthropicModel,
                    models = SettingsViewModel.ANTHROPIC_MODELS,
                    onModel = { v -> vm.update { it.copy(anthropicModel = v) } },
                    testResult = tests["anthropic"],
                    onTest = { vm.testAnthropic() }
                )
                HorizontalDivider(color = Tokens.BorderSubtle)

                ProviderCard(
                    name = "Ollama (local, free)",
                    hint = "Apne PC par Ollama chal raha ho",
                    key = s.ollamaUrl,
                    onKey = { v -> vm.update { it.copy(ollamaUrl = v) } },
                    keyLabel = "Ollama URL",
                    isSecret = false,
                    model = s.ollamaModel,
                    models = s.ollamaModels,
                    onModel = { v -> vm.update { it.copy(ollamaModel = v) } },
                    testResult = tests["ollama"],
                    onTest = { vm.testOllama() }
                )
            }

            // ================= ② AUTO FALLBACK CHAIN =================
            SectionCard(
                step = "②",
                title = "Auto Fallback Chain",
                subtitle = "Ek model fail ho to app khud agla model try karega. Keys ke hisaab se chain auto ban jati hai."
            ) {
                val chain = s.fallbackChain.ifEmpty { s.autoChain }
                if (chain.isEmpty()) {
                    Text(
                        "Abhi chain khali hai — ① me key daalte hi auto ban jayegi.",
                        color = Tokens.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    chain.forEachIndexed { i, entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${i + 1}.",
                                color = Tokens.AccentPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                entry,
                                color = Tokens.TextPrimary,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = AppFonts.Mono),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { vm.moveChain(i, up = true) }, enabled = i > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, "Up", tint = Tokens.TextSecondary)
                            }
                            IconButton(
                                onClick = { vm.moveChain(i, up = false) },
                                enabled = i < chain.size - 1
                            ) {
                                Icon(Icons.Filled.KeyboardArrowDown, "Down", tint = Tokens.TextSecondary)
                            }
                        }
                    }
                    Text(
                        "↑↓ se order badal sakte hain. Pehle wala model sabse pehle call hota hai.",
                        color = Tokens.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            }
            if (settingsTab == 1) {
                SectionCard(
                    step = "L",
                    title = "On-Device Model (Local Gemma)",
                    subtitle = "Model phone me hi chalega — data bhi private, offline bhi kaam karega."
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { modelPicker.launch(arrayOf("*/*")) }) {
                            Text("📁 Select model file", color = Tokens.AccentPrimary)
                        }
                        if (s.localModelPath.isNotBlank())
                            Text(
                                "✓ ${s.localModelPath.substringAfterLast('/')}",
                                color = Tokens.AccentPrimary,
                                style = MaterialTheme.typography.labelSmall
                            )
                    }
                    SwitchRow("Chain me on-device model ko pehle rakho", s.localModelEnabled) { v ->
                        vm.update { it.copy(localModelEnabled = v) }
                    }
                    HorizontalDivider(color = Tokens.BorderSubtle)
                    Text(
                    "Kaise: HuggingFace/Kaggle se Gemma .task file phone me download karein (WiFi, 1-4GB), " +
                        "phir yahan select karein. Selected file app ke private folder me copy ho jati hai. " +
                        "Gemini key bhi ho to dono ek sath kaam karte hain (auto-fallback).",
                        color = Tokens.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TestRow("local", tests["local"]) { }
                }
            }

            if (settingsTab == 2) {
            // ================= ③ EXCHANGES =================
            SectionCard(
                step = "③",
                title = "Exchange API Keys (Live trading)",
                subtitle = "Optional — sirf tab chahiye jab app se trade execute karwana ho."
            ) {
                SecretField("Bitget API Key", s.bitgetKey) { v -> vm.update { it.copy(bitgetKey = v) } }
                SecretField("Bitget Secret", s.bitgetSecret) { v -> vm.update { it.copy(bitgetSecret = v) } }
                SecretField("Bitget Passphrase", s.bitgetPassphrase) { v -> vm.update { it.copy(bitgetPassphrase = v) } }
                TestRow("bitget", tests["bitget"]) { vm.testBitget() }
                HorizontalDivider(color = Tokens.BorderSubtle)

                SecretField("Binance API Key", s.binanceKey) { v -> vm.update { it.copy(binanceKey = v) } }
                SecretField("Binance Secret", s.binanceSecret) { v -> vm.update { it.copy(binanceSecret = v) } }
                TestRow("binance", tests["binance"]) { vm.testBinance() }
                HorizontalDivider(color = Tokens.BorderSubtle)

                SecretField("Bybit API Key", s.bybitKey) { v -> vm.update { it.copy(bybitKey = v) } }
                SecretField("Bybit Secret", s.bybitSecret) { v -> vm.update { it.copy(bybitSecret = v) } }
                TestRow("bybit", tests["bybit"]) { vm.testBybit() }
                HorizontalDivider(color = Tokens.BorderSubtle)

                PlainField("MT5 Login ID", s.mt5Login) { v -> vm.update { it.copy(mt5Login = v) } }
                SecretField("MT5 Password", s.mt5Password) { v -> vm.update { it.copy(mt5Password = v) } }
                PlainField("MT5 Server (jaise ICMarkets-Demo)", s.mt5Server) { v -> vm.update { it.copy(mt5Server = v) } }
                TestRow("mt5", tests["mt5"]) { vm.testMt5() }
            }

            }
            if (settingsTab == 3) {
            // ================= ④ BACKEND =================
            SectionCard(
                step = "④",
                title = "Backend (optional)",
                subtitle = "Apna TradingAgentArmy backend server ho to URL daalein — chat pehle wahan jayega."
            ) {
                PlainField("REST URL (jaise http://192.168.1.100:8000)", s.backendUrl) { v ->
                    vm.update { it.copy(backendUrl = v) }
                }
                PlainField("WebSocket URL (jaise ws://192.168.1.100:8000/ws)", s.wsUrl) { v ->
                    vm.update { it.copy(wsUrl = v) }
                }
                SecretField("Auth Bearer Token", s.bearerToken) { v -> vm.update { it.copy(bearerToken = v) } }
                TestRow("backend", tests["backend"]) { vm.testBackend() }
            }

            }
            if (settingsTab == 4) {
                SectionCard(
                    step = "A",
                    title = "Agent Army + HFT (24/7)",
                    subtitle = "14 AI agents milkar market dekhte hain, debate karte hain, trade decision lete hain."
                ) {
                    PlainField("Trading symbol (jaise BTCUSDT)", s.armySymbol) { v ->
                        vm.update { it.copy(armySymbol = v) }
                    }
                    SwitchRow("24/7 auto rounds (Army screen se START karein)", s.agentsEnabled) { v ->
                        vm.update { it.copy(agentsEnabled = v) }
                    }
                    SwitchRow("⚡ HFT Scalper engine (fast loop, paper)", s.hftEnabled) { v ->
                        vm.update { it.copy(hftEnabled = v) }
                    }
                    HorizontalDivider(color = Tokens.BorderSubtle)
                    SwitchRow("🔴 LIVE trading — real money (Exchange tab me Bitget keys chahiye)", s.liveTrading) { v ->
                        vm.update { it.copy(liveTrading = v) }
                    }
                    Text(
                        "Default PAPER mode hai — koi asli paisa nahi lagta. LIVE on karne se pehle " +
                            "risk rules samajh lein: daily loss limit aur kill-switch Army screen par milte hain.",
                        color = Tokens.AccentDanger,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (settingsTab == 5) {
            SectionCard(
                step = "⑤",
                title = "App Color + Notifications",
                subtitle = "App ka theme chunein — White (default), Dark ya AMOLED Black."
            ) {
                Text("Theme", color = Tokens.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip("⚪ White", s.themeMode == "light") { vm.update { it.copy(themeMode = "light") } }
                    ThemeChip("🌙 Dark", s.themeMode == "dark") { vm.update { it.copy(themeMode = "dark") } }
                    ThemeChip("⚫ AMOLED", s.themeMode == "amoled") { vm.update { it.copy(themeMode = "amoled") } }
                }
                HorizontalDivider(color = Tokens.BorderSubtle)

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
                    Text("Chart/price refresh: ", color = Tokens.TextSecondary, style = MaterialTheme.typography.bodySmall)
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
                        "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Firebase: sqlrrr",
                        color = Tokens.TextSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = AppFonts.Mono)
                    )
                    TextButton(onClick = { vm.askAbout() }) { Text("About") }
                }
            }

            // ================= DANGER ZONE =================
            Surface(
                color = Tokens.AccentDanger.copy(alpha = 0.07f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("⚠️ Danger Zone", color = Tokens.AccentDanger, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Sabhi saved keys device se delete ho jayengi.",
                        color = Tokens.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = { vm.clearAllKeys() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Clear All Keys", color = Tokens.AccentDanger)
                    }
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
                    Text("AI chain: Gemini → DeepSeek → OpenAI → Claude → Ollama → offline", color = Tokens.AccentPrimary, style = MaterialTheme.typography.bodySmall)
                    Text("Firebase: sqlrrr · Firestore synced", color = Tokens.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissAbout() }) { Text("Close", color = Tokens.AccentPrimary) }
            }
        )
    }
}

// ---------------------------------------------------------------- helpers

@Composable
private fun SectionCard(
    step: String,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(color = Tokens.Surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    step,
                    color = Tokens.AccentPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column {
                    Text(title, color = Tokens.TextPrimary, style = MaterialTheme.typography.titleSmall)
                    Text(subtitle, color = Tokens.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider(color = Tokens.BorderSubtle)
            content()
        }
    }
}

@Composable
private fun ProviderCard(
    name: String,
    hint: String,
    key: String,
    onKey: (String) -> Unit,
    model: String,
    models: List<String>,
    onModel: (String) -> Unit,
    testResult: String?,
    onTest: () -> Unit,
    keyLabel: String = "API Key",
    isSecret: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, color = Tokens.TextPrimary, style = MaterialTheme.typography.labelLarge)
                Text(hint, color = Tokens.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            if (key.isNotBlank()) {
                Text("● saved", color = Tokens.AccentPrimary, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (isSecret) {
            SecretField(keyLabel, key, onKey)
        } else {
            PlainField(keyLabel, key, onKey)
        }
        DropdownField("Model", model, models, onModel)
        TestRow(name.substringBefore(" ").substringBefore("("), testResult, onTest)
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
private fun TestRow(label: String, result: String?, onTest: () -> Unit) {
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
            Text("🔌 Test", color = Tokens.AccentPrimary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) Tokens.AccentPrimary.copy(alpha = 0.12f) else Color.Transparent,
            contentColor = if (selected) Tokens.AccentPrimary else Tokens.TextSecondary
        )
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
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
                checkedThumbColor = Tokens.Surface
            )
        )
    }
}
