package mahin.studio.khoborakhobor

import android.content.res.Configuration
import android.content.res.Resources

internal data class ThemePalette(
    val isDark: Boolean,
    val appBackground: Int,
    val topBarBackground: Int,
    val navBackground: Int,
    val cardBackground: Int,
    val surfaceAlt: Int,
    val cardBorder: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val chipSelectedBg: Int,
    val chipSelectedText: Int,
    val chipUnselectedBg: Int,
    val chipUnselectedText: Int,
    val buttonBg: Int,
    val buttonText: Int,
    val buttonBorder: Int,
    val outlinedButtonBg: Int,
    val outlinedButtonText: Int,
    val outlinedButtonBorder: Int,
    val subtleIcon: Int,
    val switchOffTrack: Int,
    val switchOffThumb: Int
) {
    companion object {
        const val TRANSPARENT: Int = 0x00000000
    }
}

internal object ThemeManager {
    val light = ThemePalette(
        isDark = false,
        appBackground = 0xFFF4EFE6.toInt(),
        topBarBackground = 0xFFF4EFE6.toInt(),
        navBackground = 0xFFF4EFE6.toInt(),
        cardBackground = 0xFFFFFDF8.toInt(),
        surfaceAlt = 0xFFECE5DA.toInt(),
        cardBorder = 0xFFDED6CA.toInt(),
        primaryText = 0xFF17130D.toInt(),
        secondaryText = 0xFF6F675C.toInt(),
        chipSelectedBg = 0xFF17130D.toInt(),
        chipSelectedText = 0xFFFFFDF8.toInt(),
        chipUnselectedBg = 0xFFFFFDF8.toInt(),
        chipUnselectedText = 0xFF17130D.toInt(),
        buttonBg = 0xFF17130D.toInt(),
        buttonText = 0xFFFFFDF8.toInt(),
        buttonBorder = 0xFF17130D.toInt(),
        outlinedButtonBg = ThemePalette.TRANSPARENT,
        outlinedButtonText = 0xFF17130D.toInt(),
        outlinedButtonBorder = 0xFFDED6CA.toInt(),
        subtleIcon = 0xFF8A8175.toInt(),
        switchOffTrack = 0xFFD8D0C4.toInt(),
        switchOffThumb = 0xFF8A8175.toInt()
    )

    val dark = ThemePalette(
        isDark = true,
        appBackground = 0xFF080808.toInt(),
        topBarBackground = 0xFF080808.toInt(),
        navBackground = 0xFF080808.toInt(),
        cardBackground = 0xFF151515.toInt(),
        surfaceAlt = 0xFF1D1D1D.toInt(),
        cardBorder = 0xFF2A2A2A.toInt(),
        primaryText = 0xFFF5F2EC.toInt(),
        secondaryText = 0xFFA8A29A.toInt(),
        chipSelectedBg = 0xFFF5F2EC.toInt(),
        chipSelectedText = 0xFF080808.toInt(),
        chipUnselectedBg = 0xFF151515.toInt(),
        chipUnselectedText = 0xFFF5F2EC.toInt(),
        buttonBg = 0xFFF5F2EC.toInt(),
        buttonText = 0xFF080808.toInt(),
        buttonBorder = 0xFFF5F2EC.toInt(),
        outlinedButtonBg = ThemePalette.TRANSPARENT,
        outlinedButtonText = 0xFFF5F2EC.toInt(),
        outlinedButtonBorder = 0xFF2A2A2A.toInt(),
        subtleIcon = 0xFFA8A29A.toInt(),
        switchOffTrack = 0xFF2D2D2D.toInt(),
        switchOffThumb = 0xFF777777.toInt()
    )

    fun palette(isDark: Boolean): ThemePalette = if (isDark) dark else light

    fun isDark(resources: Resources, preference: ThemePreference): Boolean {
        return when (preference) {
            ThemePreference.System -> {
                val nightMask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMask == Configuration.UI_MODE_NIGHT_YES
            }
            ThemePreference.Light -> false
            ThemePreference.Dark -> true
        }
    }
}
