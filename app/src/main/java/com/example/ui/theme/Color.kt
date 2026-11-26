package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Light Studio Alabaster & Brushed Silver Palette (Reference-faithful)
val StudioCanvasTop = Color(0xFFF6F7FB)
val StudioCanvasBottom = Color(0xFFECEFF6)
val StudioDomeTop = Color(0xFFFBFCFF)
val StudioDomeBottom = Color(0xFFF1F3F9)
val StudioCapsuleBg = Color(0xFFFFFFFF)
val StudioPlinthTop = Color(0xFFF3F5F9)
val StudioPlinthBottom = Color(0xFFE3E7F0)
val StudioPlinthEdgeLight = Color(0xFFFFFFFF)
val StudioPlinthEdgeShadow = Color(0xFFC5CDDC)
val StudioButtonTop = Color(0xFFFCFDFE)
val StudioButtonBottom = Color(0xFFECEFF6)
val StudioButtonPressedTop = Color(0xFFE4E8F1)
val StudioButtonPressedBottom = Color(0xFFF6F8FC)
val StudioShadowDark = Color(0xFFBCC5D6)
val StudioShadowLight = Color(0xFFFFFFFF)
val StudioHairline = Color(0xFFDDE2EC)
val StudioInkPrimary = Color(0xFF111318)
val StudioInkSecondary = Color(0xFF7B8394)
val StudioInkMuted = Color(0xFFA7B0C0)
val StudioVisualizerBar = Color(0xFF9AA4B6)
val StudioVisualizerActive = Color(0xFF535D70)
val StudioAccent = Color(0xFF2B3445)
val StudioWarmBrass = Color(0xFFC49A62)

// Dark Anodized Titanium & Smoked Obsidian Palette (Custom luxury Dark Mode)
val TitaniumCanvasTop = Color(0xFF14171D)
val TitaniumCanvasBottom = Color(0xFF0C0E12)
val TitaniumDomeTop = Color(0xFF1A1E26)
val TitaniumDomeBottom = Color(0xFF101318)
val TitaniumCapsuleBg = Color(0xFF1E222B)
val TitaniumPlinthTop = Color(0xFF282D38)
val TitaniumPlinthBottom = Color(0xFF191D25)
val TitaniumPlinthEdgeLight = Color(0xFF3E4656)
val TitaniumPlinthEdgeShadow = Color(0xFF0A0C10)
val TitaniumButtonTop = Color(0xFF252A34)
val TitaniumButtonBottom = Color(0xFF1A1E26)
val TitaniumButtonPressedTop = Color(0xFF13161C)
val TitaniumButtonPressedBottom = Color(0xFF222731)
val TitaniumShadowDark = Color(0xFF050608)
val TitaniumShadowLight = Color(0xFF323947)
val TitaniumHairline = Color(0xFF2C3240)
val TitaniumInkPrimary = Color(0xFFF2F5FA)
val TitaniumInkSecondary = Color(0xFF8F99AB)
val TitaniumInkMuted = Color(0xFF5D6575)
val TitaniumVisualizerBar = Color(0xFF6B768B)
val TitaniumVisualizerActive = Color(0xFFD6DFEE)
val TitaniumAccent = Color(0xFFDCE4F2)
val TitaniumWarmBrass = Color(0xFFD9B075)

// Shared Vinyl Physical Constants
val FavoriteCoral = Color(0xFFD9535F)

@Immutable
data class PlinthPalette(
    val isDark: Boolean,
    val canvasTop: Color,
    val canvasBottom: Color,
    val domeTop: Color,
    val domeBottom: Color,
    val capsuleBg: Color,
    val plinthTop: Color,
    val plinthBottom: Color,
    val plinthEdgeLight: Color,
    val plinthEdgeShadow: Color,
    val buttonTop: Color,
    val buttonBottom: Color,
    val buttonPressedTop: Color,
    val buttonPressedBottom: Color,
    val shadowDark: Color,
    val shadowLight: Color,
    val hairline: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkMuted: Color,
    val visualizerBar: Color,
    val visualizerActive: Color,
    val accent: Color,
    val warmBrass: Color,
    val favoriteRed: Color,
    val navBarBg: Color,
)

val LightPlinthPalette = PlinthPalette(
    isDark = false,
    canvasTop = StudioCanvasTop,
    canvasBottom = StudioCanvasBottom,
    domeTop = StudioDomeTop,
    domeBottom = StudioDomeBottom,
    capsuleBg = StudioCapsuleBg,
    plinthTop = StudioPlinthTop,
    plinthBottom = StudioPlinthBottom,
    plinthEdgeLight = StudioPlinthEdgeLight,
    plinthEdgeShadow = StudioPlinthEdgeShadow,
    buttonTop = StudioButtonTop,
    buttonBottom = StudioButtonBottom,
    buttonPressedTop = StudioButtonPressedTop,
    buttonPressedBottom = StudioButtonPressedBottom,
    shadowDark = StudioShadowDark,
    shadowLight = StudioShadowLight,
    hairline = StudioHairline,
    inkPrimary = StudioInkPrimary,
    inkSecondary = StudioInkSecondary,
    inkMuted = StudioInkMuted,
    visualizerBar = StudioVisualizerBar,
    visualizerActive = StudioVisualizerActive,
    accent = StudioAccent,
    warmBrass = StudioWarmBrass,
    favoriteRed = FavoriteCoral,
    navBarBg = Color(0xF5F8FAFD),
)

val DarkPlinthPalette = PlinthPalette(
    isDark = true,
    canvasTop = TitaniumCanvasTop,
    canvasBottom = TitaniumCanvasBottom,
    domeTop = TitaniumDomeTop,
    domeBottom = TitaniumDomeBottom,
    capsuleBg = TitaniumCapsuleBg,
    plinthTop = TitaniumPlinthTop,
    plinthBottom = TitaniumPlinthBottom,
    plinthEdgeLight = TitaniumPlinthEdgeLight,
    plinthEdgeShadow = TitaniumPlinthEdgeShadow,
    buttonTop = TitaniumButtonTop,
    buttonBottom = TitaniumButtonBottom,
    buttonPressedTop = TitaniumButtonPressedTop,
    buttonPressedBottom = TitaniumButtonPressedBottom,
    shadowDark = TitaniumShadowDark,
    shadowLight = TitaniumShadowLight,
    hairline = TitaniumHairline,
    inkPrimary = TitaniumInkPrimary,
    inkSecondary = TitaniumInkSecondary,
    inkMuted = TitaniumInkMuted,
    visualizerBar = TitaniumVisualizerBar,
    visualizerActive = TitaniumVisualizerActive,
    accent = TitaniumAccent,
    warmBrass = TitaniumWarmBrass,
    favoriteRed = Color(0xFFEF6C76),
    navBarBg = Color(0xF5141820),
)
