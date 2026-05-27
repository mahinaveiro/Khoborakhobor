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
    secondary = Color(0xFFCFCFCF),
    onSecondary = EditorialBlack,
    background = Color(0xFF090909),
    onBackground = Color.White,
    surface = Color(0xFF141414),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF202020),
    onSurfaceVariant = Color(0xFFD7D7D7),
    outline = Color(0xFF333333)
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
    surfaceVariant = Color(0xFFF0F0EF),
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
