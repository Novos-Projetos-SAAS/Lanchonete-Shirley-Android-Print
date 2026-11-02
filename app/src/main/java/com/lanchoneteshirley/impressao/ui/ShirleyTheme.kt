package com.lanchoneteshirley.impressao.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF9F3426), onPrimary = Color.White, primaryContainer = Color(0xFFFFDAD3), onPrimaryContainer = Color(0xFF3C0803),
    secondary = Color(0xFF725A2C), onSecondary = Color.White, background = Color(0xFFFFF8F3), onBackground = Color(0xFF231917),
    surface = Color(0xFFFFF8F3), surfaceVariant = Color(0xFFF3DDD8), outline = Color(0xFF8D716B), error = Color(0xFFBA1A1A),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFFFB4A7), onPrimary = Color(0xFF5F150C), primaryContainer = Color(0xFF7F2118), onPrimaryContainer = Color(0xFFFFDAD3),
    secondary = Color(0xFFE1C17A), onSecondary = Color(0xFF3E2E04), background = Color(0xFF1B1110), onBackground = Color(0xFFF2DFDB),
    surface = Color(0xFF1B1110), surfaceVariant = Color(0xFF53433F), outline = Color(0xFFD7C1BC), error = Color(0xFFFFB4AB),
)

@Composable fun ShirleyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Typography(), content = content)
}
