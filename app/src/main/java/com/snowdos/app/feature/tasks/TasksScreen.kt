package com.snowdos.app.feature.tasks

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snowdos.app.R
import com.snowdos.app.core.common.resolve
import com.snowdos.app.core.designsystem.SnowDosThemeTokens
import com.snowdos.app.core.designsystem.components.SnowFrostedDialog
import com.snowdos.app.core.designsystem.components.SnowGlassButton
import com.snowdos.app.core.designsystem.components.SnowGlassSnackbarHost
import com.snowdos.app.core.designsystem.components.SnowSegmentOption
import com.snowdos.app.core.designsystem.components.SnowSegmentedPill
import com.snowdos.app.core.designsystem.components.glassReadySurface
import com.snowdos.app.core.designsystem.components.snowPressFeedback
import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.usecase.TaskListFilter
import com.snowdos.app.feature.tasks.components.SnoozePickerDialog
import com.snowdos.app.feature.tasks.components.TaskEditorSheet
import com.snowdos.app.feature.tasks.components.TaskItemCard
import com.snowdos.app.feature.tutorial.TutorialTargetId
import com.snowdos.app.feature.tutorial.tutorialTarget

@Composable
fun TasksScreen(
    modifier: Modifier = Modifier,
    isTutorialTaskHighlightActive: Boolean = false,
    viewModel: TasksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var pendingScrollTaskId by remember { mutableStateOf<String?>(null) }
    val tokens = SnowDosThemeTokens.current
    val currentContext by rememberUpdatedState(LocalContext.current)

    LaunchedEffect(isTutorialTaskHighlightActive) {
        if (isTutorialTaskHighlightActive) {
            listState.scrollToItem(0)
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.onAction(TasksAction.OnRefreshOverdue)
        onPauseOrDispose { }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TasksEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message.resolve(currentContext))
                is TasksEffect.ScrollToTask -> pendingScrollTaskId = effect.taskId
            }
        }
    }

    LaunchedEffect(pendingScrollTaskId, uiState.tasks) {
        val targetId = pendingScrollTaskId ?: return@LaunchedEffect
        val index = uiState.tasks.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            pendingScrollTaskId = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = tokens.spacing.md)
        ) {
            Column(
                modifier = Modifier.padding(top = tokens.spacing.md, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.tasks_title),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = stringResource(R.string.tasks_summary_counts, uiState.openCount, uiState.completedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .tutorialTarget(TutorialTargetId.SEARCH_AND_FILTER)
            ) {
                TextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onAction(TasksAction.OnSearchQueryChange(it)) },
                    placeholder = { Text(stringResource(R.string.tasks_search_placeholder)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.tasks_search_cd)
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            val clearInteraction = remember { MutableInteractionSource() }
                            IconButton(
                                onClick = { viewModel.onAction(TasksAction.OnSearchQueryChange("")) },
                                interactionSource = clearInteraction,
                                modifier = Modifier.snowPressFeedback(clearInteraction, pressedScale = 0.92f)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.tasks_clear_search_cd)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(tokens.radius.pill),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassReadySurface(
                            shape = RoundedCornerShape(tokens.radius.pill),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            alpha = tokens.surface.interactiveAlpha
                        )
                )

                val filterOptions = listOf(
                    SnowSegmentOption(TaskListFilter.ACTIVE, stringResource(R.string.tasks_filter_active)),
                    SnowSegmentOption(TaskListFilter.COMPLETED, stringResource(R.string.tasks_filter_completed)),
                    SnowSegmentOption(TaskListFilter.ALL, stringResource(R.string.tasks_filter_all))
                )
                SnowSegmentedPill(
                    options = filterOptions,
                    selected = uiState.selectedFilter,
                    onSelect = { viewModel.onAction(TasksAction.OnFilterSelected(it)) },
                    modifier = Modifier.padding(vertical = tokens.spacing.sm)
                )
            }

            if (uiState.tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = tokens.spacing.floatingNavBottomClearance),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.md)
                            .tutorialTarget(TutorialTargetId.TASK_ACTIONS)
                    ) {
                        Text(
                            text = stringResource(
                                if (uiState.searchQuery.isNotBlank()) R.string.tasks_empty_filtered
                                else R.string.tasks_empty_all
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = tokens.spacing.xs,
                        bottom = tokens.spacing.floatingNavBottomClearance + 76.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(
                        items = uiState.tasks,
                        key = { _, task -> task.id },
                        contentType = { _, _ -> "task_card" }
                    ) { index, task ->
                        TaskItemCard(
                            task = task,
                            dismissBehavior = uiState.dismissBehavior,
                            onCheck = { viewModel.onAction(TasksAction.OnCheckTask(task)) },
                            onSnooze = { viewModel.onAction(TasksAction.OnOpenSnoozePicker(task)) },
                            onEdit = { viewModel.onAction(TasksAction.OnOpenEditEditor(task)) },
                            onDismiss = { viewModel.onAction(TasksAction.OnDismissTask(task)) },
                            modifier = Modifier
                                .let { base ->
                                    if (index == 0) {
                                        base.tutorialTarget(TutorialTargetId.TASK_ACTIONS)
                                    } else {
                                        base
                                    }
                                }
                                .animateItem(
                                    fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    placementSpec = spring(
                                        dampingRatio = 0.82f,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    fadeOutSpec = spring(stiffness = Spring.StiffnessMedium)
                                )
                        )
                    }
                }
            }
        }

        SnowGlassSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    horizontal = tokens.spacing.md,
                    vertical = tokens.spacing.floatingNavBottomClearance
                )
        )
    }

    TaskEditorSheet(
        visible = uiState.isEditorOpen,
        editingTask = uiState.editingTask,
        onSave = { title, notes, priority, recurrence, dueAt, remindAt ->
            viewModel.onAction(
                TasksAction.OnSaveTask(
                    title = title,
                    notes = notes,
                    priority = priority,
                    recurrence = recurrence,
                    dueAtEpochMillis = dueAt,
                    remindAtEpochMillis = remindAt
                )
            )
        },
        onDismiss = { viewModel.onAction(TasksAction.OnCloseEditor) }
    )

    var lastSnoozeTarget by remember { mutableStateOf<Task?>(null) }
    if (uiState.snoozeTargetTask != null) {
        lastSnoozeTarget = uiState.snoozeTargetTask
    }
    lastSnoozeTarget?.let { target ->
        SnoozePickerDialog(
            visible = uiState.snoozeTargetTask != null,
            task = target,
            defaultSnoozeMinutes = uiState.defaultSnoozeMinutes,
            onSelectMinutes = { minutes ->
                viewModel.onAction(TasksAction.OnConfirmSnooze(target.id, minutes))
            },
            onDismiss = { viewModel.onAction(TasksAction.OnCloseSnoozePicker) }
        )
    }

    var lastProofPrompt by remember { mutableStateOf<ProofPromptState?>(null) }
    if (uiState.proofPrompt != null) {
        lastProofPrompt = uiState.proofPrompt
    }
    lastProofPrompt?.let { prompt ->
        SnowFrostedDialog(
            visible = uiState.proofPrompt != null,
            onDismissRequest = { viewModel.onAction(TasksAction.OnDismissProofDialog) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(tokens.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
            ) {
                Text(
                    text = stringResource(R.string.tasks_proof_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = prompt.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm, Alignment.End)
                ) {
                    SnowGlassButton(
                        onClick = { viewModel.onAction(TasksAction.OnDismissProofDialog) }
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    SnowGlassButton(
                        highlighted = true,
                        onClick = {
                            viewModel.onAction(
                                TasksAction.OnSubmitProof(prompt.taskId, "manual_stub")
                            )
                        }
                    ) {
                        Text(stringResource(R.string.action_confirm))
                    }
                }
            }
        }
    }
}
