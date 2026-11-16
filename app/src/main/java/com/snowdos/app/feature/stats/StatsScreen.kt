package com.snowdos.app.feature.stats

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snowdos.app.R
import com.snowdos.app.core.common.formatCompactDateTime
import com.snowdos.app.core.common.labelRes
import com.snowdos.app.core.designsystem.SnowDosThemeTokens
import com.snowdos.app.core.designsystem.components.ScoreGauge
import com.snowdos.app.core.designsystem.components.SnowFrostedDialog
import com.snowdos.app.core.designsystem.components.SnowGlassButton
import com.snowdos.app.core.designsystem.components.glassReadySurface
import com.snowdos.app.core.designsystem.components.snowPressFeedback
import com.snowdos.app.domain.model.ActivityEventType
import com.snowdos.app.domain.model.TaskActivityEvent
import com.snowdos.app.feature.tutorial.TutorialTargetId
import com.snowdos.app.feature.tutorial.tutorialTarget

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    isTutorialHighlightActive: Boolean = false,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tokens = SnowDosThemeTokens.current
    val listState = rememberLazyListState()
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isTutorialHighlightActive) {
        if (isTutorialHighlightActive) {
            listState.scrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = tokens.spacing.md),
        contentPadding = PaddingValues(
            top = tokens.spacing.md,
            bottom = tokens.spacing.floatingNavBottomClearance
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "stats_title", contentType = "title") {
            Text(
                text = stringResource(R.string.stats_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item(key = "stats_overview", contentType = "overview") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .tutorialTarget(TutorialTargetId.STATS_OVERVIEW),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ScoreGauge(
                    scorePercent = uiState.summary.reliabilityScorePercent,
                    hasScoreData = uiState.summary.hasScoreData,
                    netPoints = uiState.summary.netPoints,
                    isActive = isActive,
                    modifier = Modifier
                        .padding(top = 28.dp, bottom = 14.dp)
                )

                val pillShape = RoundedCornerShape(tokens.radius.pill)
                Surface(
                    shape = pillShape,
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassReadySurface(
                            shape = pillShape,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            alpha = tokens.surface.cardAlpha
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = tokens.spacing.sm, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatCounterItem(
                            label = stringResource(R.string.stats_counter_done),
                            count = uiState.summary.completedCount,
                            accentColor = tokens.semantic.positive,
                            isActive = isActive,
                            modifier = Modifier.weight(1f)
                        )
                        StatCounterItem(
                            label = stringResource(R.string.stats_counter_missed),
                            count = uiState.summary.missedCount,
                            accentColor = tokens.semantic.negative,
                            isActive = isActive,
                            modifier = Modifier.weight(1f)
                        )
                        StatCounterItem(
                            label = stringResource(R.string.stats_counter_ignored),
                            count = uiState.summary.ignoredCount,
                            accentColor = tokens.semantic.negative,
                            isActive = isActive,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item(key = "stats_history_header", contentType = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = tokens.spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.stats_history_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (uiState.events.isNotEmpty()) {
                    TextButton(
                        onClick = { showResetDialog = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.stats_history_reset),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (uiState.events.isEmpty()) {
            item(key = "stats_history_empty", contentType = "empty") {
                Text(
                    text = stringResource(R.string.stats_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(
                items = uiState.events,
                key = { it.id },
                contentType = { "activity_event" }
            ) { event ->
                ActivityEventRow(
                    event = event,
                    onDelete = { viewModel.deleteEvent(event.id) },
                    modifier = Modifier.animateItem(
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

    SnowFrostedDialog(
        visible = showResetDialog,
        onDismissRequest = { showResetDialog = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
        ) {
            Text(
                text = stringResource(R.string.stats_reset_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.stats_reset_dialog_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm, Alignment.End)
            ) {
                SnowGlassButton(
                    onClick = { showResetDialog = false }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                SnowGlassButton(
                    highlighted = true,
                    onClick = {
                        showResetDialog = false
                        viewModel.clearAllEvents()
                    }
                ) {
                    Text(stringResource(R.string.stats_reset_confirm))
                }
            }
        }
    }
}

@Composable
private fun StatCounterItem(
    label: String,
    count: Int,
    accentColor: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedCount by animateIntAsState(
        targetValue = if (isActive) count else 0,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessLow
        ),
        label = "statCounter_$label"
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = animatedCount.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = accentColor,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActivityEventRow(
    event: TaskActivityEvent,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = SnowDosThemeTokens.current
    val eventLabel = stringResource(event.eventType.labelRes)
    val badgeColor = when (event.eventType) {
        ActivityEventType.COMPLETED -> tokens.semantic.positive
        ActivityEventType.MISSED -> tokens.semantic.negative
        ActivityEventType.IGNORED -> tokens.semantic.negative
        ActivityEventType.SNOOZED -> tokens.semantic.warning
        ActivityEventType.CREATED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val eventShape = RoundedCornerShape(tokens.radius.compactCard)
    Surface(
        shape = eventShape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .glassReadySurface(
                shape = eventShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                alpha = tokens.surface.cardAlpha
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.md, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = tokens.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = event.taskTitleSnapshot,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$eventLabel • ${formatCompactDateTime(event.occurredAtEpochMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (event.scoreDelta != 0) {
                    val deltaText = if (event.scoreDelta > 0) "+${event.scoreDelta}" else event.scoreDelta.toString()
                    Surface(
                        shape = RoundedCornerShape(tokens.radius.chip),
                        color = badgeColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = deltaText,
                            style = MaterialTheme.typography.labelLarge,
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                val deleteInteraction = remember { MutableInteractionSource() }
                IconButton(
                    onClick = onDelete,
                    interactionSource = deleteInteraction,
                    modifier = Modifier
                        .size(34.dp)
                        .snowPressFeedback(
                            interactionSource = deleteInteraction,
                            pressedScale = 0.90f
                        )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.stats_event_delete_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
