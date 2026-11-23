package com.irontrack.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.irontrack.app.data.repo.ThemePref

val Accent = Color(0xFFFF6B2C)
val SuccessGreen = Color(0xFF4CAF7A)
val WarningAmber = Color(0xFFE0A030)
val MissedRed = Color(0xFFE5534B)
val WeekAColor = Color(0xFF4F8EF7)
val WeekBColor = Color(0xFFB46BF2)

private val Dark = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A0A00),
    primaryContainer = Color(0xFF5A2A10),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = Color(0xFF9FB4D8),
    onSecondary = Color(0xFF0D1B33),
    secondaryContainer = Color(0xFF263449),
    onSecondaryContainer = Color(0xFFD6E3FF),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE4E2E6),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFE4E2E6),
    surfaceVariant = Color(0xFF2A2C31),
    onSurfaceVariant = Color(0xFFC4C6CF),
    surfaceContainer = Color(0xFF1C1D21),
    surfaceContainerHigh = Color(0xFF24262B),
    surfaceContainerHighest = Color(0xFF2E3036),
    surfaceContainerLow = Color(0xFF18191D),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44464E),
    error = MissedRed,
)

private val Light = lightColorScheme(
    primary = Color(0xFFC2470F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCB),
    onPrimaryContainer = Color(0xFF3A1300),
    secondary = Color(0xFF465E8A),
    secondaryContainer = Color(0xFFD6E3FF),
    background = Color(0xFFFBF8F6),
    surface = Color(0xFFFBF8F6),
    surfaceVariant = Color(0xFFEDE7E3),
    error = Color(0xFFBA1A1A),
)

@Composable
fun IronTrackTheme(theme: ThemePref, content: @Composable () -> Unit) {
    val dark = when (theme) {
        ThemePref.DARK -> true
        ThemePref.LIGHT -> false
        ThemePref.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
