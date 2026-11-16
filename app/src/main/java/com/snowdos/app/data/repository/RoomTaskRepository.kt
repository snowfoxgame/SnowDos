package com.snowdos.app.data.repository

import androidx.room.withTransaction
import com.snowdos.app.data.local.db.SnowDosDatabase
import com.snowdos.app.data.local.db.TaskActivityDao
import com.snowdos.app.data.local.db.TaskDao
import com.snowdos.app.data.local.mapper.toDomain
import com.snowdos.app.data.local.mapper.toEntity
import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.model.TaskActivityEvent
import com.snowdos.app.domain.model.TaskStatus
import com.snowdos.app.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomTaskRepository @Inject constructor(
    private val database: SnowDosDatabase,
    private val taskDao: TaskDao,
    private val activityDao: TaskActivityDao
) : TaskRepository {

    private val actionableStatuses = listOf(TaskStatus.OPEN, TaskStatus.SNOOZED)

    override fun observeAllTasks(): Flow<List<Task>> =
        taskDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getTaskById(taskId: String): Task? =
        taskDao.getById(taskId)?.toDomain()

    override suspend fun getActionableTasksWithDeadline(): List<Task> =
        taskDao.getByStatusesWithDeadline(actionableStatuses).map { it.toDomain() }

    override suspend fun getActionableTasksWithReminder(): List<Task> =
        taskDao.getByStatusesWithReminder(actionableStatuses).map { it.toDomain() }

    override suspend fun insertTaskWithEvent(task: Task, event: TaskActivityEvent) {
        database.withTransaction {
            taskDao.insert(task.toEntity())
            activityDao.insert(event.toEntity())
        }
    }

    override suspend fun updateTask(task: Task) {
        taskDao.update(task.toEntity())
    }

    override suspend fun applyLifecycleTransition(updatedTask: Task, event: TaskActivityEvent) {
        database.withTransaction {
            taskDao.update(updatedTask.toEntity())
            activityDao.insert(event.toEntity())
        }
    }

    override suspend fun deleteTaskById(taskId: String) {
        taskDao.deleteById(taskId)
    }
}
