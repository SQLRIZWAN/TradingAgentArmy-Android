package com.rizwan.tradingagentarmy.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Mutable design tokens. Default = WHITE (light) theme.
 * ThemeController.applyTheme() switches palettes at runtime; every screen
 * reads these as snapshot state so it recomposes automatically.
 */
object Tokens {
    var BackgroundBase by mutableStateOf(Color(0xFFF6F7F9))
    var Surface by mutableStateOf(Color(0xFFFFFFFF))
    var SurfaceElevated by mutableStateOf(Color(0xFFEDF0F4))
    var BorderSubtle by mutableStateOf(Color(0xFFDDE2E9))
    var AccentPrimary by mutableStateOf(Color(0xFF00A884))
    var AccentDanger by mutableStateOf(Color(0xFFE5484D))
    var AccentWarning by mutableStateOf(Color(0xFFD97706))
    var TextPrimary by mutableStateOf(Color(0xFF14171A))
    var TextSecondary by mutableStateOf(Color(0xFF5F6B7A))
    var UserBubble by mutableStateOf(Color(0xFFD7F5EC))

    // dark palette (used by dark mode)
    internal val DarkBackground = Color(0xFF0A0C0F)
    internal val DarkSurface = Color(0xFF111318)
    internal val DarkSurfaceElevated = Color(0xFF161A21)
    internal val DarkBorder = Color(0xFF1E2229)
    internal val DarkTextPrimary = Color(0xFFE8EAED)
    internal val DarkTextSecondary = Color(0xFF8A9BB0)
    internal val DarkUserBubble = Color(0xFF1E3A4A)
    internal val BrightAccent = Color(0xFF00D4AA)
    internal val BrightDanger = Color(0xFFFF4757)
    internal val BrightWarning = Color(0xFFFFB347)

    // amoled palette
    internal val AmoledBackground = Color(0xFF000000)
    internal val AmoledSurface = Color(0xFF0A0A0B)
}
