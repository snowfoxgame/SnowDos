package com.snowdos.app.feature.tasks.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snowdos.app.R
import com.snowdos.app.core.common.formatCompactDateTime
import com.snowdos.app.core.common.labelRes
import com.snowdos.app.core.designsystem.SnowDosThemeTokens
import com.snowdos.app.core.designsystem.components.glassReadySurface
import com.snowdos.app.core.designsystem.components.snowPressFeedback
import com.snowdos.app.domain.model.DismissBehavior
import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.model.TaskPriority
import com.snowdos.app.domain.model.TaskStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskItemCard(
    task: Task,
    dismissBehavior: DismissBehavior,
    onCheck: () -> Unit,
    onSnooze: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = SnowDosThemeTokens.current
    val isTerminal = task.status.isTerminal

    val cardShape = RoundedCornerShape(tokens.radius.card)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .glassReadySurface(
                shape = cardShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                alpha = if (isTerminal) 0.34f else tokens.surface.cardAlpha
            ),
        shape = cardShape,
        color = androidx.compose.ui.graphics.Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.sm, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val checkInteraction = remember { MutableInteractionSource() }
            IconButton(
                onClick = onCheck,
                enabled = task.status.isActionable,
                interactionSource = checkInteraction,
                modifier = Modifier.snowPressFeedback(
                    interactionSource = checkInteraction,
                    enabled = task.status.isActionable,
                    pressedScale = 0.92f
                )
            ) {
                Icon(
                    imageVector = if (task.status == TaskStatus.COMPLETED) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Outlined.CheckCircle
                    },
                    contentDescription = stringResource(R.string.task_card_complete_cd),
                    tint = when (task.status) {
                        TaskStatus.COMPLETED -> tokens.semantic.positive
                        TaskStatus.MISSED, TaskStatus.IGNORED -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = tokens.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isTerminal) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (isTerminal) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.notes.isNotBlank()) {
                    Text(
                        text = task.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PriorityChip(priority = task.priority)

                    if (task.recurrence.isRecurring) {
                        MetaBadge(
                            text = "↻ ${stringResource(task.recurrence.labelRes)}",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    val dueAt = task.dueAtEpochMillis
                    val remindAt = task.remindAtEpochMillis
                    val isSnoozedWithTime = task.status == TaskStatus.SNOOZED && remindAt != null
                    val dueMatchesReminder = dueAt != null && remindAt == dueAt

                    when {
                        dueAt != null && dueMatchesReminder && task.status.isActionable -> {
                            // Show a single combined badge when due and reminder share the same timestamp
                            if (!isSnoozedWithTime) {
                                MetaBadge(
                                    text = stringResource(R.string.task_card_due_short, formatCompactDateTime(dueAt)),
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Alarm,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                )
                            }
                        }
                        else -> {
                            if (dueAt != null && !(isSnoozedWithTime && dueAt == remindAt)) {
                                MetaBadge(
                                    text = stringResource(R.string.task_card_due_full, formatCompactDateTime(dueAt)),
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            if (remindAt != null && task.status.isActionable && !isSnoozedWithTime) {
                                MetaBadge(
                                    text = formatCompactDateTime(remindAt),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Alarm,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )
                            }
                        }
                    }

                    if (task.status == TaskStatus.SNOOZED || task.snoozeCount > 0) {
                        val snoozeLabel = if (isSnoozedWithTime) {
                            stringResource(
                                R.string.task_card_snoozed_until,
                                formatCompactDateTime(remindAt),
                                task.snoozeCount
                            )
                        } else {
                            stringResource(R.string.task_card_snoozed_count, task.snoozeCount)
                        }
                        MetaBadge(
                            text = snoozeLabel,
                            containerColor = tokens.semantic.warning.copy(alpha = 0.16f),
                            contentColor = tokens.semantic.warning
                        )
                    }

                    if (isTerminal) {
                        val (statusLabel, color) = when (task.status) {
                            TaskStatus.COMPLETED -> stringResource(R.string.task_status_completed) to tokens.semantic.positive
                            TaskStatus.IGNORED -> stringResource(R.string.task_status_ignored) to tokens.semantic.negative
                            TaskStatus.MISSED -> stringResource(R.string.task_status_missed) to tokens.semantic.negative
                            else -> "" to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        MetaBadge(
                            text = statusLabel,
                            containerColor = color.copy(alpha = 0.14f),
                            contentColor = color
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (task.status.isActionable) {
                    val snoozeInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = onSnooze,
                        interactionSource = snoozeInteraction,
                        modifier = Modifier.snowPressFeedback(
                            interactionSource = snoozeInteraction,
                            pressedScale = 0.92f
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Snooze,
                            contentDescription = stringResource(R.string.task_card_snooze_cd)
                        )
                    }
                    val editInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = onEdit,
                        interactionSource = editInteraction,
                        modifier = Modifier.snowPressFeedback(
                            interactionSource = editInteraction,
                            pressedScale = 0.92f
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.task_card_edit_cd)
                        )
                    }
                }
                val dismissInteraction = remember { MutableInteractionSource() }
                IconButton(
                    onClick = onDismiss,
                    interactionSource = dismissInteraction,
                    modifier = Modifier.snowPressFeedback(
                        interactionSource = dismissInteraction,
                        pressedScale = 0.92f
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(
                            if (dismissBehavior == DismissBehavior.MARK_IGNORED && task.status.isActionable) {
                                R.string.task_card_dismiss_ignored_cd
                            } else {
                                R.string.task_card_delete_cd
                            }
                        ),
                        tint = if (task.status.isActionable) {
                            tokens.semantic.negative
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PriorityChip(priority: TaskPriority) {
    val tokens = SnowDosThemeTokens.current
    val color = when (priority) {
        TaskPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
        TaskPriority.MEDIUM -> MaterialTheme.colorScheme.primary
        TaskPriority.HIGH -> tokens.semantic.negative
    }
    MetaBadge(
        text = stringResource(priority.labelRes),
        containerColor = color.copy(alpha = 0.12f),
        contentColor = color
    )
}

@Composable
private fun MetaBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val tokens = SnowDosThemeTokens.current
    Surface(
        shape = RoundedCornerShape(tokens.radius.chip),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            leadingIcon?.invoke()
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
