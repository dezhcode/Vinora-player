package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.PersianStrings

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

val LocalPlinthColors = staticCompositionLocalOf { LightPlinthPalette }
val LocalAppStrings = staticCompositionLocalOf { EnglishStrings }

private val PlinthLightColorScheme = lightColorScheme(
    primary = StudioAccent,
    onPrimary = StudioCapsuleBg,
    secondary = StudioInkSecondary,
    onSecondary = StudioCapsuleBg,
    background = StudioCanvasTop,
    onBackground = StudioInkPrimary,
    surface = StudioCapsuleBg,
    onSurface = StudioInkPrimary,
    surfaceVariant = StudioPlinthBottom,
    onSurfaceVariant = StudioInkSecondary,
    outline = StudioHairline,
)

private val PlinthDarkColorScheme = darkColorScheme(
    primary = TitaniumAccent,
    onPrimary = TitaniumCanvasBottom,
    secondary = TitaniumInkSecondary,
    onSecondary = TitaniumCanvasBottom,
    background = TitaniumCanvasTop,
    onBackground = TitaniumInkPrimary,
    surface = TitaniumCapsuleBg,
    onSurface = TitaniumInkPrimary,
    surfaceVariant = TitaniumPlinthBottom,
    onSurfaceVariant = TitaniumInkSecondary,
    outline = TitaniumHairline,
)

@Composable
fun PlinthTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    language: AppLanguage = AppLanguage.EN,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = if (useDark) DarkPlinthPalette else LightPlinthPalette
    val colorScheme = if (useDark) PlinthDarkColorScheme else PlinthLightColorScheme
    val isPersian = language == AppLanguage.FA
    val strings = if (isPersian) PersianStrings else EnglishStrings
    val layoutDirection = if (isPersian) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalPlinthColors provides palette,
        LocalAppStrings provides strings,
        LocalLayoutDirection provides layoutDirection,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = plinthTypography(isPersian),
            content = content,
        )
    }
}
