package com.snowdos.app.core.designsystem.components.backgrounds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.snowdos.app.domain.model.WeatherConditionKind
import com.snowdos.app.domain.model.WeatherConditionMapper
import com.snowdos.app.domain.model.WeatherSnapshot
import com.snowdos.app.domain.model.WeatherThemeStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class PrecipParticle(
    var x: Float,
    var y: Float,
    val speed: Float,
    val size: Float,
    val swayPhase: Float
)

private data class SkyCloud(
    var x: Float,
    val y: Float,
    val scale: Float,
    val speed: Float
)

private class RadialGlowCache {
    var center: Offset = Offset.Unspecified
    var radius: Float = -1f
    var dimmed: Boolean = false
    var brush: Brush? = null

    fun getOrCreate(
        center: Offset,
        radius: Float,
        dimmed: Boolean,
        buildColors: () -> List<Color>
    ): Brush {
        val current = brush
        if (current != null && this.center == center && this.radius == radius && this.dimmed == dimmed) {
            return current
        }
        val created = Brush.radialGradient(
            colors = buildColors(),
            center = center,
            radius = radius
        )
        this.center = center
        this.radius = radius
        this.dimmed = dimmed
        this.brush = created
        return created
    }
}

/**
 * Dynamic sky, sun/moon arc, compact rolling hill, and full WMO weather effects rendered in
 * either illustrated `CUTE_2D` or atmospheric `REALISTIC` style.
 */
