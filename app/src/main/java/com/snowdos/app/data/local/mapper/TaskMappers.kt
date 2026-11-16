package com.snowdos.app.data.local.mapper

import com.snowdos.app.data.local.db.TaskActivityEntity
import com.snowdos.app.data.local.db.TaskEntity
import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.model.TaskActivityEvent

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    notes = notes,
    priority = priority,
    status = status,
    recurrence = recurrence,
    dueAtEpochMillis = dueAtEpochMillis,
    remindAtEpochMillis = remindAtEpochMillis,
    snoozeCount = snoozeCount,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    completedAtEpochMillis = completedAtEpochMillis,
    remoteId = remoteId,
    syncState = syncState
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    notes = notes,
    priority = priority,
    status = status,
    recurrence = recurrence,
    dueAtEpochMillis = dueAtEpochMillis,
    remindAtEpochMillis = remindAtEpochMillis,
    snoozeCount = snoozeCount,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    completedAtEpochMillis = completedAtEpochMillis,
    remoteId = remoteId,
    syncState = syncState
)

fun TaskActivityEntity.toDomain(): TaskActivityEvent = TaskActivityEvent(
    id = id,
    taskId = taskId,
    taskTitleSnapshot = taskTitleSnapshot,
    eventType = eventType,
    scoreDelta = scoreDelta,
    occurredAtEpochMillis = occurredAtEpochMillis,
    metadataJson = metadataJson
)

fun TaskActivityEvent.toEntity(): TaskActivityEntity = TaskActivityEntity(
    id = id,
    taskId = taskId,
    taskTitleSnapshot = taskTitleSnapshot,
    eventType = eventType,
    scoreDelta = scoreDelta,
    occurredAtEpochMillis = occurredAtEpochMillis,
    metadataJson = metadataJson
)
