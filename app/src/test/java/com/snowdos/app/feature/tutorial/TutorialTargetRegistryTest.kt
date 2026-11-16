package com.snowdos.app.feature.tutorial

import androidx.compose.ui.geometry.Rect
import com.snowdos.app.feature.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TutorialTargetRegistryTest {

    @Test
    fun `tutorial steps follow 5-step flow across Tasks, Stats, and Settings`() {
        val steps = TutorialStep.ordered
        assertEquals(5, steps.size)
        assertEquals(
            listOf(
                TutorialTargetId.ADD_TASK,
                TutorialTargetId.SEARCH_AND_FILTER,
                TutorialTargetId.TASK_ACTIONS,
                TutorialTargetId.STATS_OVERVIEW,
                TutorialTargetId.SETTINGS_HIGHLIGHTS
            ),
            steps.map { it.targetId }
        )
        assertEquals(
            listOf(
                TopLevelDestination.TASKS,
                TopLevelDestination.TASKS,
                TopLevelDestination.TASKS,
                TopLevelDestination.STATS,
                TopLevelDestination.SETTINGS
            ),
            steps.map { it.destination }
        )
    }

    @Test
    fun `registry ignores bounds when disabled and updates valid bounds when enabled`() {
        val registry = TutorialTargetRegistry()
        val fabRect = Rect(800f, 1600f, 940f, 1740f)

        // Inactive tutorial must ignore bounds updates completely
        registry.update(TutorialTargetId.ADD_TASK, fabRect)
        assertNull(registry.get(TutorialTargetId.ADD_TASK))

        registry.isEnabled = true
        registry.update(TutorialTargetId.ADD_TASK, Rect.Zero)
        assertNull(registry.get(TutorialTargetId.ADD_TASK))

        registry.update(TutorialTargetId.ADD_TASK, fabRect)
        assertEquals(fabRect, registry.get(TutorialTargetId.ADD_TASK))
    }
}
