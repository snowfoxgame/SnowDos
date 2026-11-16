package com.snowdos.app.data.local.db

import androidx.room.TypeConverter
import com.snowdos.app.domain.model.ActivityEventType
import com.snowdos.app.domain.model.RecurrenceRule
import com.snowdos.app.domain.model.SyncState
import com.snowdos.app.domain.model.TaskPriority
import com.snowdos.app.domain.model.TaskStatus

class SnowDosTypeConverters {
    @TypeConverter
    fun fromTaskStatus(value: TaskStatus): String = value.name

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)

    @TypeConverter
    fun fromTaskPriority(value: TaskPriority): String = value.name

    @TypeConverter
    fun toTaskPriority(value: String): TaskPriority = TaskPriority.valueOf(value)

    @TypeConverter
    fun fromRecurrenceRule(value: RecurrenceRule): String = value.name

    @TypeConverter
    fun toRecurrenceRule(value: String): RecurrenceRule =
        runCatching { RecurrenceRule.valueOf(value) }.getOrDefault(RecurrenceRule.NONE)

    @TypeConverter
    fun fromSyncState(value: SyncState): String = value.name

    @TypeConverter
    fun toSyncState(value: String): SyncState = SyncState.valueOf(value)

    @TypeConverter
    fun fromActivityEventType(value: ActivityEventType): String = value.name

    @TypeConverter
    fun toActivityEventType(value: String): ActivityEventType = ActivityEventType.valueOf(value)
}
