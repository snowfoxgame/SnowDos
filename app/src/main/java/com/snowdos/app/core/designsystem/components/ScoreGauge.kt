package com.snowdos.app.core.designsystem.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snowdos.app.R
import com.snowdos.app.core.designsystem.SnowDosThemeTokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private data class CachedGaugeMarker(
    val tickStart: Offset,
    val tickEnd: Offset,
    val textLayout: TextLayoutResult,
    val textTopLeft: Offset
)

private val GAUGE_MARKER_PERCENTS = intArrayOf(0, 25, 50, 75, 100)

/**
 * Compact, frameless semicircular reliability gauge (Red -> Yellow -> Green)
 * with centered score label, 25% scale markers, and a 120Hz-cached arc pointer.
 */
@Composable
fun ScoreGauge(
    scorePercent: Float,
    hasScoreData: Boolean,
    netPoints: Int,
    modifier: Modifier = Modifier,
    isActive: Boolean = true
) {
    val tokens = SnowDosThemeTokens.current
    val clamped = scorePercent.coerceIn(0f, 100f)
    val animatedPercentState = animateFloatAsState(
        targetValue = when {
            !isActive -> 0f
            hasScoreData -> clamped
            else -> 50f
        },
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessLow
        ),
        label = "scoreGaugeNeedle"
    )
    val animatedNetPointsState = animateIntAsState(
        targetValue = if (isActive && hasScoreData) netPoints else 0,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessLow
        ),
        label = "scoreGaugeNetPoints"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val pointerColor = MaterialTheme.colorScheme.onSurface
    val pointerBorderColor = MaterialTheme.colorScheme.surface
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val tickLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    val markerTextStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = tickLabelColor
    )
    val textMeasurer = rememberTextMeasurer()
    val gaugeLow = tokens.semantic.gaugeLow
    val gaugeMid = tokens.semantic.gaugeMid
    val gaugeHigh = tokens.semantic.gaugeHigh
    val gradientColors = remember(gaugeLow, gaugeMid, gaugeHigh) {
        listOf(gaugeLow, gaugeMid, gaugeHigh)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .width(292.dp)
                .height(144.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val strokeWidth = 13.dp.toPx()
                        val arcStroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        val sideReserve = 46.dp.toPx()
                        val topReserve = 28.dp.toPx()
                        val bottomMargin = 10.dp.toPx()
                        val diameter = minOf(
                            size.width - sideReserve * 2f,
                            (size.height - bottomMargin - topReserve) * 2f
                        )
                        val radius = diameter / 2f
                        val center = Offset(x = size.width / 2f, y = size.height - bottomMargin)
                        val arcTopLeft = Offset(x = center.x - radius, y = center.y - radius)
                        val arcSize = Size(width = diameter, height = diameter)
                        val gradientBrush = Brush.horizontalGradient(
                            colors = gradientColors,
                            startX = arcTopLeft.x,
                            endX = arcTopLeft.x + diameter
                        )
                        val outerEdge = radius + strokeWidth / 2f
                        val tickStartRadius = outerEdge + 3.dp.toPx()
                        val tickEndRadius = outerEdge + 7.dp.toPx()
                        val labelAnchorRadius = outerEdge + 10.dp.toPx()
                        val tickStrokeWidth = 1.5.dp.toPx()

                        val cachedMarkers = GAUGE_MARKER_PERCENTS.map { percent ->
                            val angleRad = (180f - (percent / 100f) * 180f) * (PI / 180.0)
                            val cosA = cos(angleRad).toFloat()
                            val sinA = sin(angleRad).toFloat()
                            val tickStart = Offset(
                                x = center.x + tickStartRadius * cosA,
                                y = center.y - tickStartRadius * sinA
                            )
                            val tickEnd = Offset(
                                x = center.x + tickEndRadius * cosA,
                                y = center.y - tickEndRadius * sinA
                            )
                            val layout = textMeasurer.measure(
                                text = "$percent%",
                                style = markerTextStyle
                            )
                            val anchor = Offset(
                                x = center.x + labelAnchorRadius * cosA,
                                y = center.y - labelAnchorRadius * sinA
                            )
                            val textTopLeft = Offset(
                                x = anchor.x - layout.size.width * ((1f - cosA) * 0.5f),
                                y = anchor.y - layout.size.height * ((1f + sinA) * 0.5f)
                            )
                            CachedGaugeMarker(
                                tickStart = tickStart,
                                tickEnd = tickEnd,
                                textLayout = layout,
                                textTopLeft = textTopLeft
                            )
                        }

                        val innerPointerRatio = radius * 0.42f
                        val outerPointerRadius = radius + strokeWidth * 0.22f
                        val pointerStrokeWidth = 3.5.dp.toPx()
                        val borderDotRadius = 6.dp.toPx()
                        val centerDotRadius = 3.8.dp.toPx()

                        onDrawBehind {
                            for (marker in cachedMarkers) {
                                drawLine(
                                    color = tickColor,
                                    start = marker.tickStart,
                                    end = marker.tickEnd,
                                    strokeWidth = tickStrokeWidth,
                                    cap = StrokeCap.Round
                                )
                                drawText(
                                    textLayoutResult = marker.textLayout,
                                    topLeft = marker.textTopLeft
                                )
                            }

                            drawArc(
                                color = trackColor,
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = false,
                                topLeft = arcTopLeft,
                                size = arcSize,
                                style = arcStroke
                            )

                            drawArc(
                                brush = gradientBrush,
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = false,
                                topLeft = arcTopLeft,
                                size = arcSize,
                                style = arcStroke
                            )

                            val angleDeg = 180f - (animatedPercentState.value / 100f) * 180f
                            val angleRad = angleDeg * (PI / 180.0)
                            val cosA = cos(angleRad).toFloat()
                            val sinA = sin(angleRad).toFloat()

                            val pointerStart = Offset(
                                x = center.x + innerPointerRatio * cosA,
                                y = center.y - innerPointerRatio * sinA
                            )
                            val pointerEnd = Offset(
                                x = center.x + outerPointerRadius * cosA,
                                y = center.y - outerPointerRadius * sinA
                            )
                            val arcPoint = Offset(
                                x = center.x + radius * cosA,
                                y = center.y - radius * sinA
                            )

                            drawLine(
                                color = pointerColor,
                                start = pointerStart,
                                end = pointerEnd,
                                strokeWidth = pointerStrokeWidth,
                                cap = StrokeCap.Round
                            )
                            drawCircle(
                                color = pointerBorderColor,
                                radius = borderDotRadius,
                                center = arcPoint
                            )
                            drawCircle(
                                color = pointerColor,
                                radius = centerDotRadius,
                                center = arcPoint
                            )
                        }
                    }
            )

            ScoreGaugePercentLabel(
                hasScoreData = hasScoreData,
                percentProvider = { animatedPercentState.value },
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        ScoreGaugeNetPointsLabel(
            hasScoreData = hasScoreData,
            netPointsProvider = { animatedNetPointsState.value }
        )
    }
}

@Composable
private fun ScoreGaugePercentLabel(
    hasScoreData: Boolean,
    percentProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    Text(
        text = if (hasScoreData) {
            stringResource(R.string.score_gauge_percent, percentProvider().roundToInt().coerceIn(0, 100))
        } else {
            stringResource(R.string.score_gauge_empty_dash)
        },
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

@Composable
private fun ScoreGaugeNetPointsLabel(
    hasScoreData: Boolean,
    netPointsProvider: () -> Int
) {
    val points = netPointsProvider()
    Text(
        text = if (hasScoreData) {
            val sign = if (points > 0) "+" else ""
            stringResource(R.string.score_gauge_net_summary, "$sign$points")
        } else {
            stringResource(R.string.score_gauge_no_data)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
