package com.snowdos.app.domain

import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.model.TaskActivityEvent
import com.snowdos.app.domain.model.ActivityEventType
import com.snowdos.app.domain.model.TaskPriority
import com.snowdos.app.domain.model.TaskStatus
import com.snowdos.app.domain.usecase.ObserveTasksUseCase
import com.snowdos.app.domain.usecase.TaskListFilter
import com.snowdos.app.fakes.FakeTaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveTasksUseCaseTest {

    @Test
    fun `filters by query and active status and sorts by deadline then priority`() = runTest {
        val repo = FakeTaskRepository()
        val tasks = listOf(
            sampleTask("1", "Milch kaufen", TaskPriority.LOW, TaskStatus.OPEN, due = 5000L),
            sampleTask("2", "Milch-Bericht fertigstellen", TaskPriority.HIGH, TaskStatus.SNOOZED, due = 2000L),
            sampleTask("3", "Alt erledigt", TaskPriority.HIGH, TaskStatus.COMPLETED, due = 1000L),
            sampleTask("4", "Sport", TaskPriority.HIGH, TaskStatus.OPEN, due = null)
        )
        tasks.forEach { task ->
            repo.insertTaskWithEvent(
                task,
                TaskActivityEvent("ev-${task.id}", task.id, task.title, ActivityEventType.CREATED, 0, 100L)
            )
        }

        val useCase = ObserveTasksUseCase(repo)
        val filtered = useCase(query = "milch", filter = TaskListFilter.ACTIVE).first()
        assertEquals(listOf("2", "1"), filtered.map { it.id })

        val completedOnly = useCase(query = "", filter = TaskListFilter.COMPLETED).first()
        assertEquals(listOf("3"), completedOnly.map { it.id })
    }

    private fun sampleTask(
        id: String,
        title: String,
        priority: TaskPriority,
        status: TaskStatus,
        due: Long?
    ) = Task(
        id = id,
        title = title,
        priority = priority,
        status = status,
        dueAtEpochMillis = due,
        createdAtEpochMillis = 100L,
        updatedAtEpochMillis = 100L
    )
}
