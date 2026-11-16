package com.snowdos.app.feature.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.snowdos.app.MainActivity
import com.snowdos.app.R
import com.snowdos.app.core.common.formatCompactDateTime
import com.snowdos.app.core.common.loadBoundedBitmapFromUri
import com.snowdos.app.core.common.localizedResourcesFor
import com.snowdos.app.core.designsystem.ThemeRegistry
import com.snowdos.app.data.local.PresetBackgroundGenerator
import com.snowdos.app.domain.model.TaskPriority
import com.snowdos.app.domain.model.ThemeAppearanceConfig
import com.snowdos.app.domain.model.ThemeMode
import com.snowdos.app.domain.model.WidgetColorMode
import com.snowdos.app.domain.repository.SettingsRepository
import com.snowdos.app.domain.usecase.ObserveScoreStatsUseCase
import com.snowdos.app.domain.usecase.ObserveTasksUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

class SnowDosTasksWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun settingsRepository(): SettingsRepository
        fun observeTasksUseCase(): ObserveTasksUseCase
        fun observeScoreStatsUseCase(): ObserveScoreStatsUseCase
        fun presetBackgroundGenerator(): PresetBackgroundGenerator
    }

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val settings = entryPoint.settingsRepository().observeSettings().first()
        val tasksSnapshot = entryPoint.observeTasksUseCase().observeSnapshot().first()
        val scoreStats = entryPoint.observeScoreStatsUseCase()().first()

        val systemDarkTheme = (
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            ) == Configuration.UI_MODE_NIGHT_YES

        val weatherSnapshot = settings.weatherSnapshot
        val paletteSpec = SnowDosWidgetData.resolvePaletteSpec(
            colorMode = settings.widgetColorMode,
            surfaceStyle = settings.widgetSurfaceStyle,
            appearance = settings.appearance,
            systemDarkTheme = systemDarkTheme,
            weatherIsDay = weatherSnapshot?.isDay ?: true,
            weatherCondition = weatherSnapshot?.condition ?: com.snowdos.app.domain.model.WeatherConditionKind.CLEAR
        )

        val backgroundUri = if (settings.widgetColorMode == WidgetColorMode.FOLLOW_APP) {
            resolveWidgetBackgroundUri(
                appearance = settings.appearance,
                isDark = paletteSpec.isDark,
                presetBackgroundGenerator = entryPoint.presetBackgroundGenerator()
            )
        } else {
            null
        }
        val backgroundBitmap = backgroundUri?.let { uri ->
            loadBoundedBitmapFromUri(
                context = context,
                uriString = uri,
                maxEdgePx = SnowDosWidgetData.MAX_WIDGET_BITMAP_EDGE_PX
            )
        }

        val snapshot = SnowDosWidgetSnapshot(
            scorePercent = scoreStats.summary.reliabilityScorePercent.roundToInt(),
            hasScoreData = scoreStats.summary.hasScoreData,
            openTasks = tasksSnapshot.tasks,
            language = settings.appLanguage,
            isDark = paletteSpec.isDark,
            isGlass = paletteSpec.isGlass,
            paletteSpec = paletteSpec,
            backgroundBitmap = backgroundBitmap
        )

        provideContent {
            SnowDosWidgetContent(context = context, snapshot = snapshot)
        }
    }

    private suspend fun resolveWidgetBackgroundUri(
        appearance: ThemeAppearanceConfig,
        isDark: Boolean,
        presetBackgroundGenerator: PresetBackgroundGenerator
    ): String? {
        val customUri = appearance.backgroundImageUri?.takeIf { it.isNotBlank() }
        if (customUri != null) return customUri
        val resolvedId = appearance.effectivePresetId()
        val preset = ThemeRegistry.findPreset(resolvedId)
        return if (!preset.hasPatternBackground) {
            null
        } else {
            presetBackgroundGenerator.getOrCreateBackgroundUri(
                presetId = resolvedId,
                darkTheme = isDark,
                gamingAccentHex = appearance.gamingAccentHex,
                eightBitVariant = appearance.eightBitVariant
            )
        }
    }
}

