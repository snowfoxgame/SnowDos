package com.snowdos.app.domain.usecase

import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.model.TaskStatus
import com.snowdos.app.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

enum class TaskListFilter {
    ACTIVE,
    COMPLETED,
    ALL
}

data class TasksSnapshot(
    val tasks: List<Task>,
    val openCount: Int,
    val completedCount: Int
)

class ObserveTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {
    operator fun invoke(
        query: String = "",
        filter: TaskListFilter = TaskListFilter.ACTIVE
    ): Flow<List<Task>> = observeSnapshot(query, filter).map { it.tasks }

    fun observeSnapshot(
        query: String = "",
        filter: TaskListFilter = TaskListFilter.ACTIVE
    ): Flow<TasksSnapshot> {
        val normalizedQuery = query.trim().lowercase()
        return taskRepository.observeAllTasks().map { allTasks ->
            val openCount = allTasks.count { it.status.isActionable }
            val completedCount = allTasks.count { it.status == TaskStatus.COMPLETED }
            val filtered = allTasks
                .filter { task ->
                    when (filter) {
                        TaskListFilter.ACTIVE -> task.status.isActionable
                        TaskListFilter.COMPLETED -> task.status == TaskStatus.COMPLETED
                        TaskListFilter.ALL -> true
                    }
                }
                .filter { task ->
                    if (normalizedQuery.isEmpty()) {
                        true
                    } else {
                        task.title.lowercase().contains(normalizedQuery) ||
                            task.notes.lowercase().contains(normalizedQuery)
                    }
                }
                .sortedWith(
                    compareBy<Task>(
                        { !it.status.isActionable },
                        { it.dueAtEpochMillis ?: Long.MAX_VALUE },
                        { -it.priority.weight },
                        { -it.createdAtEpochMillis }
                    )
                )

            TasksSnapshot(
                tasks = filtered,
                openCount = openCount,
                completedCount = completedCount
            )
        }
    }
}
