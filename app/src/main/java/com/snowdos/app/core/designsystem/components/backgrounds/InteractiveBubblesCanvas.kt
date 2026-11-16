package com.snowdos.app.core.designsystem.components.backgrounds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.hypot
import kotlin.random.Random

private class BubbleOrb(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val baseVx: Float,
    val baseVy: Float,
    val radiusFraction: Float,
    val colorIndex: Int,
    val alpha: Float
)

/**
 * Touch-reactive drifting orbs rendered in the backdrop layer. Reads [impulseState] populated via
 * passive `PointerEventPass.Initial` observation so foreground scrolls/clicks are never blocked.
 */
@Composable
fun InteractiveBubblesCanvas(
    impulseState: BubbleTouchImpulseState,
    reduceLiveBlur: Boolean,
    modifier: Modifier = Modifier
) {
    val bg = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val targetOrbCount = if (reduceLiveBlur) 7 else 14
    val orbs = remember(targetOrbCount) {
        val rng = Random(4207)
        List(targetOrbCount) { idx ->
            val vx = (rng.nextFloat() - 0.5f) * 38f
            val vy = (rng.nextFloat() - 0.5f) * 38f
            BubbleOrb(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                vx = vx,
                vy = vy,
                baseVx = vx,
                baseVy = vy,
                radiusFraction = 0.055f + rng.nextFloat() * 0.085f,
                colorIndex = idx % 3,
                alpha = 0.24f + rng.nextFloat() * 0.20f
            )
        }
    }

    val orbColors = remember(primary, secondary, tertiary) {
        arrayOf(primary, secondary, tertiary)
    }
    val rimStroke = remember { Stroke(width = 1.6f) }

    var viewportSize by remember { mutableLongStateOf(0L) }
    val canvasSize = remember { IntArray(2) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(orbs, reduceLiveBlur, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var lastNanos = 0L
            var skipToggle = false
            while (true) {
                withFrameNanos { frameNanos ->
                    if (reduceLiveBlur) {
                        skipToggle = !skipToggle
                        if (skipToggle) {
                            lastNanos = frameNanos
                            return@withFrameNanos
                        }
                    }
                    val dt = if (lastNanos == 0L) {
                        0.016f
                    } else {
                        ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.032f)
                    }
                    lastNanos = frameNanos

                    val w = canvasSize[0].toFloat().coerceAtLeast(1f)
                    val h = canvasSize[1].toFloat().coerceAtLeast(1f)
                    val minDim = minOf(w, h)
                    val touchActive = (frameNanos - impulseState.lastTouchTimeNanos) < 280_000_000L
                    val touchPos = impulseState.pointerPosition
                    val touchVel = impulseState.pointerVelocity
                    val interactionRadius = minDim * 0.30f

                    for (orb in orbs) {
                        var px = orb.x * w
                        var py = orb.y * h

                        // Gentle damping back toward natural ambient drift velocity
                        orb.vx += (orb.baseVx - orb.vx) * (dt * 1.9f)
                        orb.vy += (orb.baseVy - orb.vy) * (dt * 1.9f)

                        if (touchActive && touchPos != Offset.Unspecified) {
                            val dx = px - touchPos.x
                            val dy = py - touchPos.y
                            val dist = hypot(dx, dy)
                            if (dist < interactionRadius && dist > 0.5f) {
                                val strength = (1f - dist / interactionRadius)
                                val nx = dx / dist
                                val ny = dy / dist
                                val pushForce = 560f * strength * dt
                                orb.vx += nx * pushForce + (touchVel.x * 0.14f * strength)
                                orb.vy += ny * pushForce + (touchVel.y * 0.14f * strength)
                            }
                        }

                        val speedCap = 520f
                        val speed = hypot(orb.vx, orb.vy)
                        if (speed > speedCap) {
                            orb.vx = (orb.vx / speed) * speedCap
                            orb.vy = (orb.vy / speed) * speedCap
                        }

                        px += orb.vx * dt
                        py += orb.vy * dt

                        val rPx = orb.radiusFraction * minDim
                        if (px < rPx) {
                            px = rPx
                            orb.vx = kotlin.math.abs(orb.vx) * 0.85f
                        } else if (px > w - rPx) {
                            px = w - rPx
                            orb.vx = -kotlin.math.abs(orb.vx) * 0.85f
                        }
                        if (py < rPx) {
                            py = rPx
                            orb.vy = kotlin.math.abs(orb.vy) * 0.85f
                        } else if (py > h - rPx) {
                            py = h - rPx
                            orb.vy = -kotlin.math.abs(orb.vy) * 0.85f
                        }

                        orb.x = (px / w).coerceIn(0f, 1f)
                        orb.y = (py / h).coerceIn(0f, 1f)
                    }
                    viewportSize = frameNanos
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .onSizeChanged {
                canvasSize[0] = it.width
                canvasSize[1] = it.height
            }
    ) {
        // Read frame tick inside draw scope so only the Canvas redraws per frame (0 recompositions)
        @Suppress("UNUSED_VARIABLE")
        val tick = viewportSize
        val minDim = size.minDimension

        for (orb in orbs) {
            val baseColor = orbColors[orb.colorIndex]
            val center = Offset(orb.x * size.width, orb.y * size.height)
            val radius = orb.radiusFraction * minDim

            if (reduceLiveBlur) {
                drawCircle(
                    color = baseColor.copy(alpha = orb.alpha * 0.72f),
                    radius = radius,
                    center = center
                )
            } else {
                // Layered translucent bubble rings without per-frame Brush/List allocations
                drawCircle(
                    color = baseColor.copy(alpha = orb.alpha * 0.34f),
                    radius = radius,
                    center = center
                )
                drawCircle(
                    color = baseColor.copy(alpha = orb.alpha * 0.62f),
                    radius = radius * 0.84f,
                    center = center
                )
                drawCircle(
                    color = Color.White.copy(alpha = orb.alpha * 0.42f),
                    radius = radius * 0.46f,
                    center = center - Offset(radius * 0.24f, radius * 0.24f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = orb.alpha * 0.75f),
                    radius = radius,
                    center = center,
                    style = rimStroke
                )
            }
        }
    }
}
