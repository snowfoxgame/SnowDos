package com.snowdos.app.feature.tasks

import androidx.compose.runtime.Immutable
import com.snowdos.app.core.common.UiText
import com.snowdos.app.domain.model.DismissBehavior
import com.snowdos.app.domain.model.RecurrenceRule
import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.model.TaskPriority
import com.snowdos.app.domain.strategy.ProofType
import com.snowdos.app.domain.usecase.TaskListFilter

@Immutable
data class ProofPromptState(
    val taskId: String,
    val proofType: ProofType,
    val message: String
)

@Immutable
data class TasksUiState(
    val searchQuery: String = "",
    val selectedFilter: TaskListFilter = TaskListFilter.ACTIVE,
    val tasks: List<Task> = emptyList(),
    val openCount: Int = 0,
    val completedCount: Int = 0,
    val defaultSnoozeMinutes: Int = 15,
    val dismissBehavior: DismissBehavior = DismissBehavior.MARK_IGNORED,
    val isEditorOpen: Boolean = false,
    val editingTask: Task? = null,
    val snoozeTargetTask: Task? = null,
    val proofPrompt: ProofPromptState? = null
)

sealed interface TasksAction {
    data class OnSearchQueryChange(val query: String) : TasksAction
    data class OnFilterSelected(val filter: TaskListFilter) : TasksAction
    data class OnCheckTask(val task: Task) : TasksAction
    data class OnSubmitProof(val taskId: String, val reference: String) : TasksAction
    data object OnDismissProofDialog : TasksAction
    data object OnOpenCreateEditor : TasksAction
    data class OnOpenEditEditor(val task: Task) : TasksAction
    data object OnCloseEditor : TasksAction
    data class OnSaveTask(
        val title: String,
        val notes: String,
        val priority: TaskPriority,
        val recurrence: RecurrenceRule = RecurrenceRule.NONE,
        val dueAtEpochMillis: Long?,
        val remindAtEpochMillis: Long?
    ) : TasksAction
    data class OnOpenSnoozePicker(val task: Task) : TasksAction
    data object OnCloseSnoozePicker : TasksAction
    data class OnConfirmSnooze(val taskId: String, val minutes: Int) : TasksAction
    data class OnDismissTask(val task: Task) : TasksAction
    data class OnOpenTaskFromNotification(val taskId: String, val triggerProof: Boolean = false) : TasksAction
    data object OnRefreshOverdue : TasksAction
}

sealed interface TasksEffect {
    data class ShowMessage(val message: UiText) : TasksEffect
    data class ScrollToTask(val taskId: String) : TasksEffect
}
