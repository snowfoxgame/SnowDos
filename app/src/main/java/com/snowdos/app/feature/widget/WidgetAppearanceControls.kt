package com.snowdos.app.feature.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snowdos.app.R
import com.snowdos.app.core.designsystem.SnowDosThemeTokens
import com.snowdos.app.core.designsystem.components.SnowBlurScope
import com.snowdos.app.core.designsystem.components.SnowSegmentOption
import com.snowdos.app.core.designsystem.components.SnowSegmentedPill
import com.snowdos.app.core.designsystem.components.rememberDecodedBackgroundBitmap
import com.snowdos.app.domain.model.ThemeAppearanceConfig
import com.snowdos.app.domain.model.ThemeMode
import com.snowdos.app.domain.model.WidgetColorMode
import com.snowdos.app.domain.model.WidgetSurfaceStyle

@Composable
fun WidgetAppearanceControls(
    colorMode: WidgetColorMode,
    surfaceStyle: WidgetSurfaceStyle,
    appearance: ThemeAppearanceConfig,
    systemDarkTheme: Boolean,
    onSelectColorMode: (WidgetColorMode) -> Unit,
    onSelectSurfaceStyle: (WidgetSurfaceStyle) -> Unit,
    modifier: Modifier = Modifier,
    blurScope: SnowBlurScope = SnowBlurScope.BACKGROUND,
    appBackgroundUri: String? = null
) {
    val tokens = SnowDosThemeTokens.current
    val previewPalette = SnowDosWidgetData.resolvePaletteSpec(
        colorMode = colorMode,
        surfaceStyle = surfaceStyle,
        appearance = appearance,
        systemDarkTheme = systemDarkTheme
    )
    val previewBgUri = if (previewPalette.followAppTheme) {
        appBackgroundUri ?: if (appearance.mode == ThemeMode.CUSTOM) appearance.backgroundImageUri else null
    } else {
        null
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
    ) {
        WidgetPreviewCard(
            palette = previewPalette,
            backgroundUri = previewBgUri
        )

        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
            Text(
                text = stringResource(R.string.widget_config_color_mode),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SnowSegmentedPill(
                options = listOf(
                    SnowSegmentOption(
                        value = WidgetColorMode.FOLLOW_APP,
                        label = stringResource(R.string.widget_color_follow_app_pill)
                    ),
                    SnowSegmentOption(
                        value = WidgetColorMode.LIGHT,
                        label = stringResource(R.string.widget_color_light)
                    ),
                    SnowSegmentOption(
                        value = WidgetColorMode.DARK,
                        label = stringResource(R.string.widget_color_dark)
                    )
                ),
                selected = colorMode,
                onSelect = onSelectColorMode,
                blurScope = blurScope
            )
            if (colorMode == WidgetColorMode.FOLLOW_APP) {
                Text(
                    text = stringResource(R.string.widget_color_follow_app_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
            Text(
                text = stringResource(R.string.widget_config_surface_style),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SnowSegmentedPill(
                options = listOf(
                    SnowSegmentOption(
                        value = WidgetSurfaceStyle.GLASS,
                        label = stringResource(R.string.widget_style_glass)
                    ),
                    SnowSegmentOption(
                        value = WidgetSurfaceStyle.SOLID,
                        label = stringResource(R.string.widget_style_solid)
                    )
                ),
                selected = surfaceStyle,
                onSelect = onSelectSurfaceStyle,
                blurScope = blurScope
            )
        }
    }
}

@Composable
private fun WidgetPreviewCard(
    palette: WidgetResolvedPaletteSpec,
    backgroundUri: String?
) {
    val wallpaperGradient = Brush.linearGradient(
        colors = if (palette.isDark) {
            listOf(Color(0xFF1D2948), Color(0xFF392B58))
        } else {
            listOf(Color(0xFF9EB9F8), Color(0xFFD0C4FA))
        }
    )

    val outerShape = RoundedCornerShape(20.dp)
    val widgetShape = RoundedCornerShape(palette.outerCornerDp.dp)
    val rowShape = RoundedCornerShape(palette.innerCornerDp.dp)
    val pillShape = RoundedCornerShape(if (palette.isEightBitRetro) 2.dp else 50.dp)
    val previewFont = if (palette.isEightBitRetro) FontFamily.Monospace else null
    val previewBitmap = rememberDecodedBackgroundBitmap(
        uriString = backgroundUri,
        maxWidthPx = SnowDosWidgetData.MAX_WIDGET_BITMAP_EDGE_PX,
        maxHeightPx = SnowDosWidgetData.MAX_WIDGET_BITMAP_EDGE_PX
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(outerShape)
            .background(wallpaperGradient)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(widgetShape)
                .border(palette.borderWidthDp.dp, palette.borderColor, widgetShape)
        ) {
            previewBitmap?.let { bmp ->
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.cardColor)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(pillShape)
                            .background(palette.headerPillColor)
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "94%",
                            style = previewTypography(
                                base = MaterialTheme.typography.labelMedium,
                                fontFamily = previewFont,
                                fontWeight = FontWeight.Bold
                            ),
                            color = palette.accentColor,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .clip(pillShape)
                            .background(palette.headerPillColor)
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = pluralStringResource(R.plurals.widget_open_count, 2, 2),
                            style = previewTypography(
                                base = MaterialTheme.typography.labelSmall,
                                fontFamily = previewFont
                            ),
                            color = palette.titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    val addBg = if (palette.followAppTheme) palette.accentColor else palette.headerPillColor
                    val addTint = if (palette.followAppTheme) palette.onAccentColor else palette.accentColor
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(if (palette.isEightBitRetro) pillShape else CircleShape)
                            .background(addBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = addTint,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                PreviewTaskRow(
                    title = stringResource(R.string.template_daily_plan_title),
                    subtitle = stringResource(R.string.date_today),
                    stripColor = palette.accentColor,
                    palette = palette,
                    rowShape = rowShape,
                    fontFamily = previewFont
                )

                PreviewTaskRow(
                    title = stringResource(R.string.template_hydrate_title),
                    subtitle = null,
                    stripColor = Color(0xFF2EA880),
                    palette = palette,
                    rowShape = rowShape,
                    fontFamily = previewFont
                )
            }
        }
    }
}

private fun previewTypography(
    base: TextStyle,
    fontFamily: FontFamily?,
    fontWeight: FontWeight? = null
): TextStyle = base.copy(
    fontFamily = fontFamily ?: base.fontFamily,
    fontWeight = fontWeight ?: base.fontWeight
)

@Composable
private fun PreviewTaskRow(
    title: String,
    subtitle: String?,
    stripColor: Color,
    palette: WidgetResolvedPaletteSpec,
    rowShape: Shape,
    fontFamily: FontFamily?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(palette.rowColor)
            .border(if (palette.isEightBitRetro) 1.5.dp else 1.dp, palette.borderColor, rowShape)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val checkShape = if (palette.isEightBitRetro) RoundedCornerShape(0.dp) else CircleShape
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(checkShape)
                .border(1.5.dp, palette.accentColor, checkShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = palette.accentColor,
                modifier = Modifier.size(12.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(if (palette.isEightBitRetro) 0.dp else 2.dp))
                .background(stripColor)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = previewTypography(
                    base = MaterialTheme.typography.bodySmall,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Medium
                ),
                color = palette.titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = previewTypography(
                        base = MaterialTheme.typography.labelSmall,
                        fontFamily = fontFamily
                    ),
                    color = palette.subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun widgetSummaryText(
    colorMode: WidgetColorMode,
    surfaceStyle: WidgetSurfaceStyle
): String {
    val colorLabel = stringResource(
        when (colorMode) {
            WidgetColorMode.FOLLOW_APP -> R.string.widget_color_follow_app
            WidgetColorMode.LIGHT -> R.string.widget_color_light
            WidgetColorMode.DARK -> R.string.widget_color_dark
        }
    )
    val styleLabel = stringResource(
        when (surfaceStyle) {
            WidgetSurfaceStyle.GLASS -> R.string.widget_style_glass
            WidgetSurfaceStyle.SOLID -> R.string.widget_style_solid
        }
    )
    return "$colorLabel · $styleLabel"
}
