package com.gmaingret.notes.widget

import androidx.glance.material3.ColorProviders
import com.gmaingret.notes.presentation.theme.DarkColorScheme
import com.gmaingret.notes.presentation.theme.LightColorScheme

/**
 * Bridges the app's Material 3 color schemes into Glance's ColorProviders.
 *
 * Used inside provideContent:
 *   GlanceTheme(colors = NotesWidgetColorScheme.colors) { ... }
 *
 * The widget will automatically switch between light and dark palettes
 * based on the system's current dark mode setting — no manual checking needed.
 */
object NotesWidgetColorScheme {
    val colors = ColorProviders(
        light = LightColorScheme,
        dark = DarkColorScheme
    )
}
