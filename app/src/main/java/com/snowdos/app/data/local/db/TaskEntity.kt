package com.snowdos.app.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.snowdos.app.domain.model.RecurrenceRule
import com.snowdos.app.domain.model.SyncState
import com.snowdos.app.domain.model.TaskPriority
import com.snowdos.app.domain.model.TaskStatus

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["status"]),
        Index(value = ["dueAtEpochMillis"]),
        Index(value = ["remindAtEpochMillis"]),
        Index(value = ["createdAtEpochMillis"])
    ]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    val priority: TaskPriority,
    val status: TaskStatus,
    @ColumnInfo(defaultValue = "'NONE'")
    val recurrence: RecurrenceRule = RecurrenceRule.NONE,
    val dueAtEpochMillis: Long?,
    val remindAtEpochMillis: Long?,
    val snoozeCount: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
    val remoteId: String?,
    val syncState: SyncState
)
