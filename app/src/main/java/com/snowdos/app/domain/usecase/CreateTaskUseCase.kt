package com.snowdos.app.domain.usecase

import com.snowdos.app.core.common.TimeProvider
import com.snowdos.app.domain.model.ActivityEventType
import com.snowdos.app.domain.model.RecurrenceRule
import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.model.TaskActivityEvent
import com.snowdos.app.domain.model.TaskPriority
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

class CreateTaskUseCase @Inject constructor(
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
        title: String,
        notes: String = "",
        priority: TaskPriority = TaskPriority.MEDIUM,
        recurrence: RecurrenceRule = RecurrenceRule.NONE,
        dueAtEpochMillis: Long? = null,
        remindAtEpochMillis: Long? = null
    ): Result<Task> {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            return Result.failure(EmptyTaskTitleException())
        }

        val now = timeProvider.nowEpochMillis()
        val taskId = UUID.randomUUID().toString()
        val task = Task(
            id = taskId,
            title = cleanTitle,
            notes = notes.trim(),
            priority = priority,
            status = TaskStatus.OPEN,
            recurrence = recurrence,
            dueAtEpochMillis = dueAtEpochMillis,
            remindAtEpochMillis = remindAtEpochMillis,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now
        )
        val createdEvent = TaskActivityEvent(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            taskTitleSnapshot = cleanTitle,
            eventType = ActivityEventType.CREATED,
            scoreDelta = 0,
            occurredAtEpochMillis = now
        )

        taskRepository.insertTaskWithEvent(task, createdEvent)

        val settings = settingsRepository.observeSettings().first()
        reminderScheduler.scheduleTaskCycle(task, settings, now)
        widgetUpdateTrigger.updateAll()

        return Result.success(task)
    }
}
