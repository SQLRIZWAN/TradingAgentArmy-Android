package com.rizwan.tradingagentarmy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private val DarkColors = darkColorScheme(
    primary = Tokens.AccentPrimary,
    onPrimary = Tokens.BackgroundBase,
    secondary = Tokens.AccentWarning,
    onSecondary = Tokens.BackgroundBase,
    error = Tokens.AccentDanger,
    onError = Tokens.TextPrimary,
    background = Tokens.BackgroundBase,
    onBackground = Tokens.TextPrimary,
    surface = Tokens.Surface,
    onSurface = Tokens.TextPrimary,
    surfaceVariant = Tokens.SurfaceElevated,
    onSurfaceVariant = Tokens.TextSecondary,
    outline = Tokens.BorderSubtle
)

object ThemeController {
    var amoled by mutableStateOf(false)
}

@Composable
fun TradingTheme(content: @Composable () -> Unit) {
    val colors = if (ThemeController.amoled) {
        DarkColors.copy(
            background = Tokens.AmoledBackground,
            surface = Tokens.AmoledSurface,
            surfaceVariant = Tokens.AmoledBackground
        )
    } else DarkColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
