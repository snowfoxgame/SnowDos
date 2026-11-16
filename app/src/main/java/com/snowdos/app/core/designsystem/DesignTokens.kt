package com.snowdos.app.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale tokens decoupled from feature screens.
 */
@Immutable
data class SnowDosSpacingTokens(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val floatingNavBottomClearance: Dp = 112.dp
)

/**
 * Corner radius tokens allowing global shape changes (e.g. playful vs. crisp themes).
 */
@Immutable
data class SnowDosRadiusTokens(
    val card: Dp = 24.dp,
    val compactCard: Dp = 18.dp,
    val chip: Dp = 12.dp,
    val pill: Dp = 999.dp,
    val sheet: Dp = 24.dp
)

/**
 * Surface styling hooks prepared for future frosted-glass / glassmorphism or elevated themes.
 */
@Immutable
data class SnowDosSurfaceTokens(
    val cardAlpha: Float = 0.24f,
    val interactiveAlpha: Float = 0.22f,
    val selectedAlpha: Float = 0.38f,
    val navPillAlpha: Float = 0.26f,
    val blurRadius: Dp = 20.dp,
    val borderWidth: Dp = 1.dp,
    val borderHighlightAlpha: Float = 0.42f,
    val borderShadowAlpha: Float = 0.12f,
    val borderAlpha: Float = 0.22f,
    val glassAccentBorderTint: Boolean = false,
    val usePixelRetroStyle: Boolean = false,
    val pixelShadowOffsetDp: Dp = 0.dp,
    val reduceLiveBlur: Boolean = false
)

/**
 * Semantic colors for score deltas, status indicators, and the reliability gauge.
 */
@Immutable
data class SnowDosSemanticColors(
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val gaugeLow: Color,
    val gaugeMid: Color,
    val gaugeHigh: Color
)

/**
 * Unified design tokens exposed via [LocalDesignTokens].
 */
@Immutable
data class SnowDosDesignTokens(
    val spacing: SnowDosSpacingTokens = SnowDosSpacingTokens(),
    val radius: SnowDosRadiusTokens = SnowDosRadiusTokens(),
    val surface: SnowDosSurfaceTokens = SnowDosSurfaceTokens(),
    val semantic: SnowDosSemanticColors
)
