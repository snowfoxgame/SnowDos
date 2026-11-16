package com.snowdos.app.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Task(
    val id: String,
    val title: String,
    val notes: String = "",
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val status: TaskStatus = TaskStatus.OPEN,
    val recurrence: RecurrenceRule = RecurrenceRule.NONE,
    val dueAtEpochMillis: Long? = null,
    val remindAtEpochMillis: Long? = null,
    val snoozeCount: Int = 0,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val remoteId: String? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY
)
