package com.snowdos.app.feature.widget

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.snowdos.app.core.designsystem.ThemeRegistry
import com.snowdos.app.domain.model.AppLanguage
import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.model.TaskPriority
import com.snowdos.app.domain.model.ThemeAppearanceConfig
import com.snowdos.app.domain.model.WeatherConditionKind
import com.snowdos.app.domain.model.WidgetColorMode
import com.snowdos.app.domain.model.WidgetSurfaceStyle

data class WidgetTaskItem(
    val id: String,
    val title: String,
    val priority: TaskPriority,
    val dueAtEpochMillis: Long?,
    val isRecurring: Boolean
)

data class WidgetResolvedPaletteSpec(
    val followAppTheme: Boolean,
    val isDark: Boolean,
    val isGlass: Boolean,
    val isEightBitRetro: Boolean,
    val baseBackdropColor: Color,
    val cardColor: Color,
    val rowColor: Color,
    val headerPillColor: Color,
    val accentColor: Color,
    val onAccentColor: Color,
    val titleColor: Color,
    val subtitleColor: Color,
    val badgeBgColor: Color,
    val badgeTextColor: Color,
    val borderColor: Color,
    val borderWidthDp: Float,
    val outerCornerDp: Float,
    val innerCornerDp: Float
)

data class SnowDosWidgetSnapshot(
    val scorePercent: Int,
    val hasScoreData: Boolean,
    val openTasks: List<Task>,
    val language: AppLanguage,
    val isDark: Boolean,
    val isGlass: Boolean,
    val paletteSpec: WidgetResolvedPaletteSpec,
    val backgroundBitmap: Bitmap? = null
)

object SnowDosWidgetData {
    const val MAX_WIDGET_BITMAP_EDGE_PX: Int = 560

    fun maxTasksForWidgetHeight(height: Dp): Int = when {
        height < 150.dp -> 1
        height < 215.dp -> 2
        else -> 3
    }

    /**
     * Keeps the newest open/snoozed tasks at the top so newly created items appear immediately.
     */
    fun selectNewestOpenTasks(
        tasks: List<Task>,
        maxVisible: Int
    ): Pair<List<WidgetTaskItem>, Int> {
        val actionableNewestFirst = tasks
            .asSequence()
            .filter { it.status.isActionable }
            .sortedWith(
                compareByDescending<Task> { it.createdAtEpochMillis }
                    .thenByDescending { it.updatedAtEpochMillis }
                    .thenBy { it.id }
            )
            .toList()

        val limit = maxVisible.coerceIn(1, 3)
        val visible = actionableNewestFirst
            .take(limit)
            .map { task ->
                WidgetTaskItem(
                    id = task.id,
                    title = task.title,
                    priority = task.priority,
                    dueAtEpochMillis = task.dueAtEpochMillis ?: task.remindAtEpochMillis,
                    isRecurring = task.recurrence.isRecurring
                )
            }
        val remaining = (actionableNewestFirst.size - visible.size).coerceAtLeast(0)
        return visible to remaining
    }

    fun resolveIsDark(
        colorMode: WidgetColorMode,
        appearance: ThemeAppearanceConfig,
        systemDarkTheme: Boolean,
        weatherIsDay: Boolean = true,
        weatherCondition: WeatherConditionKind = WeatherConditionKind.CLEAR
    ): Boolean = when (colorMode) {
        WidgetColorMode.FOLLOW_APP -> ThemeRegistry.isEffectiveDarkTheme(
            appearance = appearance,
            systemDarkTheme = systemDarkTheme,
            weatherIsDay = weatherIsDay,
            weatherCondition = weatherCondition
        )
        WidgetColorMode.LIGHT -> false
        WidgetColorMode.DARK -> true
    }

    fun resolveIsGlass(style: WidgetSurfaceStyle): Boolean = style == WidgetSurfaceStyle.GLASS