@Composable
fun WeatherTimeCanvas(
    style: WeatherThemeStyle,
    snapshot: WeatherSnapshot,
    reduceLiveBlur: Boolean,
    modifier: Modifier = Modifier
) {
    val condition = snapshot.condition
    val particleCount = when (condition) {
        WeatherConditionKind.HEAVY_RAIN, WeatherConditionKind.THUNDERSTORM_HAIL -> if (reduceLiveBlur) 26 else 56
        WeatherConditionKind.RAIN, WeatherConditionKind.FREEZING_RAIN, WeatherConditionKind.SNOW_HEAVY -> if (reduceLiveBlur) 20 else 40
        WeatherConditionKind.DRIZZLE, WeatherConditionKind.SNOW_LIGHT -> if (reduceLiveBlur) 14 else 26
        else -> 0
    }

    val rng = remember { Random(2026) }
    val particles = remember(particleCount, condition) {
        List(particleCount) {
            PrecipParticle(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                speed = when (condition) {
                    WeatherConditionKind.HEAVY_RAIN, WeatherConditionKind.THUNDERSTORM_HAIL -> 0.85f + rng.nextFloat() * 0.55f
                    WeatherConditionKind.RAIN, WeatherConditionKind.FREEZING_RAIN -> 0.58f + rng.nextFloat() * 0.35f
                    WeatherConditionKind.DRIZZLE -> 0.36f + rng.nextFloat() * 0.22f
                    WeatherConditionKind.SNOW_HEAVY -> 0.16f + rng.nextFloat() * 0.14f
                    WeatherConditionKind.SNOW_LIGHT -> 0.10f + rng.nextFloat() * 0.08f
                    else -> 0f
                },
                size = 0.75f + rng.nextFloat() * 0.65f,
                swayPhase = rng.nextFloat() * 6.28f
            )
        }
    }

    val cloudCount = when (condition) {
        WeatherConditionKind.CLEAR -> 0
        WeatherConditionKind.PARTLY_CLOUDY -> 3
        else -> 5
    }
    val clouds = remember(cloudCount) {
        List(cloudCount) { idx ->
            SkyCloud(
                x = (idx.toFloat() / cloudCount.coerceAtLeast(1)) + rng.nextFloat() * 0.12f,
                y = 0.12f + (idx % 3) * 0.075f,
                scale = 0.82f + (idx % 3) * 0.22f,
                speed = 0.008f + rng.nextFloat() * 0.007f
            )
        }
    }

    val stars = remember {
        List(28) {
            Triple(rng.nextFloat(), 0.05f + rng.nextFloat() * 0.48f, rng.nextFloat() * 6.28f)
        }
    }

    val nowMillis = System.currentTimeMillis()
    val celestial = remember(nowMillis / 60_000L, snapshot.sunriseEpochMillis, snapshot.sunsetEpochMillis) {
        WeatherConditionMapper.computeCelestialState(
            nowMillis = nowMillis,
            sunriseMillis = snapshot.sunriseEpochMillis,
            sunsetMillis = snapshot.sunsetEpochMillis
        )
    }

    val shouldAnimate = !(particles.isEmpty() &&
        clouds.isEmpty() &&
        celestial.isDay &&
        condition != WeatherConditionKind.THUNDERSTORM_HAIL)

    var frameTick by remember { mutableLongStateOf(0L) }
    var elapsedSec by remember { mutableLongStateOf(0L) }
    var elapsedFloat = 0f
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(particles, clouds, condition, reduceLiveBlur, shouldAnimate, lifecycleOwner) {
        if (!shouldAnimate) return@LaunchedEffect
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
                    val dt = if (lastNanos == 0L) 0.016f else ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.04f)
                    lastNanos = frameNanos
                    elapsedFloat += dt
                    elapsedSec = (elapsedFloat * 1000f).toLong()

                    for (cloud in clouds) {
                        cloud.x += cloud.speed * dt
                        if (cloud.x > 1.25f) cloud.x = -0.25f
                    }

                    val isSnow = condition == WeatherConditionKind.SNOW_LIGHT || condition == WeatherConditionKind.SNOW_HEAVY
                    for (p in particles) {
                        p.y += p.speed * dt
                        p.x += if (isSnow) {
                            sin(elapsedFloat * 1.4f + p.swayPhase) * 0.03f * dt
                        } else {
                            -0.06f * dt
                        }
                        if (p.y > 1.05f) {
                            p.y = -0.05f
                            p.x = rng.nextFloat()
                        }
                        if (p.x < -0.05f) p.x += 1.1f
                        if (p.x > 1.05f) p.x -= 1.1f
                    }
                    frameTick = frameNanos
                }
            }
        }
    }

    val (skyTop, skyMid, skyBottom) = remember(
        celestial.isDay,
        celestial.elevationNormalized,
        condition,
        style
    ) {
        resolveSkyColors(
            isDay = celestial.isDay,
            elevation = celestial.elevationNormalized,
            condition = condition,
            style = style
        )
    }
    val skyBrush = remember(skyTop, skyMid, skyBottom) {
        Brush.verticalGradient(colors = listOf(skyTop, skyMid, skyBottom))
    }
    val fogBrush = remember(celestial.isDay) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = if (celestial.isDay) 0.42f else 0.18f),
                Color.White.copy(alpha = if (celestial.isDay) 0.58f else 0.25f)
            )
        )
    }
    val density = LocalDensity.current
    val boltStroke = remember(density) {
        with(density) {
            Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        }
    }
    val hillPath = remember { Path() }
    val boltPath = remember { Path() }
    val sunGlowCache = remember { RadialGlowCache() }
    val moonGlowCache = remember { RadialGlowCache() }

    Canvas(modifier = modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE")
        val tick = frameTick
        val tSec = elapsedSec / 1000f

        drawRect(brush = skyBrush)

        // Stars at night when not completely overcast/stormy
        if (!celestial.isDay && condition != WeatherConditionKind.OVERCAST && condition != WeatherConditionKind.FOG) {
            for ((sx, sy, phase) in stars) {
                val twinkle = 0.45f + 0.55f * ((sin(tSec * 2.1f + phase) + 1f) * 0.5f)
                val starPos = Offset(sx * size.width, sy * size.height)
                if (style == WeatherThemeStyle.CUTE_2D) {
                    drawCircle(color = Color(0xFFFFF9D6).copy(alpha = twinkle * 0.75f), radius = 2.1.dp.toPx(), center = starPos)
                } else {
                    drawCircle(color = Color.White.copy(alpha = twinkle * 0.65f), radius = 1.6.dp.toPx(), center = starPos)
                }
            }
        }

        // Sun (day) or Moon (night) traveling left -> top arc -> right
        val bodyCenter = Offset(
            x = size.width * (0.10f + 0.80f * celestial.arcProgress),
            y = size.height * (0.42f - 0.28f * celestial.elevationNormalized)
        )
        if (celestial.isDay) {
            if (condition != WeatherConditionKind.OVERCAST && condition != WeatherConditionKind.THUNDERSTORM_HAIL) {
                drawSun(
                    center = bodyCenter,
                    style = style,
                    dimmed = condition != WeatherConditionKind.CLEAR,
                    glowCache = sunGlowCache
                )
            }
        } else {
            drawMoon(
                center = bodyCenter,
                style = style,
                skyColor = skyTop,
                glowCache = moonGlowCache
            )
        }

        // Moving clouds
        for (cloud in clouds) {
            drawSkyCloud(
                center = Offset(cloud.x * size.width, cloud.y * size.height),
                scale = cloud.scale,
                isDay = celestial.isDay,
                stormy = condition == WeatherConditionKind.THUNDERSTORM_HAIL || condition == WeatherConditionKind.HEAVY_RAIN,
                style = style
            )
        }

        // Fog mist layers
        if (condition == WeatherConditionKind.FOG) {
            drawRect(brush = fogBrush)
        }

        // Lightning flash overlay & bolt on thunderstorm
        if (condition == WeatherConditionKind.THUNDERSTORM_HAIL) {
            val cycle = (tSec % 5.5f)
            if (cycle in 0.15f..0.28f || cycle in 0.36f..0.44f) {
                drawRect(color = Color(0xFFE0ECFF).copy(alpha = 0.22f))
                val boltStart = Offset(size.width * 0.66f, size.height * 0.18f)
                boltPath.reset()
                boltPath.moveTo(boltStart.x, boltStart.y)
                boltPath.lineTo(boltStart.x - 18.dp.toPx(), boltStart.y + 38.dp.toPx())
                boltPath.lineTo(boltStart.x + 6.dp.toPx(), boltStart.y + 42.dp.toPx())
                boltPath.lineTo(boltStart.x - 14.dp.toPx(), boltStart.y + 84.dp.toPx())
                drawPath(
                    path = boltPath,
                    color = Color(0xFFFDE68A),
                    style = boltStroke
                )
            }
        }

        // Precipitation (Rain, Drizzle, Freezing Rain, Snow, Hail)
        drawPrecipitation(particles = particles, condition = condition, style = style)

        // Compact foreground hill at bottom (~15% height so Frosted Glass cards keep clean readability)
        drawCompactHill(path = hillPath, isDay = celestial.isDay, condition = condition, style = style)
    }
}

