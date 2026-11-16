package com.snowdos.app.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snowdos.app.R
import com.snowdos.app.core.common.UiText
import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.repository.SettingsRepository
import com.snowdos.app.domain.repository.TaskRepository
import com.snowdos.app.domain.strategy.ProofInput
import com.snowdos.app.domain.usecase.CompleteTaskOutcome
import com.snowdos.app.domain.usecase.CompleteTaskUseCase
import com.snowdos.app.domain.usecase.CreateTaskUseCase
import com.snowdos.app.domain.usecase.DismissTaskOutcome
import com.snowdos.app.domain.usecase.DismissTaskUseCase
import com.snowdos.app.domain.usecase.EmptyTaskTitleException
import com.snowdos.app.domain.usecase.ObserveTasksUseCase
import com.snowdos.app.domain.usecase.ProcessOverdueTasksUseCase
import com.snowdos.app.domain.usecase.SnoozeTaskUseCase
import com.snowdos.app.domain.usecase.TaskListFilter
import com.snowdos.app.domain.usecase.TaskNotActionableException
import com.snowdos.app.domain.usecase.TaskNotFoundException
import com.snowdos.app.domain.usecase.UpdateTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val observeTasksUseCase: ObserveTasksUseCase,
    private val createTaskUseCase: CreateTaskUseCase,
    private val updateTaskUseCase: UpdateTaskUseCase,
    private val completeTaskUseCase: CompleteTaskUseCase,
    private val snoozeTaskUseCase: SnoozeTaskUseCase,
    private val dismissTaskUseCase: DismissTaskUseCase,
    private val processOverdueTasksUseCase: ProcessOverdueTasksUseCase,
    private val taskRepository: TaskRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(TaskListFilter.ACTIVE)
    private val editorState = MutableStateFlow<Pair<Boolean, Task?>>(false to null)
    private val snoozeTarget = MutableStateFlow<Task?>(null)
    private val proofPrompt = MutableStateFlow<ProofPromptState?>(null)

    private val _effects = Channel<TasksEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val tasksWithCriteria = combine(searchQuery, selectedFilter) { query, filter ->
        query to filter
    }.flatMapLatest { (query, filter) ->
        observeTasksUseCase.observeSnapshot(query = query, filter = filter).map { snapshot ->
            Triple(query, filter, snapshot)
        }
    }

    val uiState: StateFlow<TasksUiState> = combine(
        tasksWithCriteria,
        settingsRepository.observeSettings(),
        combine(editorState, snoozeTarget, proofPrompt) { editor, snooze, proof ->
            Triple(editor, snooze, proof)
        }
    ) { (query, filter, snapshot), settings, modalState ->
        val (editor, snooze, proof) = modalState
        TasksUiState(
            searchQuery = query,
            selectedFilter = filter,
            tasks = snapshot.tasks,
            openCount = snapshot.openCount,
            completedCount = snapshot.completedCount,
            defaultSnoozeMinutes = settings.defaultSnoozeMinutes,
            dismissBehavior = settings.dismissBehavior,
            isEditorOpen = editor.first,
            editingTask = editor.second,
            snoozeTargetTask = snooze,
            proofPrompt = proof
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TasksUiState()
    )

    init {
        refreshOverdue()
    }

    fun onAction(action: TasksAction) {
        when (action) {
            is TasksAction.OnSearchQueryChange -> searchQuery.value = action.query
            is TasksAction.OnFilterSelected -> selectedFilter.value = action.filter
            is TasksAction.OnCheckTask -> handleCompleteTask(action.task.id, ProofInput.None)
            is TasksAction.OnSubmitProof -> {
                proofPrompt.value = null
                handleCompleteTask(action.taskId, ProofInput.Provided(action.reference))
            }
            TasksAction.OnDismissProofDialog -> proofPrompt.value = null
            TasksAction.OnOpenCreateEditor -> editorState.value = true to null
            is TasksAction.OnOpenEditEditor -> editorState.value = true to action.task
            TasksAction.OnCloseEditor -> editorState.value = false to null
            is TasksAction.OnSaveTask -> handleSaveTask(action)
            is TasksAction.OnOpenSnoozePicker -> snoozeTarget.value = action.task
            TasksAction.OnCloseSnoozePicker -> snoozeTarget.value = null
            is TasksAction.OnConfirmSnooze -> handleSnoozeTask(action.taskId, action.minutes)
            is TasksAction.OnDismissTask -> handleDismissTask(action.task.id)
            is TasksAction.OnOpenTaskFromNotification -> handleOpenFromNotification(action.taskId, action.triggerProof)
            TasksAction.OnRefreshOverdue -> refreshOverdue()
        }
    }

    private fun handleOpenFromNotification(taskId: String, triggerProof: Boolean) {
        viewModelScope.launch {
            val task = taskRepository.getTaskById(taskId)
            if (task == null) {
                _effects.send(TasksEffect.ShowMessage(UiText.res(R.string.tasks_msg_not_found)))
                return@launch
            }
            if (searchQuery.value.isNotBlank()) {
                searchQuery.value = ""
            }
            if ((!task.status.isActionable && selectedFilter.value == TaskListFilter.ACTIVE) ||
                (task.status.isActionable && selectedFilter.value == TaskListFilter.COMPLETED)
            ) {
                selectedFilter.value = TaskListFilter.ALL
            }
            _effects.send(TasksEffect.ScrollToTask(task.id))
            if (triggerProof) {
                handleCompleteTask(task.id, ProofInput.None)
            } else {
                editorState.value = true to task
            }
        }
    }

    private fun refreshOverdue() {
        viewModelScope.launch {
            processOverdueTasksUseCase()
        }
    }

    private fun handleSaveTask(action: TasksAction.OnSaveTask) {
        viewModelScope.launch {
            val editing = editorState.value.second
            val result = if (editing == null) {
                createTaskUseCase(
                    title = action.title,
                    notes = action.notes,
                    priority = action.priority,
                    recurrence = action.recurrence,
                    dueAtEpochMillis = action.dueAtEpochMillis,
                    remindAtEpochMillis = action.remindAtEpochMillis
                )
            } else {
                updateTaskUseCase(
                    taskId = editing.id,
                    title = action.title,
                    notes = action.notes,
                    priority = action.priority,
                    dueAtEpochMillis = action.dueAtEpochMillis,
                    remindAtEpochMillis = action.remindAtEpochMillis,
                    recurrence = action.recurrence
                )
            }

            result.onSuccess {
                editorState.value = false to null
                _effects.send(
                    TasksEffect.ShowMessage(
                        UiText.res(
                            if (editing == null) R.string.tasks_msg_created else R.string.tasks_msg_updated
                        )
                    )
                )
            }.onFailure { error ->
                _effects.send(
                    TasksEffect.ShowMessage(error.toUiText(fallbackRes = R.string.tasks_msg_save_failed))
                )
            }
        }
    }

    private fun handleCompleteTask(taskId: String, proofInput: ProofInput) {
        viewModelScope.launch {
            when (val outcome = completeTaskUseCase(taskId, proofInput)) {
                is CompleteTaskOutcome.Completed -> {
                    val message = if (outcome.task.recurrence.isRecurring) {
                        UiText.res(R.string.tasks_msg_completed_recurring, outcome.pointsAwarded)
                    } else {
                        UiText.res(R.string.tasks_msg_completed_points, outcome.pointsAwarded)
                    }
                    _effects.send(TasksEffect.ShowMessage(message))
                }
                is CompleteTaskOutcome.ProofRequired -> {
                    proofPrompt.value = ProofPromptState(
                        taskId = taskId,
                        proofType = outcome.proofType,
                        message = outcome.promptMessage
                    )
                }
                is CompleteTaskOutcome.Rejected -> {
                    _effects.send(TasksEffect.ShowMessage(UiText.Dynamic(outcome.reason)))
                }
                CompleteTaskOutcome.NotActionable -> Unit
            }
        }
    }

    private fun handleSnoozeTask(taskId: String, minutes: Int) {
        viewModelScope.launch {
            snoozeTarget.value = null
            snoozeTaskUseCase(taskId = taskId, snoozeMinutes = minutes)
                .onSuccess {
                    _effects.send(TasksEffect.ShowMessage(UiText.res(R.string.tasks_msg_snoozed, minutes)))
                }
                .onFailure { error ->
                    _effects.send(
                        TasksEffect.ShowMessage(error.toUiText(fallbackRes = R.string.tasks_msg_snooze_failed))
                    )
                }
        }
    }

    private fun handleDismissTask(taskId: String) {
        viewModelScope.launch {
            when (val outcome = dismissTaskUseCase(taskId)) {
                is DismissTaskOutcome.MarkedIgnored -> {
                    _effects.send(
                        TasksEffect.ShowMessage(
                            UiText.res(R.string.tasks_msg_marked_ignored, outcome.scoreDelta)
                        )
                    )
                }
                DismissTaskOutcome.Deleted -> {
                    _effects.send(TasksEffect.ShowMessage(UiText.res(R.string.tasks_msg_deleted)))
                }
                DismissTaskOutcome.NotFound -> Unit
            }
        }
    }

    private fun Throwable.toUiText(fallbackRes: Int): UiText = when (this) {
        is EmptyTaskTitleException -> UiText.res(R.string.error_empty_title)
        is TaskNotFoundException -> UiText.res(R.string.tasks_msg_not_found)
        is TaskNotActionableException -> UiText.res(R.string.error_task_not_actionable)
        else -> message?.let { UiText.Dynamic(it) } ?: UiText.res(fallbackRes)
    }
}
