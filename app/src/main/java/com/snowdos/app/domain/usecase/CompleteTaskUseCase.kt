package com.snowdos.app.domain.usecase

import com.snowdos.app.core.common.TimeProvider
import com.snowdos.app.domain.model.ActivityEventType
import com.snowdos.app.domain.model.SyncState
import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.model.TaskActivityEvent
import com.snowdos.app.domain.model.TaskStatus
import com.snowdos.app.domain.repository.SettingsRepository
import com.snowdos.app.domain.repository.TaskRepository
import com.snowdos.app.domain.strategy.CompletionProofStrategy
import com.snowdos.app.domain.strategy.NoOpWidgetUpdateTrigger
import com.snowdos.app.domain.strategy.ProofInput
import com.snowdos.app.domain.strategy.ProofResult
import com.snowdos.app.domain.strategy.ProofType
import com.snowdos.app.domain.strategy.ReminderScheduler
import com.snowdos.app.domain.strategy.WidgetUpdateTrigger
import com.snowdos.app.domain.strategy.scheduleTaskCycle
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

sealed interface CompleteTaskOutcome {
    data class Completed(val task: Task, val pointsAwarded: Int) : CompleteTaskOutcome
    data class ProofRequired(val proofType: ProofType, val promptMessage: String) : CompleteTaskOutcome
    data class Rejected(val reason: String) : CompleteTaskOutcome
    data object NotActionable : CompleteTaskOutcome
}

class CompleteTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val completionProofStrategy: CompletionProofStrategy,
    private val reminderScheduler: ReminderScheduler,
    private val timeProvider: TimeProvider,
    private val widgetUpdateTrigger: WidgetUpdateTrigger
) {
    constructor(
        taskRepository: TaskRepository,
        settingsRepository: SettingsRepository,
        completionProofStrategy: CompletionProofStrategy,
        reminderScheduler: ReminderScheduler,
        timeProvider: TimeProvider
    ) : this(
        taskRepository = taskRepository,
        settingsRepository = settingsRepository,
        completionProofStrategy = completionProofStrategy,
        reminderScheduler = reminderScheduler,
        timeProvider = timeProvider,
        widgetUpdateTrigger = NoOpWidgetUpdateTrigger
    )
    suspend operator fun invoke(
        taskId: String,
        proofInput: ProofInput = ProofInput.None
    ): CompleteTaskOutcome {
        val task = taskRepository.getTaskById(taskId) ?: return CompleteTaskOutcome.NotActionable
        if (!task.status.isActionable) {
            return CompleteTaskOutcome.NotActionable
        }

        return when (val proofResult = completionProofStrategy.verify(task, proofInput)) {
            is ProofResult.RequiresInteraction -> CompleteTaskOutcome.ProofRequired(
                proofType = proofResult.proofType,
                promptMessage = proofResult.promptMessage
            )
            is ProofResult.Rejected -> CompleteTaskOutcome.Rejected(proofResult.reason)
            ProofResult.Verified -> {
                val now = timeProvider.nowEpochMillis()
                val settings = settingsRepository.observeSettings().first()
                val points = settings.scoreRules.completedPointsFor(task.priority)
                val event = TaskActivityEvent(
                    id = UUID.randomUUID().toString(),
                    taskId = task.id,
                    taskTitleSnapshot = task.title,
                    eventType = ActivityEventType.COMPLETED,
                    scoreDelta = points,
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
                        completedAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                        syncState = nextSyncState
                    )
                    taskRepository.applyLifecycleTransition(rescheduledTask, event)
                    reminderScheduler.cancelReminder(task.id)
                    reminderScheduler.scheduleTaskCycle(rescheduledTask, settings, now)
                    widgetUpdateTrigger.updateAll()
                    CompleteTaskOutcome.Completed(rescheduledTask, points)
                } else {
                    val completedTask = task.copy(
                        status = TaskStatus.COMPLETED,
                        completedAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                        syncState = nextSyncState
                    )
                    taskRepository.applyLifecycleTransition(completedTask, event)
                    reminderScheduler.cancelReminder(task.id)
                    widgetUpdateTrigger.updateAll()
                    CompleteTaskOutcome.Completed(completedTask, points)
                }
            }
        }
    }
}
