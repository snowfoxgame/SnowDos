package com.snowdos.app.feature.tasks.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snowdos.app.R
import com.snowdos.app.core.designsystem.SnowDosThemeTokens
import com.snowdos.app.core.designsystem.components.SnowFrostedDialog
import com.snowdos.app.core.designsystem.components.SnowGlassButton
import com.snowdos.app.core.designsystem.components.SnowGlassChip
import com.snowdos.app.domain.model.Task

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SnoozePickerDialog(
    visible: Boolean,
    task: Task,
    defaultSnoozeMinutes: Int,
    onSelectMinutes: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = SnowDosThemeTokens.current
    val options = listOf(5, 10, 15, 30, 60, 180)
        .plus(defaultSnoozeMinutes)
        .distinct()
        .sorted()

    SnowFrostedDialog(
        visible = visible,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(tokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
        ) {
            Text(
                text = stringResource(R.string.snooze_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = stringResource(R.string.snooze_dialog_message, task.title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { minutes ->
                    val label = when {
                        minutes >= 60 && minutes % 60 == 0 -> stringResource(R.string.label_hours_short, minutes / 60)
                        else -> stringResource(R.string.label_minutes_short, minutes)
                    }
                    val isDefault = minutes == defaultSnoozeMinutes
                    SnowGlassChip(
                        label = if (isDefault) stringResource(R.string.snooze_option_default_tag, label) else label,
                        selected = isDefault,
                        onClick = { onSelectMinutes(minutes) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm, Alignment.End)
            ) {
                SnowGlassButton(
                    onClick = onDismiss
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SnowGlassButton(
                    highlighted = true,
                    onClick = { onSelectMinutes(defaultSnoozeMinutes) }
                ) {
                    Text(
                        text = stringResource(R.string.snooze_action_default, defaultSnoozeMinutes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
