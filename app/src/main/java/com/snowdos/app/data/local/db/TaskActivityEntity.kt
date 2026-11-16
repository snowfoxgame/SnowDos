package com.snowdos.app.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.snowdos.app.domain.model.ActivityEventType

@Entity(
    tableName = "task_activity_events",
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["occurredAtEpochMillis"])
    ]
)
data class TaskActivityEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val taskTitleSnapshot: String,
    val eventType: ActivityEventType,
    val scoreDelta: Int,
    val occurredAtEpochMillis: Long,
    val metadataJson: String?
)