@Composable
private fun SnowDosWidgetContent(
    context: Context,
    snapshot: SnowDosWidgetSnapshot
) {
    val size = LocalSize.current
    val maxTasks = SnowDosWidgetData.maxTasksForWidgetHeight(size.height)
    val (visibleTasks, remainingCount) = SnowDosWidgetData.selectNewestOpenTasks(
        tasks = snapshot.openTasks,
        maxVisible = maxTasks
    )

    val localizedRes = context.localizedResourcesFor(snapshot.language)
    val palette = widgetPalette(snapshot.paletteSpec)

    val openTasksIntent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(MainActivity.EXTRA_OPEN_TASKS_TAB, true)
    }
    val createTaskIntent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(MainActivity.EXTRA_OPEN_CREATE_TASK, true)
    }

    val scoreText = if (snapshot.hasScoreData) {
        localizedRes.getString(R.string.widget_score_badge_format, snapshot.scorePercent)
    } else {
        localizedRes.getString(R.string.widget_score_no_data)
    }

    val openCount = visibleTasks.size + remainingCount
    val openCountText = localizedRes.getQuantityString(R.plurals.widget_open_count, openCount, openCount)

    val todayLabel = localizedRes.getString(R.string.date_today)
    val tomorrowLabel = localizedRes.getString(R.string.date_tomorrow)

    val outerContainerModifier = if (palette.spec.followAppTheme) {
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(palette.spec.outerCornerDp.dp)
            .background(ColorProvider(palette.spec.borderColor))
            .padding(palette.spec.borderWidthDp.dp)
            .clickable(actionStartActivity(openTasksIntent))
    } else {
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(R.dimen.snowdos_widget_corner_radius)
            .background(ImageProvider(palette.backgroundDrawable))
            .clickable(actionStartActivity(openTasksIntent))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    }

    Box(modifier = outerContainerModifier) {
        if (palette.spec.followAppTheme) {
            val innerCardCorner = (palette.spec.outerCornerDp - palette.spec.borderWidthDp)
                .coerceAtLeast(0f)
                .dp
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(innerCardCorner)
                    .background(ColorProvider(palette.spec.baseBackdropColor))
            ) {
                snapshot.backgroundBitmap?.let { bitmap ->
                    Image(
                        provider = ImageProvider(bitmap),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .cornerRadius(innerCardCorner)
                    )
                }
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(innerCardCorner)
                        .background(ColorProvider(palette.spec.cardColor))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    WidgetInnerContent(
                        palette = palette,
                        scoreText = scoreText,
                        openCountText = openCountText,
                        visibleTasks = visibleTasks,
                        remainingCount = remainingCount,
                        widgetHeight = size.height,
                        localizedRes = localizedRes,
                        todayLabel = todayLabel,
                        tomorrowLabel = tomorrowLabel,
                        language = snapshot.language,
                        createTaskIntent = createTaskIntent,
                        openTasksIntent = openTasksIntent
                    )
                }
            }
        } else {
            WidgetInnerContent(
                palette = palette,
                scoreText = scoreText,
                openCountText = openCountText,
                visibleTasks = visibleTasks,
                remainingCount = remainingCount,
                widgetHeight = size.height,
                localizedRes = localizedRes,
                todayLabel = todayLabel,
                tomorrowLabel = tomorrowLabel,
                language = snapshot.language,
                createTaskIntent = createTaskIntent,
                openTasksIntent = openTasksIntent
            )
        }
    }
}

