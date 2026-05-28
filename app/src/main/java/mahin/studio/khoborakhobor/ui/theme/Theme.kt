package mahin.studio.khoborakhobor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import mahin.studio.khoborakhobor.ThemeManager

private val DarkPalette = ThemeManager.dark
private val LightPalette = ThemeManager.light

private val DarkColorScheme = darkColorScheme(
    primary = Color(DarkPalette.buttonBg),
    onPrimary = Color(DarkPalette.buttonText),
    secondary = Color(DarkPalette.secondaryText),
    onSecondary = Color(DarkPalette.buttonText),
    background = Color(DarkPalette.appBackground),
    onBackground = Color(DarkPalette.primaryText),
    surface = Color(DarkPalette.cardBackground),
    onSurface = Color(DarkPalette.primaryText),
    surfaceVariant = Color(DarkPalette.surfaceAlt),
    onSurfaceVariant = Color(DarkPalette.secondaryText),
    outline = Color(DarkPalette.cardBorder)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(LightPalette.buttonBg),
    onPrimary = Color(LightPalette.buttonText),
    secondary = Color(LightPalette.primaryText),
    onSecondary = Color(LightPalette.buttonText),
    background = Color(LightPalette.appBackground),
    onBackground = Color(LightPalette.primaryText),
    surface = Color(LightPalette.cardBackground),
    onSurface = Color(LightPalette.primaryText),
    surfaceVariant = Color(LightPalette.surfaceAlt),
    onSurfaceVariant = Color(LightPalette.secondaryText),
    outline = Color(LightPalette.cardBorder)
)

@Composable
fun KhoborakhoborTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
