package com.snowdos.app.feature.tasks.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snowdos.app.R
import com.snowdos.app.core.common.formatCompactDateTime
import com.snowdos.app.core.common.labelRes
import com.snowdos.app.core.designsystem.SnowDosThemeTokens
import com.snowdos.app.core.designsystem.components.SnowFrostedDialog
import com.snowdos.app.core.designsystem.components.SnowFrostedSheet
import com.snowdos.app.core.designsystem.components.SnowGlassButton
import com.snowdos.app.core.designsystem.components.SnowGlassChip
import com.snowdos.app.core.designsystem.components.SnowSegmentOption
import com.snowdos.app.core.designsystem.components.SnowSegmentedPill
import com.snowdos.app.core.designsystem.components.glassReadySurface
import com.snowdos.app.core.designsystem.components.snowPressFeedback
import com.snowdos.app.core.designsystem.components.snowSurfaceBorderColor
import com.snowdos.app.domain.model.RecurrenceRule
import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.model.TaskPriority
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
    visible: Boolean,
    editingTask: Task?,
    onSave: (
        title: String,
        notes: String,
        priority: TaskPriority,
        recurrence: RecurrenceRule,
        dueAtEpochMillis: Long?,
        remindAtEpochMillis: Long?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = SnowDosThemeTokens.current

    var title by remember { mutableStateOf(editingTask?.title.orEmpty()) }
    var notes by remember { mutableStateOf(editingTask?.notes.orEmpty()) }
    var priority by remember { mutableStateOf(editingTask?.priority ?: TaskPriority.MEDIUM) }
    var recurrence by remember { mutableStateOf(editingTask?.recurrence ?: RecurrenceRule.NONE) }
    var dueAtMillis by remember { mutableStateOf(editingTask?.dueAtEpochMillis) }
    var remindAtDue by remember {
        mutableStateOf(
            if (editingTask == null) true else editingTask.remindAtEpochMillis != null
        )
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var datePickerSession by remember { mutableIntStateOf(0) }
    var timePickerSession by remember { mutableIntStateOf(0) }
    var pendingPickedDate by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(visible, editingTask) {
        if (visible) {
            title = editingTask?.title.orEmpty()
            notes = editingTask?.notes.orEmpty()
            priority = editingTask?.priority ?: TaskPriority.MEDIUM
            recurrence = editingTask?.recurrence ?: RecurrenceRule.NONE
            dueAtMillis = editingTask?.dueAtEpochMillis
            remindAtDue = if (editingTask == null) true else editingTask.remindAtEpochMillis != null
            showDatePicker = false
            showTimePicker = false
            pendingPickedDate = null
        }
    }

    SnowFrostedSheet(
        visible = visible,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = tokens.spacing.md)
                .padding(top = 12.dp, bottom = tokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                )
            }

            Text(
                text = stringResource(
                    if (editingTask == null) R.string.task_editor_new_title else R.string.task_editor_edit_title
                ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.task_editor_title_label)) },
                placeholder = { Text(stringResource(R.string.task_editor_title_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.task_editor_notes_label)) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                Text(
                    text = stringResource(R.string.task_editor_priority_label),
                    style = MaterialTheme.typography.labelLarge
                )
                val priorities = listOf(
                    SnowSegmentOption(TaskPriority.LOW, stringResource(R.string.priority_low)),
                    SnowSegmentOption(TaskPriority.MEDIUM, stringResource(R.string.priority_medium)),
                    SnowSegmentOption(TaskPriority.HIGH, stringResource(R.string.priority_high))
                )
                SnowSegmentedPill(
                    options = priorities,
                    selected = priority,
                    onSelect = { priority = it }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                Text(
                    text = stringResource(R.string.task_editor_due_section),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = dueAtMillis?.let {
                        stringResource(R.string.task_card_due_full, formatCompactDateTime(it))
                    } ?: stringResource(R.string.task_editor_due_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val chipShape = RoundedCornerShape(tokens.radius.pill)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SnowGlassChip(
                        label = stringResource(R.string.task_editor_in_one_hour),
                        selected = false,
                        shape = chipShape,
                        onClick = { dueAtMillis = System.currentTimeMillis() + 60 * 60_000L }
                    )
                    SnowGlassChip(
                        label = "18:00",
                        selected = false,
                        shape = chipShape,
                        onClick = {
                            val zone = ZoneId.systemDefault()
                            val today18 = ZonedDateTime.of(LocalDate.now(zone), LocalTime.of(18, 0), zone)
                            val target = if (today18.toInstant().toEpochMilli() > System.currentTimeMillis()) {
                                today18
                            } else {
                                today18.plusDays(1)
                            }
                            dueAtMillis = target.toInstant().toEpochMilli()
                        }
                    )
                    SnowGlassChip(
                        label = stringResource(R.string.task_editor_pick_datetime),
                        selected = false,
                        shape = chipShape,
                        onClick = {
                            datePickerSession++
                            showDatePicker = true
                        }
                    )
                    if (dueAtMillis != null) {
                        SnowGlassChip(
                            label = stringResource(R.string.action_remove),
                            selected = false,
                            shape = chipShape,
                            onClick = { dueAtMillis = null }
                        )
                    }
                }

                if (dueAtMillis != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.task_editor_send_reminder),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = tokens.spacing.sm)
                        )
                        Switch(
                            checked = remindAtDue,
                            onCheckedChange = { remindAtDue = it }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                Text(
                    text = stringResource(R.string.task_editor_recurrence_label),
                    style = MaterialTheme.typography.labelLarge
                )
                val chipShape = RoundedCornerShape(tokens.radius.pill)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RecurrenceRule.entries.forEach { rule ->
                        SnowGlassChip(
                            label = stringResource(rule.labelRes),
                            selected = recurrence == rule,
                            shape = chipShape,
                            onClick = { recurrence = rule }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
            ) {
                val buttonShape = RoundedCornerShape(tokens.radius.pill)
                SnowGlassButton(
                    onClick = onDismiss,
                    shape = buttonShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SnowGlassButton(
                    onClick = {
                        val effectiveRemind = if (dueAtMillis != null && remindAtDue) dueAtMillis else null
                        onSave(title, notes, priority, recurrence, dueAtMillis, effectiveRemind)
                    },
                    enabled = title.isNotBlank(),
                    highlighted = title.isNotBlank(),
                    shape = buttonShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.action_save),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    val datePickerState = key(editingTask?.id, datePickerSession) {
        rememberDatePickerState(
            initialSelectedDateMillis = dueAtMillis ?: System.currentTimeMillis()
        )
    }
    SnowFrostedDialog(
        visible = showDatePicker,
        onDismissRequest = { showDatePicker = false },
        maxWidth = 420.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = tokens.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(containerColor = Color.Transparent)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm, Alignment.End)
            ) {
                SnowGlassButton(
                    onClick = { showDatePicker = false }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                SnowGlassButton(
                    highlighted = true,
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                        pendingPickedDate = Instant.ofEpochMilli(selectedMillis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                        showDatePicker = false
                        timePickerSession++
                        showTimePicker = true
                    }
                ) {
                    Text(stringResource(R.string.action_next))
                }
            }
        }
    }

    val zone = ZoneId.systemDefault()
    val initialTime = dueAtMillis?.let {
        Instant.ofEpochMilli(it).atZone(zone).toLocalTime()
    } ?: LocalTime.now(zone).plusHours(1)
    val timePickerState = key(editingTask?.id, timePickerSession) {
        rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = true
        )
    }
    SnowFrostedDialog(
        visible = showTimePicker,
        onDismissRequest = { showTimePicker = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(tokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.task_editor_pick_time_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            TimePicker(
                state = timePickerState,
                colors = TimePickerDefaults.colors(
                    containerColor = Color.Transparent,
                    clockDialColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.42f),
                    periodSelectorBorderColor = snowSurfaceBorderColor()
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm, Alignment.End)
            ) {
                SnowGlassButton(
                    onClick = { showTimePicker = false }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                SnowGlassButton(
                    highlighted = true,
                    onClick = {
                        val date = pendingPickedDate ?: LocalDate.now(zone)
                        val time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        dueAtMillis = ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()
                        showTimePicker = false
                    }
                ) {
                    Text(stringResource(R.string.action_apply))
                }
            }
        }
    }
}
