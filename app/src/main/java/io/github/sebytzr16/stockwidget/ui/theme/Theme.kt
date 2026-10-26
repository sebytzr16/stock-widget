package io.github.sebytzr16.stockwidget.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.github.sebytzr16.stockwidget.core.Settings
import io.github.sebytzr16.stockwidget.core.ThemeMode

/** Colors outside Material's scheme: price direction, tinted cards and tertiary text. */
@Immutable
data class Palette(
    val up: Color,
    val down: Color,
    val upTint: Color,
    val downTint: Color,
    val upLine: Color,
    val downLine: Color,
    val text3: Color,
    val sheet: Color,
    val dark: Boolean,
)

private val LightPalette = Palette(
    up = Color(0xFF12783A), down = Color(0xFFC42B2F),
    upTint = Color(0xFFE9F6ED), downTint = Color(0xFFFCEDED),
    upLine = Color(0xFF1E9E4A), downLine = Color(0xFFE0484E),
    text3 = Color(0xFF626765), sheet = Color(0xFFFFFFFF), dark = false,
)

private val DarkPalette = Palette(
    up = Color(0xFF45C97A), down = Color(0xFFF2706F),
    upTint = Color(0xFF13291C), downTint = Color(0xFF2E191A),
    upLine = Color(0xFF45C97A), downLine = Color(0xFFF2706F),
    text3 = Color(0xFF8D9390), sheet = Color(0xFF161918), dark = true,
)

private val BlackPalette = DarkPalette.copy(
    upTint = Color(0xFF0A1F12), downTint = Color(0xFF230F10), sheet = Color(0xFF0B0C0C),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6E6E), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4ECEA), onPrimaryContainer = Color(0xFF0A4747),
    secondary = Color(0xFF0F6E6E), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4ECEA), onSecondaryContainer = Color(0xFF0A4747),
    background = Color(0xFFFAFAF9), onBackground = Color(0xFF141615),
    surface = Color(0xFFFAFAF9), onSurface = Color(0xFF141615),
    surfaceVariant = Color(0xFFF0F0EE), onSurfaceVariant = Color(0xFF575C5A),
    surfaceTint = Color(0xFF0F6E6E),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF5F5F3),
    surfaceContainer = Color(0xFFF0F0EE), surfaceContainerHigh = Color(0xFFE6E7E4),
    surfaceContainerHighest = Color(0xFFDFE0DD),
    outline = Color(0xFFD4D6D3), outlineVariant = Color(0xFFE7E8E5),
    inverseSurface = Color(0xFF2B2F2E), inverseOnSurface = Color(0xFFF1F2F1), inversePrimary = Color(0xFF8EDBD6),
    error = Color(0xFFC42B2F), onError = Color(0xFFFFFFFF),
    scrim = Color(0xFF141615),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5CC8C3), onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF163F3D), onPrimaryContainer = Color(0xFFB2EEEA),
    secondary = Color(0xFF5CC8C3), onSecondary = Color(0xFF003735),
    secondaryContainer = Color(0xFF163F3D), onSecondaryContainer = Color(0xFFB2EEEA),
    background = Color(0xFF0E1010), onBackground = Color(0xFFECEEED),
    surface = Color(0xFF0E1010), onSurface = Color(0xFFECEEED),
    surfaceVariant = Color(0xFF1C1F1E), onSurfaceVariant = Color(0xFFA6ACA9),
    surfaceTint = Color(0xFF5CC8C3),
    surfaceContainerLowest = Color(0xFF090B0B), surfaceContainerLow = Color(0xFF161918),
    surfaceContainer = Color(0xFF1C1F1E), surfaceContainerHigh = Color(0xFF262A29),
    surfaceContainerHighest = Color(0xFF2F3332),
    outline = Color(0xFF3B403E), outlineVariant = Color(0xFF262A29),
    inverseSurface = Color(0xFFE6E8E7), inverseOnSurface = Color(0xFF1A1C1C), inversePrimary = Color(0xFF0F6E6E),
    error = Color(0xFFF2706F), onError = Color(0xFF3A0B0B),
    scrim = Color(0xFF000000),
)

private val BlackColors = DarkColors.copy(
    background = Color(0xFF000000), surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF131515),
    surfaceContainerLowest = Color(0xFF000000), surfaceContainerLow = Color(0xFF0B0C0C),
    surfaceContainer = Color(0xFF131515), surfaceContainerHigh = Color(0xFF1C1F1E),
    surfaceContainerHighest = Color(0xFF252928),
    outline = Color(0xFF333836), outlineVariant = Color(0xFF1C1F1E),
    primaryContainer = Color(0xFF123533), secondaryContainer = Color(0xFF123533),
)

/** Tabular figures everywhere so prices line up and don't jitter while updating. */
private fun TextStyle.tabular(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) =
    copy(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.sp, fontFeatureSettings = "tnum")

private val base = Typography()

val AppTypography = Typography(
    displaySmall = base.displaySmall.tabular(40, 48, FontWeight.Medium, -0.8f),
    headlineLarge = base.headlineLarge.tabular(32, 40, FontWeight.Medium, -0.6f),
    headlineMedium = base.headlineMedium.tabular(28, 34, FontWeight.SemiBold, -0.3f),
    headlineSmall = base.headlineSmall.tabular(22, 28, FontWeight.Medium),
    titleLarge = base.titleLarge.tabular(20, 26, FontWeight.SemiBold),
    titleMedium = base.titleMedium.tabular(17, 22, FontWeight.SemiBold),
    titleSmall = base.titleSmall.tabular(16, 22, FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.tabular(16, 22, FontWeight.Normal),
    bodyMedium = base.bodyMedium.tabular(14, 20, FontWeight.Normal),
    bodySmall = base.bodySmall.tabular(13, 18, FontWeight.Normal),
    labelLarge = base.labelLarge.tabular(14, 20, FontWeight.Medium),
    labelMedium = base.labelMedium.tabular(13, 18, FontWeight.Medium),
    labelSmall = base.labelSmall.tabular(12, 16, FontWeight.Normal),
)

@Composable
fun isDark(settings: Settings): Boolean = when (settings.theme) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun StockTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = isDark(settings)
    val black = dark && settings.pureBlack
    val context = LocalContext.current
    val scheme = when {
        black -> BlackColors
        dark -> DarkColors
        else -> LightColors
    }
    val colors = if (settings.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        // Wallpaper accent only: neutrals and the green/red price colors stay fixed.
        val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        scheme.copy(
            primary = dynamic.primary, onPrimary = dynamic.onPrimary,
            primaryContainer = dynamic.primaryContainer, onPrimaryContainer = dynamic.onPrimaryContainer,
            secondary = dynamic.primary, onSecondary = dynamic.onPrimary,
            secondaryContainer = dynamic.primaryContainer, onSecondaryContainer = dynamic.onPrimaryContainer,
            inversePrimary = dynamic.inversePrimary, surfaceTint = dynamic.primary,
        )
    } else scheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val palette = when {
        black -> BlackPalette
        dark -> DarkPalette
        else -> LightPalette
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
    }
}