private fun resolveSkyColors(
    isDay: Boolean,
    elevation: Float,
    condition: WeatherConditionKind,
    style: WeatherThemeStyle
): Triple<Color, Color, Color> {
    if (!isDay) {
        return when (style) {
            WeatherThemeStyle.CUTE_2D -> Triple(Color(0xFF162040), Color(0xFF21315E), Color(0xFF2E4278))
            WeatherThemeStyle.REALISTIC -> Triple(Color(0xFF0A1122), Color(0xFF13203C), Color(0xFF1D3158))
        }
    }
    if (condition == WeatherConditionKind.THUNDERSTORM_HAIL) {
        return Triple(Color(0xFF2A3447), Color(0xFF3E4B63), Color(0xFF56657D))
    }
    if (condition == WeatherConditionKind.OVERCAST || condition == WeatherConditionKind.RAIN || condition == WeatherConditionKind.HEAVY_RAIN || condition == WeatherConditionKind.FOG) {
        return Triple(Color(0xFF9FB4CC), Color(0xFFBBD0E4), Color(0xFFDCE7F2))
    }
    // Golden hour transition near sunrise/sunset (elevation < 0.25)
    val noonTop = if (style == WeatherThemeStyle.CUTE_2D) Color(0xFF7BC4FA) else Color(0xFF58A6EB)
    val noonMid = if (style == WeatherThemeStyle.CUTE_2D) Color(0xFFB4E0FD) else Color(0xFF9CD0F8)
    val noonBot = Color(0xFFEAF5FF)

    val goldenFactor = (1f - (elevation / 0.30f)).coerceIn(0f, 1f)
    val top = lerp(noonTop, Color(0xFF5D7BB0), goldenFactor * 0.7f)
    val mid = lerp(noonMid, Color(0xFFEFA57D), goldenFactor * 0.65f)
    val bot = lerp(noonBot, Color(0xFFFCD9B6), goldenFactor * 0.75f)
    return Triple(top, mid, bot)
}

