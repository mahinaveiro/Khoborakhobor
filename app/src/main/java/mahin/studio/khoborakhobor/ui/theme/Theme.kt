package mahin.studio.khoborakhobor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = EditorialBlack,
    secondary = Color(0xFFA8A29A),
    onSecondary = Color(0xFF080808),
    background = Color(0xFF080808),
    onBackground = Color(0xFFF5F2EC),
    surface = Color(0xFF151515),
    onSurface = Color(0xFFF5F2EC),
    surfaceVariant = Color(0xFF1D1D1D),
    onSurfaceVariant = Color(0xFFA8A29A),
    outline = Color(0xFF2A2A2A)
)

private val LightColorScheme = lightColorScheme(
    primary = EditorialBlack,
    onPrimary = Color.White,
    secondary = EditorialInk,
    onSecondary = Color.White,
    background = EditorialSoft,
    onBackground = EditorialInk,
    surface = EditorialSurface,
    onSurface = EditorialInk,
    surfaceVariant = Color(0xFFECE5DA),
    onSurfaceVariant = EditorialGray,
    outline = EditorialLine
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
