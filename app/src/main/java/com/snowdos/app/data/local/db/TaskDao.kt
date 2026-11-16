package com.snowdos.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.snowdos.app.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getById(taskId: String): TaskEntity?

    @Query(
        "SELECT * FROM tasks WHERE status IN (:statuses) AND dueAtEpochMillis IS NOT NULL"
    )
    suspend fun getByStatusesWithDeadline(statuses: List<TaskStatus>): List<TaskEntity>

    @Query(
        "SELECT * FROM tasks WHERE status IN (:statuses) AND remindAtEpochMillis IS NOT NULL"
    )
    suspend fun getByStatusesWithReminder(statuses: List<TaskStatus>): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TaskEntity)

    @Update
    suspend fun update(entity: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteById(taskId: String)
}
