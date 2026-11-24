package com.irontrack.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.irontrack.app.data.repo.AccentColor
import com.irontrack.app.data.repo.ThemePref

val SuccessGreen = Color(0xFF4CAF7A)
val WarningAmber = Color(0xFFE0A030)
val MissedRed = Color(0xFFE5534B)
val WeekAColor = Color(0xFF4F8EF7)
val WeekBColor = Color(0xFFB46BF2)

/** Déclinaisons d'une couleur d'accent pour les thèmes sombre et clair. */
data class AccentPalette(
    val swatch: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
    val lightPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
)

fun AccentColor.palette(): AccentPalette = when (this) {
    AccentColor.ORANGE -> AccentPalette(Color(0xFFFF6B2C), Color(0xFFFF6B2C), Color(0xFF1A0A00), Color(0xFF5A2A10), Color(0xFFFFDBCB), Color(0xFFC2470F), Color(0xFFFFDBCB), Color(0xFF3A1300))
    AccentColor.RED -> AccentPalette(Color(0xFFEF4444), Color(0xFFFF5C5C), Color(0xFF2A0000), Color(0xFF6B1A1A), Color(0xFFFFDAD6), Color(0xFFC62828), Color(0xFFFFDAD6), Color(0xFF410002))
    AccentColor.PINK -> AccentPalette(Color(0xFFEC4899), Color(0xFFFF6BB5), Color(0xFF2A0018), Color(0xFF6A1F46), Color(0xFFFFD8E8), Color(0xFFB8256F), Color(0xFFFFD8E8), Color(0xFF3E0022))
    AccentColor.PURPLE -> AccentPalette(Color(0xFF8B5CF6), Color(0xFFA78BFA), Color(0xFF1A0B3D), Color(0xFF3F2A78), Color(0xFFE9DDFF), Color(0xFF6D3FD6), Color(0xFFE9DDFF), Color(0xFF22005D))
    AccentColor.BLUE -> AccentPalette(Color(0xFF3B82F6), Color(0xFF6AA3FF), Color(0xFF001A3F), Color(0xFF1E3F73), Color(0xFFD8E2FF), Color(0xFF1F5FCC), Color(0xFFD8E2FF), Color(0xFF001A43))
    AccentColor.CYAN -> AccentPalette(Color(0xFF06B6D4), Color(0xFF3DD5EE), Color(0xFF00222A), Color(0xFF0E4A57), Color(0xFFC6F1FA), Color(0xFF00788C), Color(0xFFC6F1FA), Color(0xFF001F26))
    AccentColor.GREEN -> AccentPalette(Color(0xFF22C55E), Color(0xFF4ADE80), Color(0xFF00210D), Color(0xFF14532D), Color(0xFFC8F5D4), Color(0xFF15803D), Color(0xFFC8F5D4), Color(0xFF002110))
    AccentColor.YELLOW -> AccentPalette(Color(0xFFEAB308), Color(0xFFFACC15), Color(0xFF211A00), Color(0xFF5A4500), Color(0xFFFFEFB0), Color(0xFF8A6A00), Color(0xFFFFEFB0), Color(0xFF261A00))
}

private fun darkScheme(a: AccentPalette, oled: Boolean) = darkColorScheme(
    primary = a.darkPrimary,
    onPrimary = a.darkOnPrimary,
    primaryContainer = a.darkContainer,
    onPrimaryContainer = a.darkOnContainer,
    secondary = Color(0xFF9FB4D8),
    onSecondary = Color(0xFF0D1B33),
    secondaryContainer = Color(0xFF263449),
    onSecondaryContainer = Color(0xFFD6E3FF),
    background = if (oled) Color.Black else Color(0xFF121316),
    onBackground = Color(0xFFE4E2E6),
    surface = if (oled) Color.Black else Color(0xFF121316),
    onSurface = Color(0xFFE4E2E6),
    surfaceVariant = if (oled) Color(0xFF1A1B1F) else Color(0xFF2A2C31),
    onSurfaceVariant = Color(0xFFC4C6CF),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = if (oled) Color(0xFF07070A) else Color(0xFF18191D),
    surfaceContainer = if (oled) Color(0xFF0C0C0F) else Color(0xFF1C1D21),
    surfaceContainerHigh = if (oled) Color(0xFF141519) else Color(0xFF24262B),
    surfaceContainerHighest = if (oled) Color(0xFF1C1D21) else Color(0xFF2E3036),
    surfaceBright = if (oled) Color(0xFF1C1D21) else Color(0xFF34363C),
    surfaceDim = Color.Black,
    outline = Color(0xFF8E9099),
    outlineVariant = if (oled) Color(0xFF2E3036) else Color(0xFF44464E),
    error = MissedRed,
)

private fun lightScheme(a: AccentPalette) = lightColorScheme(
    primary = a.lightPrimary,
    onPrimary = Color.White,
    primaryContainer = a.lightContainer,
    onPrimaryContainer = a.lightOnContainer,
    secondary = Color(0xFF465E8A),
    secondaryContainer = Color(0xFFD6E3FF),
    background = Color(0xFFFBF8F6),
    surface = Color(0xFFFBF8F6),
    surfaceVariant = Color(0xFFEDE7E3),
    error = Color(0xFFBA1A1A),
)

@Composable
fun IronTrackTheme(theme: ThemePref, accent: AccentColor = AccentColor.ORANGE, oledBlack: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (theme) {
        ThemePref.DARK -> true
        ThemePref.LIGHT -> false
        ThemePref.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = accent.palette()
    MaterialTheme(colorScheme = if (dark) darkScheme(palette, oledBlack) else lightScheme(palette), content = content)
}
