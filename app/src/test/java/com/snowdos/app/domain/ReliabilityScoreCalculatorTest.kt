package com.snowdos.app.domain

import com.snowdos.app.domain.model.ActivityEventType
import com.snowdos.app.domain.model.ScoreRulesConfig
import com.snowdos.app.domain.model.TaskActivityEvent
import com.snowdos.app.domain.strategy.ReliabilityPercentageScoreCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReliabilityScoreCalculatorTest {

    private val calculator = ReliabilityPercentageScoreCalculator()

    @Test
    fun `empty or only created events produce neutral score with hasScoreData false`() {
        val summary = calculator.calculate(
            events = listOf(event(ActivityEventType.CREATED, 0)),
            rules = ScoreRulesConfig()
        )
        assertFalse(summary.hasScoreData)
        assertEquals(100f, summary.reliabilityScorePercent, 0.01f)
        assertEquals(0, summary.netPoints)
    }

    @Test
    fun `calculates reliability percentage and counts across completed missed ignored`() {
        val events = listOf(
            event(ActivityEventType.COMPLETED, 1),
            event(ActivityEventType.COMPLETED, 1),
            event(ActivityEventType.COMPLETED, 1),
            event(ActivityEventType.MISSED, -1),
            event(ActivityEventType.IGNORED, -1),
            event(ActivityEventType.SNOOZED, 0)
        )

        val summary = calculator.calculate(events, ScoreRulesConfig())
        assertTrue(summary.hasScoreData)
        assertEquals(3, summary.completedCount)
        assertEquals(1, summary.missedCount)
        assertEquals(1, summary.ignoredCount)
        assertEquals(60f, summary.reliabilityScorePercent, 0.01f)
        assertEquals(1, summary.netPoints)
    }

    @Test
    fun `weights reliability score and net points by priority event deltas`() {
        val positiveHeavy = calculator.calculate(
            events = listOf(
                event(ActivityEventType.COMPLETED, 3), // HIGH (+3)
                event(ActivityEventType.MISSED, -1)    // LOW (-1)
            ),
            rules = ScoreRulesConfig()
        )
        assertEquals(75f, positiveHeavy.reliabilityScorePercent, 0.01f)
        assertEquals(2, positiveHeavy.netPoints)

        val negativeHeavy = calculator.calculate(
            events = listOf(
                event(ActivityEventType.COMPLETED, 1), // LOW (+1)
                event(ActivityEventType.IGNORED, -3)   // HIGH (-3)
            ),
            rules = ScoreRulesConfig()
        )
        assertEquals(25f, negativeHeavy.reliabilityScorePercent, 0.01f)
        assertEquals(-2, negativeHeavy.netPoints)
    }

    private fun event(type: ActivityEventType, delta: Int) = TaskActivityEvent(
        id = "e-${System.nanoTime()}",
        taskId = "t-1",
        taskTitleSnapshot = "Test",
        eventType = type,
        scoreDelta = delta,
        occurredAtEpochMillis = 1000L
    )
}
