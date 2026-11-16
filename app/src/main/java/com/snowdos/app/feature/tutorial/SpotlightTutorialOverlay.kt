package com.snowdos.app.feature.tutorial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.snowdos.app.R
import com.snowdos.app.core.designsystem.SnowDosThemeTokens
import com.snowdos.app.core.designsystem.components.SnowBlurScope
import com.snowdos.app.core.designsystem.components.SnowGlassButton
import com.snowdos.app.core.designsystem.components.SnowGlassCard
import kotlin.math.roundToInt

/**
 * Full-screen blocking coach-mark overlay with an offscreen-cleared spotlight cutout around the
 * active [TutorialStep] target and an automatically positioned frosted-glass explanation card.
 */
@Composable
fun SpotlightTutorialOverlay(
    visible: Boolean,
    stepIndex: Int,
    registry: TutorialTargetRegistry,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(240)),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        val steps = TutorialStep.ordered
        val safeIndex = stepIndex.coerceIn(0, steps.lastIndex)
        val step = steps[safeIndex]
        val isLastStep = safeIndex == steps.lastIndex
        val tokens = SnowDosThemeTokens.current
        val density = LocalDensity.current

        var overlayOriginInRoot by remember { mutableStateOf(Offset.Zero) }
        var overlaySize by remember { mutableStateOf(IntSize.Zero) }
        var cardHeightPx by remember { mutableIntStateOf(0) }

        val rawRootTarget = registry.get(step.targetId)
        val padPx = with(density) { step.cutoutPaddingDp.dp.toPx() }
        val localTargetRect: Rect? = remember(rawRootTarget, overlayOriginInRoot, overlaySize, padPx) {
            if (rawRootTarget == null || overlaySize.width == 0 || overlaySize.height == 0) {
                null
            } else {
                val translated = Rect(
                    left = rawRootTarget.left - overlayOriginInRoot.x - padPx,
                    top = rawRootTarget.top - overlayOriginInRoot.y - padPx,
                    right = rawRootTarget.right - overlayOriginInRoot.x + padPx,
                    bottom = rawRootTarget.bottom - overlayOriginInRoot.y + padPx
                )
                // Only lock onto targets currently inside the visible viewport so offscreen pager
                // tabs don't drag the cutout sideways mid-scroll.
                val fitsHorizontally = translated.right > 0f &&
                    translated.left < overlaySize.width.toFloat() &&
                    translated.left >= -overlaySize.width * 0.15f &&
                    translated.right <= overlaySize.width * 1.15f
                if (fitsHorizontally && translated.width > 0f && translated.height > 0f) {
                    Rect(
                        left = translated.left.coerceIn(0f, overlaySize.width.toFloat()),
                        top = translated.top.coerceIn(0f, overlaySize.height.toFloat()),
                        right = translated.right.coerceIn(0f, overlaySize.width.toFloat()),
                        bottom = translated.bottom.coerceIn(0f, overlaySize.height.toFloat())
                    )
                } else {
                    null
                }
            }
        }

        val fallbackCenterRect = remember(overlaySize) {
            val cx = (overlaySize.width / 2f).coerceAtLeast(1f)
            val cy = (overlaySize.height * 0.35f).coerceAtLeast(1f)
            Rect(cx - 1f, cy - 1f, cx + 1f, cy + 1f)
        }

        val targetForAnimation = localTargetRect ?: fallbackCenterRect
        val animatedCutout by animateRectAsState(
            targetValue = targetForAnimation,
            animationSpec = spring(
                dampingRatio = 0.86f,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "spotlightCutout"
        )
        val showCutoutHighlight = localTargetRect != null

        val pulseTransition = rememberInfiniteTransition(label = "spotlightPulse")
        val pulseAlpha by pulseTransition.animateFloat(
            initialValue = 0.65f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )

        val borderColor = MaterialTheme.colorScheme.primary
        val cornerRadiusPx = if (step.isCircularCutout) {
            minOf(animatedCutout.width, animatedCutout.height) / 2f
        } else {
            with(density) { tokens.radius.card.toPx() }
        }
        val strokeWidthPx = with(density) { 2.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    if (coords.isAttached) {
                        overlayOriginInRoot = coords.positionInRoot()
                        overlaySize = coords.size
                    }
                }
        ) {
            // Blocking scrim + transparent cutout layer (placed before the explanation card so
            // touches on the dim background AND inside the cutout cannot reach underlying UI).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                ) {
                    drawRect(Color.Black.copy(alpha = 0.72f))

                    if (showCutoutHighlight && animatedCutout.width > 4f && animatedCutout.height > 4f) {
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = animatedCutout.topLeft,
                            size = Size(animatedCutout.width, animatedCutout.height),
                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                            blendMode = BlendMode.Clear
                        )
                    }
                }

                if (showCutoutHighlight && animatedCutout.width > 4f && animatedCutout.height > 4f) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRoundRect(
                            color = borderColor.copy(alpha = pulseAlpha),
                            topLeft = animatedCutout.topLeft,
                            size = Size(animatedCutout.width, animatedCutout.height),
                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                            style = Stroke(width = strokeWidthPx)
                        )
                    }
                }
            }

            // Compute safe vertical position above or below the cutout.
            val statusTopPx = with(density) {
                WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() + 12.dp.toPx()
            }
            val navBottomPx = with(density) {
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() + 16.dp.toPx()
            }
            val gapPx = with(density) { 16.dp.toPx() }
            val maxBottomY = (overlaySize.height.toFloat() - navBottomPx - cardHeightPx.toFloat())
                .coerceAtLeast(statusTopPx)

            val cardTopPx = if (!showCutoutHighlight) {
                ((overlaySize.height - cardHeightPx) / 2f).coerceIn(statusTopPx, maxBottomY)
            } else {
                val spaceBelow = (overlaySize.height.toFloat() - navBottomPx) - animatedCutout.bottom
                val spaceAbove = animatedCutout.top - statusTopPx
                val preferBelow = spaceBelow >= cardHeightPx + gapPx || spaceBelow >= spaceAbove
                if (preferBelow) {
                    (animatedCutout.bottom + gapPx).coerceIn(statusTopPx, maxBottomY)
                } else {
                    (animatedCutout.top - gapPx - cardHeightPx).coerceIn(statusTopPx, maxBottomY)
                }
            }

            SnowGlassCard(
                blurScope = SnowBlurScope.CHROME,
                highlighted = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.md)
                    .offset { IntOffset(x = 0, y = cardTopPx.roundToInt()) }
                    .onSizeChanged { cardHeightPx = it.height }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(tokens.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                R.string.tutorial_step_counter,
                                safeIndex + 1,
                                steps.size
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        val skipInteraction = remember { MutableInteractionSource() }
                        Text(
                            text = stringResource(R.string.tutorial_skip),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = skipInteraction,
                                    indication = null,
                                    onClick = onSkip
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = stringResource(step.titleRes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = stringResource(step.bodyRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = tokens.spacing.xs),
                        horizontalArrangement = Arrangement.End
                    ) {
                        SnowGlassButton(
                            onClick = onNext,
                            highlighted = true,
                            blurScope = SnowBlurScope.CHROME
                        ) {
                            Text(
                                text = stringResource(
                                    if (isLastStep) R.string.tutorial_finish else R.string.tutorial_next
                                ),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