    fun computeScaledDimensions(
        width: Int,
        height: Int,
        maxEdgePx: Int = MAX_WIDGET_BITMAP_EDGE_PX
    ): Pair<Int, Int> {
        if (width <= 0 || height <= 0 || maxEdgePx <= 0) return 0 to 0
        val longest = maxOf(width, height)
        if (longest <= maxEdgePx) return width to height
        val scale = maxEdgePx.toFloat() / longest.toFloat()
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    fun resolvePaletteSpec(
        colorMode: WidgetColorMode,
        surfaceStyle: WidgetSurfaceStyle,
        appearance: ThemeAppearanceConfig,
        systemDarkTheme: Boolean,
        weatherIsDay: Boolean = true,
        weatherCondition: WeatherConditionKind = WeatherConditionKind.CLEAR
    ): WidgetResolvedPaletteSpec {
        val isDark = resolveIsDark(colorMode, appearance, systemDarkTheme, weatherIsDay, weatherCondition)
        val isGlass = resolveIsGlass(surfaceStyle)
        if (colorMode != WidgetColorMode.FOLLOW_APP) {
            return fixedModePaletteSpec(isDark = isDark, isGlass = isGlass)
        }

        val themeConfig = ThemeRegistry.resolve(
            appearance = appearance,
            weatherIsDay = weatherIsDay,
            weatherCondition = weatherCondition
        )
        val scheme = themeConfig.colorScheme(isDark)
        val tokens = themeConfig.tokens(isDark)
        val isEightBitRetro = tokens.surface.usePixelRetroStyle

        if (isEightBitRetro) {
            return WidgetResolvedPaletteSpec(
                followAppTheme = true,
                isDark = isDark,
                isGlass = isGlass,
                isEightBitRetro = true,
                baseBackdropColor = scheme.surface,
                cardColor = scheme.surfaceContainer.copy(alpha = if (isGlass) 0.88f else 0.96f),
                rowColor = scheme.surfaceContainerLow,
                headerPillColor = scheme.secondaryContainer,
                accentColor = scheme.primary,
                onAccentColor = scheme.onPrimary,
                titleColor = scheme.onSurface,
                subtitleColor = scheme.onSurfaceVariant,
                badgeBgColor = scheme.primaryContainer,
                badgeTextColor = scheme.onPrimaryContainer,
                borderColor = scheme.outline,
                borderWidthDp = 2f,
                outerCornerDp = 2f,
                innerCornerDp = 0f
            )
        }

        val glassOverlayAlpha = (appearance.glassOpacity + 0.44f).coerceIn(0.54f, 0.84f)
        val borderColor = when {
            tokens.surface.glassAccentBorderTint ->
                scheme.primary.copy(alpha = if (isGlass) 0.62f else 0.45f)
            isDark -> Color.White.copy(alpha = if (isGlass) 0.24f else 0.14f)
            else -> scheme.outline.copy(alpha = if (isGlass) 0.22f else 0.18f)
        }

        return WidgetResolvedPaletteSpec(
            followAppTheme = true,
            isDark = isDark,
            isGlass = isGlass,
            isEightBitRetro = false,
            baseBackdropColor = scheme.surface,
            cardColor = scheme.surfaceContainer.copy(alpha = if (isGlass) glassOverlayAlpha else 0.94f),
            rowColor = if (isGlass) {
                scheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.74f else 0.82f)
            } else {
                scheme.surfaceContainerLow
            },
            headerPillColor = scheme.secondaryContainer.copy(alpha = if (isGlass) 0.88f else 1f),
            accentColor = scheme.primary,
            onAccentColor = scheme.onPrimary,
            titleColor = scheme.onSurface,
            subtitleColor = scheme.onSurfaceVariant,
            badgeBgColor = scheme.primaryContainer,
            badgeTextColor = scheme.onPrimaryContainer,
            borderColor = borderColor,
            borderWidthDp = 1f,
            outerCornerDp = 22f,
            innerCornerDp = 14f
        )
    }

    private fun fixedModePaletteSpec(
        isDark: Boolean,
        isGlass: Boolean
    ): WidgetResolvedPaletteSpec = when {
        isDark && isGlass -> WidgetResolvedPaletteSpec(
            followAppTheme = false,
            isDark = true,
            isGlass = true,
            isEightBitRetro = false,
            baseBackdropColor = Color(0xFF121A2B),
            cardColor = Color(0xBD121A2B),
            rowColor = Color(0x80202C42),
            headerPillColor = Color(0x88243652),
            accentColor = Color(0xFF81B9FF),
            onAccentColor = Color(0xFF0A1B33),
            titleColor = Color(0xFFF3F6FB),
            subtitleColor = Color(0xFFB7C3D8),
            badgeBgColor = Color(0x3D81B9FF),
            badgeTextColor = Color(0xFFCDE3FF),
            borderColor = Color(0x38FFFFFF),
            borderWidthDp = 1f,
            outerCornerDp = 22f,
            innerCornerDp = 14f
        )
        isDark -> WidgetResolvedPaletteSpec(
            followAppTheme = false,
            isDark = true,
            isGlass = false,
            isEightBitRetro = false,
            baseBackdropColor = Color(0xFF141B29),
            cardColor = Color(0xFA141B29),
            rowColor = Color(0xFF1F293B),
            headerPillColor = Color(0xFF23324A),
            accentColor = Color(0xFF81B9FF),
            onAccentColor = Color(0xFF0A1B33),
            titleColor = Color(0xFFF4F7FC),
            subtitleColor = Color(0xFFAAB7CE),
            badgeBgColor = Color(0x3381B9FF),
            badgeTextColor = Color(0xFFD0E4FF),
            borderColor = Color(0x28FFFFFF),
            borderWidthDp = 1f,
            outerCornerDp = 22f,
            innerCornerDp = 14f
        )
        isGlass -> WidgetResolvedPaletteSpec(
            followAppTheme = false,
            isDark = false,
            isGlass = true,
            isEightBitRetro = false,
            baseBackdropColor = Color(0xFFF5F9FF),
            cardColor = Color(0xCFF5F9FF),
            rowColor = Color(0xB8FFFFFF),
            headerPillColor = Color(0xCCE6F0FF),
            accentColor = Color(0xFF2C66B8),
            onAccentColor = Color.White,
            titleColor = Color(0xFF142136),
            subtitleColor = Color(0xFF4F5E76),
            badgeBgColor = Color(0x262C66B8),
            badgeTextColor = Color(0xFF1F4E93),
            borderColor = Color(0x66FFFFFF),
            borderWidthDp = 1f,
            outerCornerDp = 22f,
            innerCornerDp = 14f
        )
        else -> WidgetResolvedPaletteSpec(
            followAppTheme = false,
            isDark = false,
            isGlass = false,
            isEightBitRetro = false,
            baseBackdropColor = Color(0xFFFAFBFD),
            cardColor = Color(0xFAFAFBFD),
            rowColor = Color(0xFFF0F4FA),
            headerPillColor = Color(0xFFE5EEFA),
            accentColor = Color(0xFF2C66B8),
            onAccentColor = Color.White,
            titleColor = Color(0xFF142136),
            subtitleColor = Color(0xFF516078),
            badgeBgColor = Color(0x1E2C66B8),
            badgeTextColor = Color(0xFF1F4E93),
            borderColor = Color(0x30142136),
            borderWidthDp = 1f,
            outerCornerDp = 22f,
            innerCornerDp = 14f
        )
    }
}
