package com.snowdos.app.domain.usecase

import com.snowdos.app.core.common.TimeProvider
import com.snowdos.app.domain.model.RecurrenceRule
import com.snowdos.app.domain.model.SyncState
import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.model.TaskPriority
import com.snowdos.app.domain.repository.SettingsRepository
import com.snowdos.app.domain.repository.TaskRepository
import com.snowdos.app.domain.strategy.NoOpWidgetUpdateTrigger
import com.snowdos.app.domain.strategy.ReminderScheduler
import com.snowdos.app.domain.strategy.WidgetUpdateTrigger
import com.snowdos.app.domain.strategy.scheduleTaskCycle
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class UpdateTaskUseCase @Inject constructor(
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
        title: String,
        notes: String,
        priority: TaskPriority,
        dueAtEpochMillis: Long?,
        remindAtEpochMillis: Long?,
        recurrence: RecurrenceRule? = null
    ): Result<Task> {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            return Result.failure(EmptyTaskTitleException())
        }

        val existing = taskRepository.getTaskById(taskId)
            ?: return Result.failure(TaskNotFoundException())

        val now = timeProvider.nowEpochMillis()
        val updated = existing.copy(
            title = cleanTitle,
            notes = notes.trim(),
            priority = priority,
            recurrence = recurrence ?: existing.recurrence,
            dueAtEpochMillis = dueAtEpochMillis,
            remindAtEpochMillis = remindAtEpochMillis,
            updatedAtEpochMillis = now,
            syncState = if (existing.remoteId != null) SyncState.PENDING_SYNC else existing.syncState
        )

        taskRepository.updateTask(updated)
        reminderScheduler.cancelReminder(updated.id)

        val settings = settingsRepository.observeSettings().first()
        reminderScheduler.scheduleTaskCycle(updated, settings, now)
        widgetUpdateTrigger.updateAll()

        return Result.success(updated)
    }
}
