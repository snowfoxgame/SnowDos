package com.snowdos.app.feature.tasks

import app.cash.turbine.test
import com.snowdos.app.domain.model.ActivityEventType
import com.snowdos.app.domain.model.TaskPriority
import com.snowdos.app.domain.model.TaskStatus
import com.snowdos.app.domain.usecase.CompleteTaskUseCase
import com.snowdos.app.domain.usecase.CreateTaskUseCase
import com.snowdos.app.domain.usecase.DismissTaskUseCase
import com.snowdos.app.domain.usecase.ObserveTasksUseCase
import com.snowdos.app.domain.usecase.ProcessOverdueTasksUseCase
import com.snowdos.app.domain.usecase.SnoozeTaskUseCase
import com.snowdos.app.domain.usecase.UpdateTaskUseCase
import com.snowdos.app.fakes.ConfigurableProofStrategy
import com.snowdos.app.fakes.FakeReminderScheduler
import com.snowdos.app.fakes.FakeSettingsRepository
import com.snowdos.app.fakes.FakeTaskRepository
import com.snowdos.app.fakes.FakeTimeProvider
import com.snowdos.app.fakes.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TasksViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var taskRepository: FakeTaskRepository
    private lateinit var viewModel: TasksViewModel

    @Before
    fun setUp() {
        taskRepository = FakeTaskRepository()
        val settingsRepo = FakeSettingsRepository()
        val scheduler = FakeReminderScheduler()
        val timeProvider = FakeTimeProvider(1_000_000L)
        val proofStrategy = ConfigurableProofStrategy()

        viewModel = TasksViewModel(
            observeTasksUseCase = ObserveTasksUseCase(taskRepository),
            createTaskUseCase = CreateTaskUseCase(taskRepository, settingsRepo, scheduler, timeProvider),
            updateTaskUseCase = UpdateTaskUseCase(taskRepository, settingsRepo, scheduler, timeProvider),
            completeTaskUseCase = CompleteTaskUseCase(
                taskRepository,
                settingsRepo,
                proofStrategy,
                scheduler,
                timeProvider
            ),
            snoozeTaskUseCase = SnoozeTaskUseCase(taskRepository, settingsRepo, scheduler, timeProvider),
            dismissTaskUseCase = DismissTaskUseCase(taskRepository, settingsRepo, scheduler, timeProvider),
            processOverdueTasksUseCase = ProcessOverdueTasksUseCase(
                taskRepository,
                settingsRepo,
                scheduler,
                timeProvider
            ),
            taskRepository = taskRepository,
            settingsRepository = settingsRepo
        )
    }

    @Test
    fun `creating, filtering and completing task updates uiState reactively`() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertTrue(initial.tasks.isEmpty())

            viewModel.onAction(
                TasksAction.OnSaveTask(
                    title = "Einkaufen",
                    notes = "Hafermilch",
                    priority = TaskPriority.MEDIUM,
                    dueAtEpochMillis = null,
                    remindAtEpochMillis = null
                )
            )

            val afterCreate = awaitItem()
            assertEquals(1, afterCreate.tasks.size)
            assertEquals("Einkaufen", afterCreate.tasks.first().title)

            viewModel.onAction(TasksAction.OnSearchQueryChange("sport"))
            val afterSearchEmpty = awaitItem()
            assertTrue(afterSearchEmpty.tasks.isEmpty())

            viewModel.onAction(TasksAction.OnSearchQueryChange("einkauf"))
            val afterSearchMatch = awaitItem()
            assertEquals(1, afterSearchMatch.tasks.size)

            val task = afterSearchMatch.tasks.first()
            viewModel.onAction(TasksAction.OnCheckTask(task))
            val afterComplete = awaitItem()
            assertTrue(afterComplete.tasks.isEmpty())
            assertEquals(TaskStatus.COMPLETED, taskRepository.getTaskById(task.id)?.status)
            assertEquals(ActivityEventType.COMPLETED, taskRepository.recordedEvents.last().eventType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opening task from notification opens editor and emits ScrollToTask`() = runTest {
        viewModel.onAction(
            TasksAction.OnSaveTask(
                title = "Müll rausbringen",
                notes = "",
                priority = TaskPriority.HIGH,
                dueAtEpochMillis = null,
                remindAtEpochMillis = null
            )
        )
        val created = taskRepository.getTaskById(taskRepository.recordedEvents.last().taskId)!!

        viewModel.effects.test {
            // Drain creation toast
            val createdToast = awaitItem()
            assertTrue(createdToast is TasksEffect.ShowMessage)

            viewModel.onAction(TasksAction.OnOpenTaskFromNotification(created.id))
            val effect = awaitItem()
            assertEquals(TasksEffect.ScrollToTask(created.id), effect)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.isEditorOpen)
            assertEquals(created.id, state.editingTask?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
