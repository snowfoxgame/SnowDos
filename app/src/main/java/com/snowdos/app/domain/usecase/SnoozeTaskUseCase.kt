package com.snowdos.app.domain.usecase

import com.snowdos.app.core.common.TimeProvider
import com.snowdos.app.domain.model.ActivityEventType
import com.snowdos.app.domain.model.SyncState
import com.snowdos.app.domain.model.Task
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

class SnoozeTaskUseCase @Inject constructor(
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
    suspend operator fun invoke(
        taskId: String,
        snoozeMinutes: Int? = null,
        metadataReason: String? = null
    ): Result<Task> {
        val task = taskRepository.getTaskById(taskId)
            ?: return Result.failure(TaskNotFoundException())

        if (!task.status.isActionable) {
            return Result.failure(TaskNotActionableException())
        }

        val settings = settingsRepository.observeSettings().first()
        val effectiveMinutes = (snoozeMinutes ?: settings.defaultSnoozeMinutes).coerceAtLeast(1)
        val now = timeProvider.nowEpochMillis()
        val newRemindAt = now + effectiveMinutes * 60_000L
        // Keep the original base due time intact for recurring tasks so daily routines never drift.
        val updatedDueAt = when {
            task.dueAtEpochMillis == null -> null
            task.recurrence.isRecurring -> task.dueAtEpochMillis
            task.dueAtEpochMillis < newRemindAt -> newRemindAt
            else -> task.dueAtEpochMillis
        }

        val snoozedTask = task.copy(
            status = TaskStatus.SNOOZED,
            snoozeCount = task.snoozeCount + 1,
            remindAtEpochMillis = newRemindAt,
            dueAtEpochMillis = updatedDueAt,
            updatedAtEpochMillis = now,
            syncState = if (task.remoteId != null) SyncState.PENDING_SYNC else task.syncState
        )

        val reasonPart = metadataReason?.let { ",\"reason\":\"$it\"" }.orEmpty()
        val event = TaskActivityEvent(
            id = UUID.randomUUID().toString(),
            taskId = task.id,
            taskTitleSnapshot = task.title,
            eventType = ActivityEventType.SNOOZED,
            scoreDelta = 0,
            occurredAtEpochMillis = now,
            metadataJson = "{\"snoozeMinutes\":$effectiveMinutes,\"snoozeCount\":${snoozedTask.snoozeCount}$reasonPart}"
        )

        taskRepository.applyLifecycleTransition(snoozedTask, event)
        reminderScheduler.scheduleTaskCycle(snoozedTask, settings, now)
        widgetUpdateTrigger.updateAll()

        return Result.success(snoozedTask)
    }
}
