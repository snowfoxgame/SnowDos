package com.snowdos.app.feature.onboarding

import com.snowdos.app.domain.model.RecurrenceRule
import com.snowdos.app.domain.model.ThemeMode
import com.snowdos.app.domain.usecase.CreateTaskUseCase
import com.snowdos.app.fakes.FakeReminderScheduler
import com.snowdos.app.fakes.FakeSettingsRepository
import com.snowdos.app.fakes.FakeTaskRepository
import com.snowdos.app.fakes.FakeTimeProvider
import com.snowdos.app.fakes.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `skip onboarding marks completed without creating tasks`() = runTest {
        val taskRepo = FakeTaskRepository()
        val settingsRepo = FakeSettingsRepository()
        val scheduler = FakeReminderScheduler()
        val timeProvider = FakeTimeProvider(currentTimeMillis = 1_700_000_000_000L)
        val viewModel = OnboardingViewModel(
            createTaskUseCase = CreateTaskUseCase(taskRepo, settingsRepo, scheduler, timeProvider),
            settingsRepository = settingsRepo,
            timeProvider = timeProvider
        )

        viewModel.toggleTemplate("hydrate")
        assertTrue("hydrate" in viewModel.uiState.value.selectedTemplateIds)

        var doneCalled = false
        viewModel.skipOnboarding { doneCalled = true }
        advanceUntilIdle()

        assertTrue(doneCalled)
        assertTrue(settingsRepo.observeSettings().first().hasCompletedOnboarding)
        assertTrue(taskRepo.observeAllTasks().first().isEmpty())
    }

    @Test
    fun `complete onboarding creates selected recurring tasks and marks onboarding done`() = runTest {
        val taskRepo = FakeTaskRepository()
        val settingsRepo = FakeSettingsRepository()
        val scheduler = FakeReminderScheduler()
        val timeProvider = FakeTimeProvider(currentTimeMillis = 1_700_000_000_000L)
        val viewModel = OnboardingViewModel(
            createTaskUseCase = CreateTaskUseCase(taskRepo, settingsRepo, scheduler, timeProvider),
            settingsRepository = settingsRepo,
            timeProvider = timeProvider
        )

        assertEquals(12, OnboardingTemplateCatalog.templates.size)

        viewModel.toggleTemplate("shower")
        viewModel.toggleTemplate("brush_teeth")
        viewModel.toggleTemplate("hydrate")
        viewModel.toggleTemplate("hydrate") // toggle off
        assertEquals(setOf("shower", "brush_teeth"), viewModel.uiState.value.selectedTemplateIds)

        var doneCalled = false
        viewModel.completeOnboarding { doneCalled = true }
        advanceUntilIdle()

        assertTrue(doneCalled)
        assertTrue(settingsRepo.observeSettings().first().hasCompletedOnboarding)

        val createdTasks = taskRepo.observeAllTasks().first()
        assertEquals(2, createdTasks.size)
        val shower = createdTasks.first { it.title == "Duschrhythmus" }
        val brush = createdTasks.first { it.title == "Zähneputzen" }
        assertEquals(RecurrenceRule.EVERY_2_DAYS, shower.recurrence)
        assertEquals(RecurrenceRule.DAILY, brush.recurrence)
        assertFalse(shower.dueAtEpochMillis == null)
        assertEquals(shower.dueAtEpochMillis, shower.remindAtEpochMillis)
        assertEquals(2, scheduler.scheduledReminders.size)
    }

    @Test
    fun `select theme preset updates uiState and persists preset mode immediately`() = runTest {
        val taskRepo = FakeTaskRepository()
        val settingsRepo = FakeSettingsRepository()
        val scheduler = FakeReminderScheduler()
        val timeProvider = FakeTimeProvider(currentTimeMillis = 1_700_000_000_000L)
        val viewModel = OnboardingViewModel(
            createTaskUseCase = CreateTaskUseCase(taskRepo, settingsRepo, scheduler, timeProvider),
            settingsRepository = settingsRepo,
            timeProvider = timeProvider
        )

        viewModel.selectThemePreset("halloween")
        advanceUntilIdle()

        assertEquals("halloween", viewModel.uiState.value.selectedThemeId)
        val persisted = settingsRepo.observeSettings().first()
        assertEquals("halloween", persisted.appearance.presetId)
        assertEquals(ThemeMode.PRESET, persisted.appearance.mode)
    }
}
