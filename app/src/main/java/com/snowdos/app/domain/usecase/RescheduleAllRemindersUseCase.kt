package com.snowdos.app.domain.usecase

import com.snowdos.app.core.common.SystemTimeProvider
import com.snowdos.app.core.common.TimeProvider
import com.snowdos.app.domain.repository.SettingsRepository
import com.snowdos.app.domain.repository.TaskRepository
import com.snowdos.app.domain.strategy.ReminderScheduler
import com.snowdos.app.domain.strategy.scheduleTaskCycle
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Re-synchronizes local alarms and overdue checks for all actionable tasks after settings changes
 * (such as toggling notifications or adjusting lead time / overdue grace period).
 */
class RescheduleAllRemindersUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val timeProvider: TimeProvider
) {
    constructor(
        taskRepository: TaskRepository,
        settingsRepository: SettingsRepository,
        reminderScheduler: ReminderScheduler
    ) : this(
        taskRepository = taskRepository,
        settingsRepository = settingsRepository,
        reminderScheduler = reminderScheduler,
        timeProvider = SystemTimeProvider()
    )

    suspend operator fun invoke() {
        val settings = settingsRepository.observeSettings().first()
        val now = timeProvider.nowEpochMillis()
        val actionable = (taskRepository.getActionableTasksWithReminder() +
            taskRepository.getActionableTasksWithDeadline())
            .distinctBy { it.id }

        for (task in actionable) {
            reminderScheduler.cancelReminder(task.id)
            reminderScheduler.scheduleTaskCycle(task, settings, now)
        }
    }
}
