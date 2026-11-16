package com.snowdos.app.domain.usecase

import com.snowdos.app.core.common.TimeProvider
import com.snowdos.app.domain.model.ActivityEventType
import com.snowdos.app.domain.model.DismissBehavior
import com.snowdos.app.domain.model.SyncState
import com.snowdos.app.domain.model.TaskActivityEvent
import com.snowdos.app.domain.model.TaskStatus
import com.snowdos.app.domain.repository.SettingsRepository
import com.snowdos.app.domain.repository.TaskRepository
import com.snowdos.app.domain.strategy.NoOpWidgetUpdateTrigger
import com.snowdos.app.domain.strategy.ReminderScheduler
import com.snowdos.app.domain.strategy.WidgetUpdateTrigger
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

sealed interface DismissTaskOutcome {
    data class MarkedIgnored(val scoreDelta: Int) : DismissTaskOutcome
    data object Deleted : DismissTaskOutcome
    data object NotFound : DismissTaskOutcome
}

class DismissTaskUseCase @Inject constructor(
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
    suspend operator fun invoke(taskId: String): DismissTaskOutcome {
        val task = taskRepository.getTaskById(taskId) ?: return DismissTaskOutcome.NotFound
        val settings = settingsRepository.observeSettings().first()
        reminderScheduler.cancelReminder(task.id)

        if (settings.dismissBehavior == DismissBehavior.DELETE_PERMANENTLY || !task.status.isActionable) {
            taskRepository.deleteTaskById(task.id)
            widgetUpdateTrigger.updateAll()
            return DismissTaskOutcome.Deleted
        }

        val now = timeProvider.nowEpochMillis()
        val penalty = settings.scoreRules.ignoredPenaltyFor(task.priority)
        val ignoredTask = task.copy(
            status = TaskStatus.IGNORED,
            updatedAtEpochMillis = now,
            syncState = if (task.remoteId != null) SyncState.PENDING_SYNC else task.syncState
        )
        val event = TaskActivityEvent(
            id = UUID.randomUUID().toString(),
            taskId = task.id,
            taskTitleSnapshot = task.title,
            eventType = ActivityEventType.IGNORED,
            scoreDelta = penalty,
            occurredAtEpochMillis = now
        )
        taskRepository.applyLifecycleTransition(ignoredTask, event)
        widgetUpdateTrigger.updateAll()
        return DismissTaskOutcome.MarkedIgnored(penalty)
    }
}
