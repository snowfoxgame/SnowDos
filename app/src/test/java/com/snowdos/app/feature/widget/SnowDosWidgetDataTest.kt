package com.snowdos.app.feature.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.snowdos.app.domain.model.EightBitVariant
import com.snowdos.app.domain.model.RecurrenceRule
import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.model.TaskPriority
import com.snowdos.app.domain.model.TaskStatus
import com.snowdos.app.domain.model.ThemeAppearanceConfig
import com.snowdos.app.domain.model.ThemeMode
import com.snowdos.app.domain.model.WidgetColorMode
import com.snowdos.app.domain.model.WidgetSurfaceStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnowDosWidgetDataTest {

    @Test
    fun `selectNewestOpenTasks filters non-actionable and keeps newest open first`() {
        val tasks = listOf(
            sampleTask(id = "old_open", status = TaskStatus.OPEN, createdAt = 100L),
            sampleTask(id = "completed_newest", status = TaskStatus.COMPLETED, createdAt = 500L),
            sampleTask(id = "middle_snoozed", status = TaskStatus.SNOOZED, createdAt = 250L),
            sampleTask(id = "newest_open", status = TaskStatus.OPEN, createdAt = 400L, recurrence = RecurrenceRule.DAILY),
            sampleTask(id = "second_open", status = TaskStatus.OPEN, createdAt = 300L),
            sampleTask(id = "ignored_open", status = TaskStatus.IGNORED, createdAt = 600L)
        )

        val (visible, remaining) = SnowDosWidgetData.selectNewestOpenTasks(tasks = tasks, maxVisible = 3)

        assertEquals(listOf("newest_open", "second_open", "middle_snoozed"), visible.map { it.id })
        assertEquals(1, remaining)
        assertTrue(visible.first().isRecurring)
    }

    @Test
    fun `maxTasksForWidgetHeight scales smoothly from 1 to 3`() {
        assertEquals(1, SnowDosWidgetData.maxTasksForWidgetHeight(110.dp))
        assertEquals(1, SnowDosWidgetData.maxTasksForWidgetHeight(149.dp))
        assertEquals(2, SnowDosWidgetData.maxTasksForWidgetHeight(160.dp))
        assertEquals(2, SnowDosWidgetData.maxTasksForWidgetHeight(214.dp))
        assertEquals(3, SnowDosWidgetData.maxTasksForWidgetHeight(220.dp))
    }

    @Test
    fun `resolveIsDark and resolveIsGlass obey explicit overrides and app fallback`() {
        val lightPresetAppearance = ThemeAppearanceConfig(
            mode = ThemeMode.PRESET,
            presetId = "standard_m3"
        )
        assertFalse(SnowDosWidgetData.resolveIsDark(WidgetColorMode.LIGHT, lightPresetAppearance, systemDarkTheme = true))
        assertTrue(SnowDosWidgetData.resolveIsDark(WidgetColorMode.DARK, lightPresetAppearance, systemDarkTheme = false))
        assertTrue(SnowDosWidgetData.resolveIsDark(WidgetColorMode.FOLLOW_APP, lightPresetAppearance, systemDarkTheme = true))
        assertFalse(SnowDosWidgetData.resolveIsDark(WidgetColorMode.FOLLOW_APP, lightPresetAppearance, systemDarkTheme = false))

        assertTrue(SnowDosWidgetData.resolveIsGlass(WidgetSurfaceStyle.GLASS))
        assertFalse(SnowDosWidgetData.resolveIsGlass(WidgetSurfaceStyle.SOLID))
    }

    @Test
    fun `resolvePaletteSpec syncs Gaming LED accent and accent border when FOLLOWING app`() {
        val gamingAppearance = ThemeAppearanceConfig(
            mode = ThemeMode.PRESET,
            presetId = "gaming",
            gamingAccentHex = "#FF2E93"
        )

        val spec = SnowDosWidgetData.resolvePaletteSpec(
            colorMode = WidgetColorMode.FOLLOW_APP,
            surfaceStyle = WidgetSurfaceStyle.GLASS,
            appearance = gamingAppearance,
            systemDarkTheme = false
        )

        assertTrue(spec.followAppTheme)
        assertTrue(spec.isDark)
        assertFalse(spec.isEightBitRetro)
        assertEquals(Color(0xFFFF2E93), spec.accentColor)
        assertEquals(22f, spec.outerCornerDp)
        assertEquals(Color(0xFFFF2E93).copy(alpha = 0.62f), spec.borderColor)
    }

    @Test
    fun `resolvePaletteSpec activates squared retro corners and pink or blue palette for 8-Bit Cute`() {
        val pinkSpec = SnowDosWidgetData.resolvePaletteSpec(
            colorMode = WidgetColorMode.FOLLOW_APP,
            surfaceStyle = WidgetSurfaceStyle.GLASS,
            appearance = ThemeAppearanceConfig(
                mode = ThemeMode.PRESET,
                presetId = "8bit_cute",
                eightBitVariant = EightBitVariant.PASTEL_PINK
            ),
            systemDarkTheme = true
        )
        val blueSpec = SnowDosWidgetData.resolvePaletteSpec(
            colorMode = WidgetColorMode.FOLLOW_APP,
            surfaceStyle = WidgetSurfaceStyle.SOLID,
            appearance = ThemeAppearanceConfig(
                mode = ThemeMode.PRESET,
                presetId = "8bit_cute",
                eightBitVariant = EightBitVariant.PASTEL_BLUE
            ),
            systemDarkTheme = true
        )

        assertTrue(pinkSpec.isEightBitRetro)
        assertFalse(pinkSpec.isDark)
        assertEquals(2f, pinkSpec.outerCornerDp)
        assertEquals(0f, pinkSpec.innerCornerDp)
        assertEquals(2f, pinkSpec.borderWidthDp)
        assertEquals(Color(0xFFD83B78), pinkSpec.accentColor)

        assertTrue(blueSpec.isEightBitRetro)
        assertFalse(blueSpec.isDark)
        assertEquals(2f, blueSpec.outerCornerDp)
        assertEquals(0f, blueSpec.innerCornerDp)
        assertEquals(Color(0xFF2B6FD6), blueSpec.accentColor)
    }

    @Test
    fun `resolvePaletteSpec adopts Custom accent hex and disables retro mode for explicit Light or Dark`() {
        val customAppearance = ThemeAppearanceConfig(
            mode = ThemeMode.CUSTOM,
            customAccentHex = "#10B981",
            glassOpacity = 0.30f
        )

        val customFollow = SnowDosWidgetData.resolvePaletteSpec(
            colorMode = WidgetColorMode.FOLLOW_APP,
            surfaceStyle = WidgetSurfaceStyle.GLASS,
            appearance = customAppearance,
            systemDarkTheme = false
        )
        assertTrue(customFollow.followAppTheme)
        assertEquals(Color(0xFF10B981), customFollow.accentColor)

        val explicitLightOverEightBit = SnowDosWidgetData.resolvePaletteSpec(
            colorMode = WidgetColorMode.LIGHT,
            surfaceStyle = WidgetSurfaceStyle.GLASS,
            appearance = ThemeAppearanceConfig(mode = ThemeMode.PRESET, presetId = "8bit_cute"),
            systemDarkTheme = true
        )
        assertFalse(explicitLightOverEightBit.followAppTheme)
        assertFalse(explicitLightOverEightBit.isEightBitRetro)
        assertFalse(explicitLightOverEightBit.isDark)
    }

    @Test
    fun `computeScaledDimensions clamps longest edge within widget binder limit`() {
        assertEquals(560 to 280, SnowDosWidgetData.computeScaledDimensions(2400, 1200))
        assertEquals(280 to 560, SnowDosWidgetData.computeScaledDimensions(1000, 2000))
        assertEquals(400 to 300, SnowDosWidgetData.computeScaledDimensions(400, 300))
        assertEquals(0 to 0, SnowDosWidgetData.computeScaledDimensions(0, 100))
    }

    private fun sampleTask(
        id: String,
        status: TaskStatus,
        createdAt: Long,
        recurrence: RecurrenceRule = RecurrenceRule.NONE
    ): Task = Task(
        id = id,
        title = "Task $id",
        priority = TaskPriority.MEDIUM,
        status = status,
        recurrence = recurrence,
        createdAtEpochMillis = createdAt,
        updatedAtEpochMillis = createdAt
    )
}
