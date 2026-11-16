package com.snowdos.app.domain.repository

import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.model.TaskActivityEvent
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeAllTasks(): Flow<List<Task>>
    suspend fun getTaskById(taskId: String): Task?
    suspend fun getActionableTasksWithDeadline(): List<Task>
    suspend fun getActionableTasksWithReminder(): List<Task>
    suspend fun insertTaskWithEvent(task: Task, event: TaskActivityEvent)
    suspend fun updateTask(task: Task)
    suspend fun applyLifecycleTransition(updatedTask: Task, event: TaskActivityEvent)
    suspend fun deleteTaskById(taskId: String)
}