private fun DrawScope.drawSun(
    center: Offset,
    style: WeatherThemeStyle,
    dimmed: Boolean,
    glowCache: RadialGlowCache
) {
    val alphaFactor = if (dimmed) 0.72f else 1f
    val r = 26.dp.toPx()
    if (style == WeatherThemeStyle.REALISTIC) {
        val glowRadius = r * 2.7f
        drawCircle(
            brush = glowCache.getOrCreate(center = center, radius = glowRadius, dimmed = dimmed) {
                listOf(
                    Color(0xFFFFF6C2).copy(alpha = 0.95f * alphaFactor),
                    Color(0xFFFCD34D).copy(alpha = 0.55f * alphaFactor),
                    Color.Transparent
                )
            },
            radius = glowRadius,
            center = center
        )
        drawCircle(color = Color(0xFFFFFBE0).copy(alpha = alphaFactor), radius = r * 0.88f, center = center)
    } else {
        // Cute 2D sun with crisp rays
        for (i in 0 until 8) {
            val angle = (i * 45.0) * PI / 180.0
            val p1 = center + Offset((cos(angle) * r * 1.25f).toFloat(), (sin(angle) * r * 1.25f).toFloat())
            val p2 = center + Offset((cos(angle) * r * 1.55f).toFloat(), (sin(angle) * r * 1.55f).toFloat())
            drawLine(
                color = Color(0xFFFBBF24).copy(alpha = 0.85f * alphaFactor),
                start = p1,
                end = p2,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        drawCircle(color = Color(0xFFFDE047).copy(alpha = alphaFactor), radius = r, center = center)
    }
}

private fun DrawScope.drawMoon(
    center: Offset,
    style: WeatherThemeStyle,
    skyColor: Color,
    glowCache: RadialGlowCache
) {
    val r = 22.dp.toPx()
    if (style == WeatherThemeStyle.REALISTIC) {
        val glowRadius = r * 2.6f
        drawCircle(
            brush = glowCache.getOrCreate(center = center, radius = glowRadius, dimmed = false) {
                listOf(
                    Color(0xFFE8F1FF).copy(alpha = 0.45f),
                    Color(0xFF93C5FD).copy(alpha = 0.14f),
                    Color.Transparent
                )
            },
            radius = glowRadius,
            center = center
        )
        drawCircle(color = Color(0xFFF3F6FC), radius = r * 0.85f, center = center)
        drawCircle(color = Color(0xFFD6E0F0).copy(alpha = 0.45f), radius = r * 0.22f, center = center + Offset(-r * 0.25f, r * 0.15f))
    } else {
        drawCircle(color = Color(0xFFFEF08A), radius = r, center = center)
        drawCircle(color = skyColor, radius = r * 0.84f, center = center + Offset(r * 0.36f, -r * 0.22f))
    }
}

private fun DrawScope.drawSkyCloud(
    center: Offset,
    scale: Float,
    isDay: Boolean,
    stormy: Boolean,
    style: WeatherThemeStyle
) {
    val r = 24.dp.toPx() * scale
    val cloudColor = when {
        stormy -> Color(0xFF64748B).copy(alpha = 0.82f)
        !isDay -> Color(0xFF475569).copy(alpha = 0.65f)
        style == WeatherThemeStyle.CUTE_2D -> Color.White.copy(alpha = 0.88f)
        else -> Color(0xFFF8FAFC).copy(alpha = 0.72f)
    }
    drawCircle(color = cloudColor, radius = r, center = center)
    drawCircle(color = cloudColor, radius = r * 0.78f, center = center + Offset(-r * 0.82f, r * 0.22f))
    drawCircle(color = cloudColor, radius = r * 0.72f, center = center + Offset(r * 0.84f, r * 0.25f))
}

private fun DrawScope.drawPrecipitation(
    particles: List<PrecipParticle>,
    condition: WeatherConditionKind,
    style: WeatherThemeStyle
) {
    if (particles.isEmpty()) return
    val isSnow = condition == WeatherConditionKind.SNOW_LIGHT || condition == WeatherConditionKind.SNOW_HEAVY
    val isFreezing = condition == WeatherConditionKind.FREEZING_RAIN

    for (p in particles) {
        val pos = Offset(p.x * size.width, p.y * size.height)
        if (isSnow) {
            drawCircle(
                color = Color.White.copy(alpha = if (style == WeatherThemeStyle.CUTE_2D) 0.88f else 0.74f),
                radius = (3.dp.toPx() * p.size),
                center = pos
            )
        } else {
            val dropColor = if (isFreezing) Color(0xFFBAE6FD) else Color(0xFF93C5FD)
            val len = (11.dp.toPx() * p.size)
            drawLine(
                color = dropColor.copy(alpha = 0.68f),
                start = pos,
                end = pos + Offset(-2.5.dp.toPx(), len),
                strokeWidth = (if (style == WeatherThemeStyle.CUTE_2D) 2.2f else 1.5f).dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawCompactHill(
    path: Path,
    isDay: Boolean,
    condition: WeatherConditionKind,
    style: WeatherThemeStyle
) {
    val hillTopY = size.height * 0.855f
    path.reset()
    path.moveTo(0f, size.height)
    path.lineTo(0f, hillTopY + 18.dp.toPx())
    path.quadraticTo(
        size.width * 0.34f,
        hillTopY - 24.dp.toPx(),
        size.width * 0.68f,
        hillTopY + 6.dp.toPx()
    )
    path.quadraticTo(
        size.width * 0.86f,
        hillTopY + 20.dp.toPx(),
        size.width,
        hillTopY - 4.dp.toPx()
    )
    path.lineTo(size.width, size.height)
    path.close()

    val hillColor = when {
        condition == WeatherConditionKind.SNOW_LIGHT || condition == WeatherConditionKind.SNOW_HEAVY ->
            if (isDay) Color(0xFFE7EEF6) else Color(0xFF4C5E78)
        !isDay -> Color(0xFF1A2E35)
        style == WeatherThemeStyle.CUTE_2D -> Color(0xFF81C784)
        else -> Color(0xFF4F8A68)
    }

    drawPath(path = path, color = hillColor)
}
