package com.snowdos.app.domain.model

import androidx.compose.runtime.Immutable

enum class ActivityEventType {
    CREATED,
    COMPLETED,
    SNOOZED,
    IGNORED,
    MISSED;

    val isScoredEvent: Boolean
        get() = this == COMPLETED || this == MISSED || this == IGNORED
}

/**
 * Immutable ledger entry recording task lifecycle transitions and score deltas.
 */
@Immutable
data class TaskActivityEvent(
    val id: String,
    val taskId: String,
    val taskTitleSnapshot: String,
    val eventType: ActivityEventType,
    val scoreDelta: Int,
    val occurredAtEpochMillis: Long,
    val metadataJson: String? = null
)
