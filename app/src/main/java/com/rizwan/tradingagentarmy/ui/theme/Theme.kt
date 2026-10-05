package com.rizwan.tradingagentarmy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * mode: "light" (white, default) | "dark" | "amoled"
 */
object ThemeController {
    var mode by mutableStateOf("light")

    /** Kept for backward compatibility (Settings old field). */
    var amoled: Boolean
        get() = mode == "amoled"
        set(v) { if (v) mode = "amoled" else if (mode == "amoled") mode = "dark" }

    fun applyTheme(newMode: String) {
        mode = when (newMode) {
            "dark", "amoled" -> newMode
            else -> "light"
        }
        when (mode) {
            "light" -> {
                Tokens.BackgroundBase = Color(0xFFF6F7F9)
                Tokens.Surface = Color(0xFFFFFFFF)
                Tokens.SurfaceElevated = Color(0xFFEDF0F4)
                Tokens.BorderSubtle = Color(0xFFDDE2E9)
                Tokens.AccentPrimary = Color(0xFF00A884)
                Tokens.AccentDanger = Color(0xFFE5484D)
                Tokens.AccentWarning = Color(0xFFD97706)
                Tokens.TextPrimary = Color(0xFF14171A)
                Tokens.TextSecondary = Color(0xFF5F6B7A)
                Tokens.UserBubble = Color(0xFFD7F5EC)
            }
            "amoled" -> {
                Tokens.BackgroundBase = Tokens.AmoledBackground
                Tokens.Surface = Tokens.AmoledSurface
                Tokens.SurfaceElevated = Color(0xFF101012)
                Tokens.BorderSubtle = Color(0xFF1A1A1C)
                Tokens.AccentPrimary = Tokens.BrightAccent
                Tokens.AccentDanger = Tokens.BrightDanger
                Tokens.AccentWarning = Tokens.BrightWarning
                Tokens.TextPrimary = Tokens.DarkTextPrimary
                Tokens.TextSecondary = Tokens.DarkTextSecondary
                Tokens.UserBubble = Tokens.DarkUserBubble
            }
            else -> {
                Tokens.BackgroundBase = Tokens.DarkBackground
                Tokens.Surface = Tokens.DarkSurface
                Tokens.SurfaceElevated = Tokens.DarkSurfaceElevated
                Tokens.BorderSubtle = Tokens.DarkBorder
                Tokens.AccentPrimary = Tokens.BrightAccent
                Tokens.AccentDanger = Tokens.BrightDanger
                Tokens.AccentWarning = Tokens.BrightWarning
                Tokens.TextPrimary = Tokens.DarkTextPrimary
                Tokens.TextSecondary = Tokens.DarkTextSecondary
                Tokens.UserBubble = Tokens.DarkUserBubble
            }
        }
    }
}

private val DarkScheme = darkColorScheme(
    primary = Tokens.BrightAccent,
    onPrimary = Color(0xFF0A0C0F),
    secondary = Tokens.BrightWarning,
    onSecondary = Color(0xFF0A0C0F),
    error = Tokens.BrightDanger,
    onError = Color(0xFFE8EAED),
    background = Tokens.DarkBackground,
    onBackground = Tokens.DarkTextPrimary,
    surface = Tokens.DarkSurface,
    onSurface = Tokens.DarkTextPrimary,
    surfaceVariant = Tokens.DarkSurfaceElevated,
    onSurfaceVariant = Tokens.DarkTextSecondary,
    outline = Tokens.DarkBorder
)

private val AmoledScheme = DarkScheme.copy(
    background = Tokens.AmoledBackground,
    surface = Tokens.AmoledSurface,
    surfaceVariant = Tokens.AmoledBackground
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF00A884),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFD97706),
    onSecondary = Color(0xFFFFFFFF),
    error = Color(0xFFE5484D),
    onError = Color(0xFFFFFFFF),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF14171A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14171A),
    surfaceVariant = Color(0xFFEDF0F4),
    onSurfaceVariant = Color(0xFF5F6B7A),
    outline = Color(0xFFDDE2E9)
)

@Composable
fun TradingTheme(content: @Composable () -> Unit) {
    val colors = when (ThemeController.mode) {
        "light" -> LightScheme
        "amoled" -> AmoledScheme
        else -> DarkScheme
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
