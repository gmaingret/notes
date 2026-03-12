package com.gmaingret.notes.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Notes app Material 3 theme.
 *
 * Uses system dark mode preference (isSystemInDarkTheme) — no manual toggle.
 * Seed color: #2563EB (blue-600).
 * Edge-to-edge is enabled in MainActivity via enableEdgeToEdge().
 */
@Composable
fun NotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
