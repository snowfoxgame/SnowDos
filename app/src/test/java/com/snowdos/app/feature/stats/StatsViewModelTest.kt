package com.snowdos.app.feature.stats

import app.cash.turbine.test
import com.snowdos.app.domain.model.ActivityEventType
import com.snowdos.app.domain.model.TaskActivityEvent
import com.snowdos.app.domain.strategy.ReliabilityPercentageScoreCalculator
import com.snowdos.app.domain.usecase.ObserveScoreStatsUseCase
import com.snowdos.app.fakes.FakeActivityRepository
import com.snowdos.app.fakes.FakeSettingsRepository
import com.snowdos.app.fakes.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StatsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `stats viewModel emits live gauge summary and events`() = runTest {
        val activityRepo = FakeActivityRepository()
        val settingsRepo = FakeSettingsRepository()
        val viewModel = StatsViewModel(
            observeScoreStatsUseCase = ObserveScoreStatsUseCase(
                activityRepository = activityRepo,
                settingsRepository = settingsRepo,
                scoreCalculator = ReliabilityPercentageScoreCalculator()
            ),
            activityRepository = activityRepo
        )

        viewModel.uiState.test {
            val empty = awaitItem()
            assertEquals(0, empty.summary.completedCount)

            activityRepo.insertEvent(
                TaskActivityEvent(
                    id = "0",
                    taskId = "t1",
                    taskTitleSnapshot = "Lesen",
                    eventType = ActivityEventType.CREATED,
                    scoreDelta = 0,
                    occurredAtEpochMillis = 1000L
                )
            )
            activityRepo.insertEvent(
                TaskActivityEvent(
                    id = "1",
                    taskId = "t1",
                    taskTitleSnapshot = "Lesen",
                    eventType = ActivityEventType.COMPLETED,
                    scoreDelta = 3,
                    occurredAtEpochMillis = 2000L
                )
            )
            val oneCompleted = awaitItem()
            assertTrue(oneCompleted.summary.hasScoreData)
            assertEquals(1, oneCompleted.summary.completedCount)
            assertEquals(100f, oneCompleted.summary.reliabilityScorePercent, 0.01f)
            assertEquals(3, oneCompleted.summary.netPoints)
            assertEquals(listOf(ActivityEventType.COMPLETED), oneCompleted.events.map { it.eventType })

            activityRepo.insertEvent(
                TaskActivityEvent(
                    id = "1b",
                    taskId = "t2",
                    taskTitleSnapshot = "Laufen",
                    eventType = ActivityEventType.SNOOZED,
                    scoreDelta = 0,
                    occurredAtEpochMillis = 2500L
                )
            )
            activityRepo.insertEvent(
                TaskActivityEvent(
                    id = "2",
                    taskId = "t2",
                    taskTitleSnapshot = "Laufen",
                    eventType = ActivityEventType.MISSED,
                    scoreDelta = -1,
                    occurredAtEpochMillis = 3000L
                )
            )
            val afterMiss = awaitItem()
            assertEquals(1, afterMiss.summary.completedCount)
            assertEquals(1, afterMiss.summary.missedCount)
            assertEquals(75f, afterMiss.summary.reliabilityScorePercent, 0.01f)
            assertEquals(2, afterMiss.summary.netPoints)

            activityRepo.insertEvent(
                TaskActivityEvent(
                    id = "3",
                    taskId = "t3",
                    taskTitleSnapshot = "Dehnen",
                    eventType = ActivityEventType.IGNORED,
                    scoreDelta = -1,
                    occurredAtEpochMillis = 4000L
                )
            )
            val afterIgnore = awaitItem()
            assertEquals(1, afterIgnore.summary.ignoredCount)
            assertEquals(
                listOf(
                    ActivityEventType.IGNORED,
                    ActivityEventType.MISSED,
                    ActivityEventType.COMPLETED
                ),
                afterIgnore.events.map { it.eventType }
            )

            // Deleting a single MISSED event immediately recalculates score, netPoints, and counters
            viewModel.deleteEvent("2")
            val afterDeleteMiss = awaitItem()
            assertEquals(1, afterDeleteMiss.summary.completedCount)
            assertEquals(0, afterDeleteMiss.summary.missedCount)
            assertEquals(1, afterDeleteMiss.summary.ignoredCount)
            assertEquals(75f, afterDeleteMiss.summary.reliabilityScorePercent, 0.01f)
            assertEquals(2, afterDeleteMiss.summary.netPoints)
            assertEquals(listOf("3", "1"), afterDeleteMiss.events.map { it.id })

            // Clearing all events resets summary and history
            viewModel.clearAllEvents()
            val afterClear = awaitItem()
            assertEquals(0, afterClear.summary.completedCount)
            assertEquals(0, afterClear.summary.missedCount)
            assertEquals(0, afterClear.summary.ignoredCount)
            assertEquals(0, afterClear.summary.netPoints)
            assertTrue(afterClear.events.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
