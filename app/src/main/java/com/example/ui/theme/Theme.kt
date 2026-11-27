package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class StudioThemeMode(val label: String) {
    OLED_DARK("OLED Dark"),
    STUDIO_DARK("Studio Dark"),
    DAYLIGHT("Daylight");

    companion object {
        fun fromRaw(raw: String?): StudioThemeMode =
            entries.firstOrNull { it.name == raw } ?: OLED_DARK
    }
}

private val OledDarkColorScheme = darkColorScheme(
    primary = CinemaCoral,
    onPrimary = Color(0xFF1A0703),
    primaryContainer = CinemaCoralContainer,
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = PlayheadCyan,
    onSecondary = Color(0xFF00232B),
    secondaryContainer = PlayheadCyanContainer,
    onSecondaryContainer = Color(0xFFC2F5FF),
    tertiary = CaptionViolet,
    onTertiary = Color(0xFF1C0C3E),
    tertiaryContainer = CaptionVioletContainer,
    onTertiaryContainer = Color(0xFFE7DDFF),
    background = OledBackground,
    onBackground = OnDarkPrimary,
    surface = OledSurface,
    onSurface = OnDarkPrimary,
    surfaceVariant = OledSurfaceContainer,
    onSurfaceVariant = OnDarkSecondary,
    surfaceContainerLowest = OledBackground,
    surfaceContainerLow = OledSurface,
    surfaceContainer = OledSurfaceContainer,
    surfaceContainerHigh = OledSurfaceHigh,
    surfaceContainerHighest = Color(0xFF253044),
    outline = OledOutline,
    outlineVariant = Color(0xFF1B2333),
    error = AudioCrimson,
    onError = Color.White
)

private val StudioDarkColorScheme = darkColorScheme(
    primary = CinemaCoral,
    onPrimary = Color(0xFF1A0703),
    primaryContainer = CinemaCoralContainer,
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = PlayheadCyan,
    onSecondary = Color(0xFF00232B),
    secondaryContainer = PlayheadCyanContainer,
    onSecondaryContainer = Color(0xFFC2F5FF),
    tertiary = CaptionViolet,
    onTertiary = Color(0xFF1C0C3E),
    tertiaryContainer = CaptionVioletContainer,
    onTertiaryContainer = Color(0xFFE7DDFF),
    background = StudioDarkBackground,
    onBackground = OnDarkPrimary,
    surface = StudioDarkSurface,
    onSurface = OnDarkPrimary,
    surfaceVariant = StudioDarkSurfaceContainer,
    onSurfaceVariant = OnDarkSecondary,
    surfaceContainerLowest = StudioDarkBackground,
    surfaceContainerLow = StudioDarkSurface,
    surfaceContainer = StudioDarkSurfaceContainer,
    surfaceContainerHigh = StudioDarkSurfaceHigh,
    surfaceContainerHighest = Color(0xFF344057),
    outline = StudioDarkOutline,
    outlineVariant = Color(0xFF273042),
    error = AudioCrimson,
    onError = Color.White
)

private val DaylightColorScheme = lightColorScheme(
    primary = CinemaCoralDark,
    onPrimary = Color.White,
    primaryContainer = CinemaCoralContainerLight,
    onPrimaryContainer = Color(0xFF4A1205),
    secondary = PlayheadCyanDark,
    onSecondary = Color.White,
    secondaryContainer = PlayheadCyanContainerLight,
    onSecondaryContainer = Color(0xFF053340),
    tertiary = CaptionVioletDark,
    onTertiary = Color.White,
    tertiaryContainer = CaptionVioletContainerLight,
    onTertiaryContainer = Color(0xFF2E1065),
    background = DaylightBackground,
    onBackground = OnLightPrimary,
    surface = DaylightSurface,
    onSurface = OnLightPrimary,
    surfaceVariant = DaylightSurfaceContainer,
    onSurfaceVariant = OnLightSecondary,
    surfaceContainerLowest = DaylightSurface,
    surfaceContainerLow = DaylightBackground,
    surfaceContainer = DaylightSurfaceContainer,
    surfaceContainerHigh = DaylightSurfaceHigh,
    surfaceContainerHighest = Color(0xFFD0D9E8),
    outline = DaylightOutline,
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FrameCutTheme(
    themeMode: StudioThemeMode = StudioThemeMode.OLED_DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        StudioThemeMode.OLED_DARK -> OledDarkColorScheme
        StudioThemeMode.STUDIO_DARK -> StudioDarkColorScheme
        StudioThemeMode.DAYLIGHT -> DaylightColorScheme
    }

    val isDark = themeMode != StudioThemeMode.DAYLIGHT
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = StudioShapes,
        content = content
    )
}
