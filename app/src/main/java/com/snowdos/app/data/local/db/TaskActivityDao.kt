package com.snowdos.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskActivityDao {
    @Query("SELECT * FROM task_activity_events ORDER BY occurredAtEpochMillis DESC")
    fun observeAllDesc(): Flow<List<TaskActivityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TaskActivityEntity)

    @Query("DELETE FROM task_activity_events WHERE id = :eventId")
    suspend fun deleteById(eventId: String)

    @Query("DELETE FROM task_activity_events")
    suspend fun deleteAll()
}
