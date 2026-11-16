package com.snowdos.app.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snowdos.app.core.designsystem.SnowDosThemeTokens
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

enum class FloatingNavIconAnimation {
    STANDARD,
    STATS_BARS,
    TASKS_CHECKMARK,
    ACCOUNT_NOD,
    SETTINGS_SPIN
}

@Immutable
data class FloatingNavItem<T>(
    val destination: T,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val iconAnimation: FloatingNavIconAnimation = FloatingNavIconAnimation.STANDARD
)

/**
 * Floating pill-shaped bottom navigation bar styled via [SnowDosThemeTokens] with a single
 * continuous frosted slider that tracks pager swipes in real time and settles with a gentle spring.
 */
@Composable
fun <T> FloatingPillNavBar(
    items: List<FloatingNavItem<T>>,
    selectedDestination: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    scrollPositionProvider: (() -> Float)? = null,
    isDragInProgress: Boolean = false,
    animateTransitions: Boolean = true
) {
    val tokens = SnowDosThemeTokens.current
    val selectedIndex = items.indexOfFirst { it.destination == selectedDestination }.coerceAtLeast(0)
    val sliderPosition = remember { Animatable(selectedIndex.toFloat()) }
    val previousItemCount = remember { androidx.compose.runtime.mutableIntStateOf(items.size) }

    val latestScrollPositionProvider by rememberUpdatedState(scrollPositionProvider)

    LaunchedEffect(isDragInProgress, selectedIndex, items.size, animateTransitions) {
        if (previousItemCount.intValue != items.size) {
            previousItemCount.intValue = items.size
            sliderPosition.snapTo(selectedIndex.toFloat())
            return@LaunchedEffect
        }
        val provider = latestScrollPositionProvider
        if (isDragInProgress && provider != null) {
            snapshotFlow { latestScrollPositionProvider?.invoke() ?: selectedIndex.toFloat() }
                .collect { pos -> sliderPosition.snapTo(pos) }
        } else if (animateTransitions) {
            sliderPosition.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.88f,
                    stiffness = Spring.StiffnessMedium
                )
            )
        } else {
            sliderPosition.snapTo(selectedIndex.toFloat())
        }
    }

    val pillShape = RoundedCornerShape(tokens.radius.pill)
    val maxSlideIndex = (items.size - 1).coerceAtLeast(0).toFloat()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = tokens.spacing.md, end = tokens.spacing.md, bottom = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.glassReadySurface(
                shape = pillShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                alpha = tokens.surface.navPillAlpha,
                blurScope = SnowBlurScope.CHROME
            ),
            shape = pillShape,
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                if (items.isNotEmpty()) {
                    Box(modifier = Modifier.matchParentSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(1f / items.size)
                                .graphicsLayer {
                                    val clamped = sliderPosition.value.coerceIn(
                                        -0.08f,
                                        maxSlideIndex + 0.08f
                                    )
                                    translationX = clamped * size.width
                                }
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(
                                        alpha = tokens.surface.selectedAlpha
                                    ),
                                    shape = pillShape
                                )
                                .snowSurfaceBorder(shape = pillShape, highlighted = true)
                        )
                    }
                }

                val sliderPositionProvider = remember(sliderPosition) {
                    { sliderPosition.value }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, item ->
                        FloatingPillNavItem(
                            item = item,
                            index = index,
                            isActivated = item.destination == selectedDestination,
                            sliderPositionProvider = sliderPositionProvider,
                            onClick = { onSelect(item.destination) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> FloatingPillNavItem(
    item: FloatingNavItem<T>,
    index: Int,
    isActivated: Boolean,
    sliderPositionProvider: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = SnowDosThemeTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val selected by remember(index, sliderPositionProvider) {
        derivedStateOf { abs(sliderPositionProvider() - index) < 0.5f }
    }
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "navItemContentColor"
    )

    Box(
        modifier = modifier
            .snowPressFeedback(
                interactionSource = interactionSource,
                pressedScale = 0.96f
            )
            .clip(RoundedCornerShape(tokens.radius.pill))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            AnimatedFloatingNavIcon(
                item = item,
                visuallySelected = selected,
                isActivated = isActivated,
                tint = contentColor
            )
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun <T> AnimatedFloatingNavIcon(
    item: FloatingNavItem<T>,
    visuallySelected: Boolean,
    isActivated: Boolean,
    tint: Color
) {
    when (item.iconAnimation) {
        FloatingNavIconAnimation.STANDARD -> {
            Icon(
                imageVector = if (visuallySelected) item.selectedIcon else item.unselectedIcon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        FloatingNavIconAnimation.STATS_BARS -> StatsBarsNavIcon(
            visuallySelected = visuallySelected,
            isActivated = isActivated,
            tint = tint
        )
        FloatingNavIconAnimation.TASKS_CHECKMARK -> TasksCheckmarkNavIcon(
            visuallySelected = visuallySelected,
            isActivated = isActivated,
            tint = tint
        )
        FloatingNavIconAnimation.ACCOUNT_NOD -> AccountNodNavIcon(
            item = item,
            visuallySelected = visuallySelected,
            isActivated = isActivated,
            tint = tint
        )
        FloatingNavIconAnimation.SETTINGS_SPIN -> SettingsSpinNavIcon(
            item = item,
            visuallySelected = visuallySelected,
            isActivated = isActivated,
            tint = tint
        )
    }
}

@Composable
private fun StatsBarsNavIcon(
    visuallySelected: Boolean,
    isActivated: Boolean,
    tint: Color
) {
    val progress = remember { Animatable(1f) }
    var wasActivated by remember { mutableStateOf(isActivated) }

    LaunchedEffect(isActivated) {
        if (isActivated && !wasActivated) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
            )
        } else if (!isActivated) {
            progress.snapTo(1f)
        }
        wasActivated = isActivated
    }

    Spacer(
        modifier = Modifier
            .size(24.dp)
            .drawWithCache {
                val barWidth = 3.6.dp.toPx()
                val gap = 2.6.dp.toPx()
                val totalWidth = barWidth * 3f + gap * 2f
                val startX = (size.width - totalWidth) / 2f
                val bottomY = size.height - 4.dp.toPx()
                val maxBarHeight = size.height - 8.dp.toPx()
                val corner = CornerRadius(1.4.dp.toPx(), 1.4.dp.toPx())
                val outlineStroke = Stroke(width = 1.6.dp.toPx())
                val barRatios = floatArrayOf(0.48f, 0.90f, 0.66f)
                val barStarts = floatArrayOf(0.00f, 0.16f, 0.32f)
                val barEnds = floatArrayOf(0.64f, 0.82f, 1.00f)

                onDrawBehind {
                    val p = progress.value
                    for (i in 0..2) {
                        val local = ((p - barStarts[i]) / (barEnds[i] - barStarts[i])).coerceIn(0f, 1f)
                        val wave = if (p < 1f) {
                            // Brief stagger settle: rises from 0.25 -> slight overshoot -> final height
                            val eased = 1f - (1f - local) * (1f - local)
                            val overshoot = kotlin.math.sin(local * Math.PI).toFloat() * 0.14f
                            (0.28f + 0.72f * eased + overshoot).coerceAtLeast(0.22f)
                        } else {
                            1f
                        }
                        val barH = maxBarHeight * barRatios[i] * wave
                        val topLeft = Offset(
                            x = startX + i * (barWidth + gap),
                            y = bottomY - barH
                        )
                        drawRoundRect(
                            color = tint,
                            topLeft = topLeft,
                            size = Size(barWidth, barH),
                            cornerRadius = corner,
                            style = if (visuallySelected) Fill else outlineStroke
                        )
                    }
                }
            }
    )
}

@Composable
private fun TasksCheckmarkNavIcon(
    visuallySelected: Boolean,
    isActivated: Boolean,
    tint: Color
) {
    val drawProgress = remember { Animatable(1f) }
    val scale = remember { Animatable(1f) }
    var wasActivated by remember { mutableStateOf(isActivated) }

    LaunchedEffect(isActivated) {
        if (isActivated && !wasActivated) {
            drawProgress.snapTo(0f)
            coroutineScope {
                launch {
                    drawProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    scale.animateTo(1.13f, spring(stiffness = Spring.StiffnessMedium))
                    scale.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium))
                }
            }
        } else if (!isActivated) {
            drawProgress.snapTo(1f)
            scale.snapTo(1f)
        }
        wasActivated = isActivated
    }

    Spacer(
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                scaleX = scale.value
                scaleY = scale.value
            }
            .drawWithCache {
                val radius = size.minDimension * 0.415f
                val center = Offset(size.width / 2f, size.height / 2f)
                val outlineStroke = Stroke(width = 1.8.dp.toPx())
                val checkStroke = Stroke(
                    width = 2.1.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
                val checkPath = Path().apply {
                    moveTo(size.width * 0.30f, size.height * 0.51f)
                    lineTo(size.width * 0.44f, size.height * 0.65f)
                    lineTo(size.width * 0.71f, size.height * 0.37f)
                }
                val pathMeasure = PathMeasure().apply { setPath(checkPath, false) }
                val totalLength = pathMeasure.length
                val partialPath = Path()

                onDrawBehind {
                    val p = drawProgress.value.coerceIn(0f, 1f)
                    if (visuallySelected) {
                        drawCircle(color = tint, radius = radius, center = center)
                        if (p >= 0.999f) {
                            drawPath(
                                path = checkPath,
                                color = Color.Black,
                                style = checkStroke,
                                blendMode = BlendMode.Clear
                            )
                        } else if (p > 0.01f) {
                            partialPath.reset()
                            pathMeasure.getSegment(0f, totalLength * p, partialPath, true)
                            drawPath(
                                path = partialPath,
                                color = Color.Black,
                                style = checkStroke,
                                blendMode = BlendMode.Clear
                            )
                        }
                    } else {
                        drawCircle(
                            color = tint,
                            radius = radius,
                            center = center,
                            style = outlineStroke
                        )
                        drawPath(
                            path = checkPath,
                            color = tint,
                            style = checkStroke
                        )
                    }
                }
            }
    )
}

@Composable
private fun <T> AccountNodNavIcon(
    item: FloatingNavItem<T>,
    visuallySelected: Boolean,
    isActivated: Boolean,
    tint: Color
) {
    val nodY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val glow = remember { Animatable(0f) }
    var wasActivated by remember { mutableStateOf(isActivated) }

    LaunchedEffect(isActivated) {
        if (isActivated && !wasActivated) {
            glow.snapTo(0f)
            coroutineScope {
                launch {
                    nodY.animateTo(1.8f, tween(95, easing = FastOutSlowInEasing))
                    nodY.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium))
                }
                launch {
                    scale.animateTo(1.10f, tween(110, easing = FastOutSlowInEasing))
                    scale.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium))
                }
                launch {
                    glow.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
                    glow.snapTo(0f)
                }
            }
        } else if (!isActivated) {
            nodY.snapTo(0f)
            scale.snapTo(1f)
            glow.snapTo(0f)
        }
        wasActivated = isActivated
    }

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    val p = glow.value
                    scaleX = 0.85f + 0.55f * p
                    scaleY = 0.85f + 0.55f * p
                    alpha = if (p > 0f && p < 1f) (1f - p) * 0.34f else 0f
                }
                .background(color = tint, shape = CircleShape)
        )
        Icon(
            imageVector = if (visuallySelected) item.selectedIcon else item.unselectedIcon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    translationY = nodY.value.dp.toPx()
                    scaleX = scale.value
                    scaleY = scale.value
                }
        )
    }
}

@Composable
private fun <T> SettingsSpinNavIcon(
    item: FloatingNavItem<T>,
    visuallySelected: Boolean,
    isActivated: Boolean,
    tint: Color
) {
    val rotation = remember { Animatable(0f) }
    var wasActivated by remember { mutableStateOf(isActivated) }

    LaunchedEffect(isActivated) {
        if (isActivated && !wasActivated) {
            rotation.animateTo(
                targetValue = rotation.value + 135f,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        wasActivated = isActivated
    }

    Icon(
        imageVector = if (visuallySelected) item.selectedIcon else item.unselectedIcon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer {
                rotationZ = rotation.value
            }
    )
}
