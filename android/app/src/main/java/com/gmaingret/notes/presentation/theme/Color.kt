package com.gmaingret.notes.presentation.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Seed color: #2563EB (blue-600)
// Material 3 tonal palette generated from this seed

// Primary tones
val Blue600 = Color(0xFF2563EB)
val Blue700 = Color(0xFF1D4ED8)
val Blue100 = Color(0xFFDBEAFE)
val Blue50  = Color(0xFFEFF6FF)
val BlueContainer = Color(0xFFD3E3FF)

// Neutral tones
val NeutralVariant50 = Color(0xFF6B7A90)
val Surface = Color(0xFFF8F9FE)
val SurfaceVariant = Color(0xFFE1E5EE)
val OnSurfaceVariant = Color(0xFF44495C)
val Outline = Color(0xFF747882)

// Dark tones
val Blue200 = Color(0xFF93C5FD)
val DarkSurface = Color(0xFF111318)
val DarkSurfaceVariant = Color(0xFF44495C)
val DarkOnSurfaceVariant = Color(0xFFC4C8D7)
val DarkOutline = Color(0xFF8E9099)

val LightColorScheme = lightColorScheme(
    primary = Blue600,
    onPrimary = Color.White,
    primaryContainer = BlueContainer,
    onPrimaryContainer = Color(0xFF00174B),
    secondary = Color(0xFF555F72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E3F9),
    onSecondaryContainer = Color(0xFF121C2C),
    tertiary = Color(0xFF6F5674),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF9D8FD),
    onTertiaryContainer = Color(0xFF28132E),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF9FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFAF9FF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = Color(0xFFC3C7D0),
    scrim = Color.Black,
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    inversePrimary = Color(0xFFA8C8FF),
)

val DarkColorScheme = darkColorScheme(
    primary = Blue200,
    onPrimary = Color(0xFF002E6E),
    primaryContainer = Blue700,
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBCC7DD),
    onSecondary = Color(0xFF26303E),
    secondaryContainer = Color(0xFF3C4759),
    onSecondaryContainer = Color(0xFFD9E3F9),
    tertiary = Color(0xFFDCBCE0),
    onTertiary = Color(0xFF3E2744),
    tertiaryContainer = Color(0xFF563D5B),
    onTertiaryContainer = Color(0xFFF9D8FD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = DarkSurface,
    onBackground = Color(0xFFE3E2EA),
    surface = DarkSurface,
    onSurface = Color(0xFFE3E2EA),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = Color(0xFF44495C),
    scrim = Color.Black,
    inverseSurface = Color(0xFFE3E2EA),
    inverseOnSurface = Color(0xFF2F3036),
    inversePrimary = Blue600,
)
