package com.snowdos.app.domain.usecase

import com.snowdos.app.core.common.TimeProvider
import com.snowdos.app.domain.model.ActivityEventType
import com.snowdos.app.domain.model.SyncState
import com.snowdos.app.domain.model.TaskActivityEvent
import com.snowdos.app.domain.model.TaskStatus
import com.snowdos.app.domain.repository.SettingsRepository
import com.snowdos.app.domain.repository.TaskRepository
import com.snowdos.app.domain.strategy.NoOpWidgetUpdateTrigger
import com.snowdos.app.domain.strategy.ReminderScheduler
import com.snowdos.app.domain.strategy.WidgetUpdateTrigger
import com.snowdos.app.domain.strategy.scheduleTaskCycle
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

/**
 * Evaluates actionable tasks whose deadline (+ optional grace period) has elapsed:
 * - Non-recurring tasks transition to [TaskStatus.MISSED] and record a missed penalty event.
 * - Recurring tasks record the [ActivityEventType.MISSED] penalty event AND roll forward to their
 *   next interval anchored to the original due time so ongoing routines never die silently.
 */
class ProcessOverdueTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val timeProvider: TimeProvider,
    private val widgetUpdateTrigger: WidgetUpdateTrigger
) {
    constructor(
        taskRepository: TaskRepository,
        settingsRepository: SettingsRepository,
        reminderScheduler: ReminderScheduler,
        timeProvider: TimeProvider
    ) : this(
        taskRepository = taskRepository,
        settingsRepository = settingsRepository,
        reminderScheduler = reminderScheduler,
        timeProvider = timeProvider,
        widgetUpdateTrigger = NoOpWidgetUpdateTrigger
    )
    suspend operator fun invoke(): Int {
        val now = timeProvider.nowEpochMillis()
        val settings = settingsRepository.observeSettings().first()
        val graceMillis = settings.scoreRules.missedGracePeriodMinutes.coerceAtLeast(0) * 60_000L

        val candidates = taskRepository.getActionableTasksWithDeadline()
        var missedCount = 0

        for (task in candidates) {
            val dueAt = task.dueAtEpochMillis ?: continue
            if (!task.status.isActionable) continue

            val effectiveDeadline = if (task.status == TaskStatus.SNOOZED && task.remindAtEpochMillis != null) {
                maxOf(dueAt, task.remindAtEpochMillis)
            } else {
                dueAt
            }

            if (now >= effectiveDeadline + graceMillis) {
                val penalty = settings.scoreRules.missedPenaltyFor(task.priority)
                val event = TaskActivityEvent(
                    id = UUID.randomUUID().toString(),
                    taskId = task.id,
                    taskTitleSnapshot = task.title,
                    eventType = ActivityEventType.MISSED,
                    scoreDelta = penalty,
                    occurredAtEpochMillis = now
                )
                val nextSyncState = if (task.remoteId != null) SyncState.PENDING_SYNC else task.syncState

                if (task.recurrence.isRecurring) {
                    val (nextDue, nextRemind) = task.recurrence.nextCycleTimestamps(
                        dueAtEpochMillis = task.dueAtEpochMillis,
                        remindAtEpochMillis = task.remindAtEpochMillis,
                        wasSnoozed = task.status == TaskStatus.SNOOZED,
                        nowMillis = now
                    )
                    val rescheduledTask = task.copy(
                        status = TaskStatus.OPEN,
                        snoozeCount = 0,
                        dueAtEpochMillis = nextDue,
                        remindAtEpochMillis = nextRemind,
                        updatedAtEpochMillis = now,
                        syncState = nextSyncState
                    )
                    taskRepository.applyLifecycleTransition(rescheduledTask, event)
                    reminderScheduler.cancelReminder(task.id)
                    reminderScheduler.scheduleTaskCycle(rescheduledTask, settings, now)
                } else {
                    val missedTask = task.copy(
                        status = TaskStatus.MISSED,
                        updatedAtEpochMillis = now,
                        syncState = nextSyncState
                    )
                    taskRepository.applyLifecycleTransition(missedTask, event)
                    reminderScheduler.cancelReminder(task.id)
                }
                missedCount++
            }
        }

        if (missedCount > 0) {
            widgetUpdateTrigger.updateAll()
        }

        return missedCount
    }
}