@Composable
private fun WidgetInnerContent(
    palette: WidgetPalette,
    scoreText: String,
    openCountText: String,
    visibleTasks: List<WidgetTaskItem>,
    remainingCount: Int,
    widgetHeight: Dp,
    localizedRes: android.content.res.Resources,
    todayLabel: String,
    tomorrowLabel: String,
    language: com.snowdos.app.domain.model.AppLanguage,
    createTaskIntent: Intent,
    openTasksIntent: Intent
) {
    val pillCorner = if (palette.spec.isEightBitRetro) 2.dp else 999.dp
    val rowCorner = palette.spec.innerCornerDp.dp

    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Header: Score Pill + Open Tasks Count + Create (+) Button
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier
                    .widgetDynamicBackground(
                        followAppTheme = palette.spec.followAppTheme,
                        color = palette.spec.badgeBgColor,
                        cornerDp = pillCorner,
                        fallbackDrawable = palette.pillDrawable
                    )
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = scoreText,
                    maxLines = 1,
                    style = widgetTextStyle(
                        palette = palette,
                        color = if (palette.spec.followAppTheme) {
                            palette.spec.badgeTextColor
                        } else {
                            palette.spec.accentColor
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(6.dp))

            Row(
                modifier = GlanceModifier
                    .widgetDynamicBackground(
                        followAppTheme = palette.spec.followAppTheme,
                        color = palette.spec.headerPillColor,
                        cornerDp = pillCorner,
                        fallbackDrawable = palette.pillDrawable
                    )
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = openCountText,
                    maxLines = 1,
                    style = widgetTextStyle(
                        palette = palette,
                        color = palette.spec.titleColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            val addBgColor = if (palette.spec.followAppTheme) {
                palette.spec.accentColor
            } else {
                palette.spec.headerPillColor
            }
            val addIconTint = if (palette.spec.followAppTheme) {
                palette.spec.onAccentColor
            } else {
                palette.spec.accentColor
            }
            Box(
                modifier = GlanceModifier
                    .size(36.dp)
                    .widgetDynamicBackground(
                        followAppTheme = palette.spec.followAppTheme,
                        color = addBgColor,
                        cornerDp = pillCorner,
                        fallbackDrawable = palette.pillDrawable
                    )
                    .clickable(actionStartActivity(createTaskIntent)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_add),
                    contentDescription = localizedRes.getString(R.string.widget_add_task_cd),
                    colorFilter = ColorFilter.tint(ColorProvider(addIconTint)),
                    modifier = GlanceModifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        if (visibleTasks.isEmpty()) {
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .widgetRowBackground(palette = palette, cornerDp = rowCorner)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = localizedRes.getString(R.string.widget_empty_title),
                    maxLines = 1,
                    style = widgetTextStyle(
                        palette = palette,
                        color = palette.spec.titleColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = localizedRes.getString(R.string.widget_empty_subtitle),
                    maxLines = 1,
                    style = widgetTextStyle(
                        palette = palette,
                        color = palette.spec.subtitleColor,
                        fontSize = 11.sp
                    )
                )
            }
        } else {
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
            ) {
                visibleTasks.forEachIndexed { index, task ->
                    if (index > 0) {
                        Spacer(modifier = GlanceModifier.height(5.dp))
                    }
                    WidgetTaskRow(
                        task = task,
                        palette = palette,
                        completeContentDescription = localizedRes.getString(R.string.widget_complete_task_cd),
                        dueText = task.dueAtEpochMillis?.let { dueMillis ->
                            formatCompactDateTime(
                                epochMillis = dueMillis,
                                language = language,
                                todayLabel = todayLabel,
                                tomorrowLabel = tomorrowLabel
                            )
                        },
                        openTasksIntent = openTasksIntent
                    )
                }

                if (remainingCount > 0 && widgetHeight >= 175.dp) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = localizedRes.getString(R.string.widget_more_tasks_format, remainingCount),
                        maxLines = 1,
                        modifier = GlanceModifier.padding(horizontal = 4.dp),
                        style = widgetTextStyle(
                            palette = palette,
                            color = palette.spec.subtitleColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetTaskRow(
    task: WidgetTaskItem,
    palette: WidgetPalette,
    completeContentDescription: String,
    dueText: String?,
    openTasksIntent: Intent
) {
    val priorityColor = when (task.priority) {
        TaskPriority.HIGH -> Color(0xFFE5484D)
        TaskPriority.MEDIUM -> palette.spec.accentColor
        TaskPriority.LOW -> Color(0xFF2EA880)
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .widgetRowBackground(palette = palette, cornerDp = palette.spec.innerCornerDp.dp)
            .clickable(actionStartActivity(openTasksIntent))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Direct check-off button (ActionCallback without opening the app)
        Box(
            modifier = GlanceModifier
                .size(38.dp)
                .clickable(
                    actionRunCallback<CompleteTaskWidgetAction>(
                        actionParametersOf(CompleteTaskWidgetAction.TaskIdKey to task.id)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = GlanceModifier
                    .size(22.dp)
                    .widgetDynamicBackground(
                        followAppTheme = palette.spec.followAppTheme,
                        color = palette.spec.badgeBgColor,
                        cornerDp = if (palette.spec.isEightBitRetro) 0.dp else 11.dp,
                        fallbackDrawable = palette.checkButtonDrawable
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_check),
                    contentDescription = completeContentDescription,
                    colorFilter = ColorFilter.tint(ColorProvider(palette.spec.accentColor)),
                    modifier = GlanceModifier.size(13.dp)
                )
            }
        }

        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(18.dp)
                .cornerRadius(if (palette.spec.isEightBitRetro) 0.dp else 2.dp)
                .background(ColorProvider(priorityColor))
        ) {}

        Spacer(modifier = GlanceModifier.width(8.dp))

        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .padding(vertical = 2.dp)
        ) {
            Text(
                text = task.title,
                maxLines = 1,
                style = widgetTextStyle(
                    palette = palette,
                    color = palette.spec.titleColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            if (dueText != null || task.isRecurring) {
                Spacer(modifier = GlanceModifier.height(1.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.isRecurring) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_repeat),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(ColorProvider(palette.spec.subtitleColor)),
                            modifier = GlanceModifier.size(11.dp)
                        )
                        if (dueText != null) {
                            Spacer(modifier = GlanceModifier.width(3.dp))
                        }
                    }
                    if (dueText != null) {
                        Text(
                            text = dueText,
                            maxLines = 1,
                            style = widgetTextStyle(
                                palette = palette,
                                color = palette.spec.subtitleColor,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun GlanceModifier.widgetDynamicBackground(
    followAppTheme: Boolean,
    color: Color,
    cornerDp: Dp,
    fallbackDrawable: Int
): GlanceModifier = if (followAppTheme) {
    cornerRadius(cornerDp).background(ColorProvider(color))
} else {
    background(ImageProvider(fallbackDrawable)).cornerRadius(cornerDp)
}

private fun GlanceModifier.widgetRowBackground(
    palette: WidgetPalette,
    cornerDp: Dp
): GlanceModifier = if (palette.spec.followAppTheme) {
    cornerRadius(cornerDp).background(ColorProvider(palette.spec.rowColor))
} else {
    background(ImageProvider(palette.rowDrawable)).cornerRadius(R.dimen.snowdos_widget_inner_radius)
}

private fun widgetTextStyle(
    palette: WidgetPalette,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal
): TextStyle = TextStyle(
    color = ColorProvider(color),
    fontSize = fontSize,
    fontWeight = fontWeight,
    fontFamily = if (palette.spec.isEightBitRetro) FontFamily.Monospace else null
)

private data class WidgetPalette(
    val spec: WidgetResolvedPaletteSpec,
    val backgroundDrawable: Int,
    val rowDrawable: Int,
    val pillDrawable: Int,
    val checkButtonDrawable: Int
)

private fun widgetPalette(spec: WidgetResolvedPaletteSpec): WidgetPalette = WidgetPalette(
    spec = spec,
    backgroundDrawable = when {
        spec.isDark && spec.isGlass -> R.drawable.widget_bg_glass_dark
        spec.isDark -> R.drawable.widget_bg_solid_dark
        spec.isGlass -> R.drawable.widget_bg_glass_light
        else -> R.drawable.widget_bg_solid_light
    },
    rowDrawable = when {
        spec.isDark && spec.isGlass -> R.drawable.widget_row_glass_dark
        spec.isDark -> R.drawable.widget_row_solid_dark
        spec.isGlass -> R.drawable.widget_row_glass_light
        else -> R.drawable.widget_row_solid_light
    },
    pillDrawable = if (spec.isDark) R.drawable.widget_pill_dark else R.drawable.widget_pill_light,
    checkButtonDrawable = if (spec.isDark) R.drawable.widget_check_btn_dark else R.drawable.widget_check_btn_light
)
