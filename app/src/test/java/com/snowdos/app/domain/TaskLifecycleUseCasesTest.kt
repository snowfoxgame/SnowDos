package com.snowdos.app.domain

import com.snowdos.app.domain.model.ActivityEventType
import com.snowdos.app.domain.model.AppSettings
import com.snowdos.app.domain.model.DismissBehavior
import com.snowdos.app.domain.model.RecurrenceRule
import com.snowdos.app.domain.model.ScoreRulesConfig
import com.snowdos.app.domain.model.TaskPriority
import com.snowdos.app.domain.model.TaskStatus
import com.snowdos.app.domain.strategy.ProofResult
import com.snowdos.app.domain.strategy.ProofType
import com.snowdos.app.domain.usecase.CompleteTaskOutcome
import com.snowdos.app.domain.usecase.CompleteTaskUseCase
import com.snowdos.app.domain.usecase.CreateTaskUseCase
import com.snowdos.app.domain.usecase.DismissTaskOutcome
import com.snowdos.app.domain.usecase.DismissTaskUseCase
import com.snowdos.app.domain.usecase.ProcessOverdueTasksUseCase
import com.snowdos.app.domain.usecase.RescheduleAllRemindersUseCase
import com.snowdos.app.domain.usecase.SnoozeTaskUseCase
import com.snowdos.app.fakes.ConfigurableProofStrategy
import com.snowdos.app.fakes.FakeReminderScheduler
import com.snowdos.app.fakes.FakeSettingsRepository
import com.snowdos.app.fakes.FakeTaskRepository
import com.snowdos.app.fakes.FakeTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskLifecycleUseCasesTest {

    private lateinit var taskRepository: FakeTaskRepository
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var reminderScheduler: FakeReminderScheduler
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var proofStrategy: ConfigurableProofStrategy

    private lateinit var createTask: CreateTaskUseCase
    private lateinit var completeTask: CompleteTaskUseCase
    private lateinit var snoozeTask: SnoozeTaskUseCase
    private lateinit var dismissTask: DismissTaskUseCase
    private lateinit var processOverdueTasks: ProcessOverdueTasksUseCase
    private lateinit var rescheduleAllReminders: RescheduleAllRemindersUseCase

    @Before
    fun setUp() {
        taskRepository = FakeTaskRepository()
        settingsRepository = FakeSettingsRepository()
        reminderScheduler = FakeReminderScheduler()
        timeProvider = FakeTimeProvider(currentTimeMillis = 1_000_000L)
        proofStrategy = ConfigurableProofStrategy()

        createTask = CreateTaskUseCase(taskRepository, settingsRepository, reminderScheduler, timeProvider)
        completeTask = CompleteTaskUseCase(
            taskRepository,
            settingsRepository,
            proofStrategy,
            reminderScheduler,
            timeProvider
        )
        snoozeTask = SnoozeTaskUseCase(taskRepository, settingsRepository, reminderScheduler, timeProvider)
        dismissTask = DismissTaskUseCase(taskRepository, settingsRepository, reminderScheduler, timeProvider)
        processOverdueTasks = ProcessOverdueTasksUseCase(
            taskRepository,
            settingsRepository,
            reminderScheduler,
            timeProvider
        )
        rescheduleAllReminders = RescheduleAllRemindersUseCase(
            taskRepository,
            settingsRepository,
            reminderScheduler,
            timeProvider
        )
    }

    @Test
    fun `create task sets OPEN, records CREATED event and schedules reminder with default lead time and grace period`() = runTest {
        val result = createTask(
            title = "Steuererklärung vorbereiten",
            priority = TaskPriority.HIGH,
            dueAtEpochMillis = 2_000_000L,
            remindAtEpochMillis = 2_000_000L
        )
        assertTrue(result.isSuccess)
        val task = result.getOrThrow()
        assertEquals(TaskStatus.OPEN, task.status)
        assertEquals(1, taskRepository.recordedEvents.size)
        assertEquals(ActivityEventType.CREATED, taskRepository.recordedEvents.first().eventType)
        assertEquals(0, taskRepository.recordedEvents.first().scoreDelta)
        val defaults = AppSettings()
        assertEquals(10, defaults.reminderLeadTimeMinutes)
        assertEquals(15, defaults.scoreRules.missedGracePeriodMinutes)
        assertEquals(
            2_000_000L - defaults.reminderLeadTimeMinutes * 60_000L,
            reminderScheduler.scheduledReminders[task.id]
        )
        assertEquals(
            2_000_000L + defaults.scoreRules.missedGracePeriodMinutes * 60_000L,
            reminderScheduler.scheduledOverdueChecks[task.id]
        )
    }

    @Test
    fun `create task triggers prompt reminder when lead time already elapsed but still within grace period`() = runTest {
        // now = 1_000_000L, due = 1_300_000L (5 min from now), lead time = 10 min (target = 700_000L <= now)
        // deadline + 15 min grace = 2_200_000L (> now), so it must fire promptly at now + 1_000L.
        val task = createTask(
            title = "Paket annehmen",
            dueAtEpochMillis = 1_300_000L,
            remindAtEpochMillis = 1_300_000L
        ).getOrThrow()
        assertEquals(1_001_000L, reminderScheduler.scheduledReminders[task.id])

        // Already past due + 15 min grace: should not schedule a stale reminder.
        timeProvider.currentTimeMillis = 3_000_000L
        val overdueTask = createTask(
            title = "Verpasster Termin",
            dueAtEpochMillis = 1_300_000L,
            remindAtEpochMillis = 1_300_000L
        ).getOrThrow()
        assertNull(reminderScheduler.scheduledReminders[overdueTask.id])
    }

    @Test
    fun `complete task records priority-weighted COMPLETED event and cancels reminder`() = runTest {
        val low = createTask(title = "Wasser trinken", priority = TaskPriority.LOW).getOrThrow()
        val medium = createTask(
            title = "Einkaufen",
            priority = TaskPriority.MEDIUM,
            remindAtEpochMillis = 1_200_000L
        ).getOrThrow()
        val high = createTask(title = "Steuer abgeben", priority = TaskPriority.HIGH).getOrThrow()
        timeProvider.currentTimeMillis = 1_100_000L

        val lowOutcome = completeTask(low.id)
        assertEquals(CompleteTaskOutcome.Completed(taskRepository.getTaskById(low.id)!!, 1), lowOutcome)
        assertEquals(1, taskRepository.recordedEvents.last().scoreDelta)

        val mediumOutcome = completeTask(medium.id)
        assertTrue(mediumOutcome is CompleteTaskOutcome.Completed)
        val updatedMedium = taskRepository.getTaskById(medium.id)!!
        assertEquals(TaskStatus.COMPLETED, updatedMedium.status)
        assertEquals(1_100_000L, updatedMedium.completedAtEpochMillis)
        assertEquals(ActivityEventType.COMPLETED, taskRepository.recordedEvents.last().eventType)
        assertEquals(2, taskRepository.recordedEvents.last().scoreDelta)
        assertTrue(reminderScheduler.canceledTaskIds.contains(medium.id))

        val highOutcome = completeTask(high.id)
        assertEquals(CompleteTaskOutcome.Completed(taskRepository.getTaskById(high.id)!!, 3), highOutcome)
        assertEquals(3, taskRepository.recordedEvents.last().scoreDelta)
    }

    @Test
    fun `complete task respects proof strategy interaction requirement`() = runTest {
        val task = createTask(title = "Foto vom Schreibtisch").getOrThrow()
        proofStrategy.nextResult = ProofResult.RequiresInteraction(
            proofType = ProofType.PHOTO,
            promptMessage = "Bitte Foto aufnehmen"
        )

        val outcome = completeTask(task.id)
        assertTrue(outcome is CompleteTaskOutcome.ProofRequired)
        assertEquals(TaskStatus.OPEN, taskRepository.getTaskById(task.id)?.status)
    }

    @Test
    fun `snooze increments count, sets SNOOZED and extends overdue deadline when needed`() = runTest {
        val task = createTask(
            title = "E-Mails beantworten",
            dueAtEpochMillis = 1_300_000L,
            remindAtEpochMillis = 1_100_000L
        ).getOrThrow()

        timeProvider.currentTimeMillis = 1_200_000L
        val snoozed = snoozeTask(task.id, snoozeMinutes = 15).getOrThrow()

        val expectedRemindAt = 1_200_000L + 15 * 60_000L
        assertEquals(TaskStatus.SNOOZED, snoozed.status)
        assertEquals(1, snoozed.snoozeCount)
        assertEquals(expectedRemindAt, snoozed.remindAtEpochMillis)
        assertEquals(expectedRemindAt, snoozed.dueAtEpochMillis)
        assertEquals(ActivityEventType.SNOOZED, taskRepository.recordedEvents.last().eventType)
        assertEquals(0, taskRepository.recordedEvents.last().scoreDelta)
    }

    @Test
    fun `dismiss marks IGNORED with priority-weighted penalty when configured as MARK_IGNORED`() = runTest {
        val low = createTask(title = "Notiz", priority = TaskPriority.LOW).getOrThrow()
        val medium = createTask(title = "Wohnzimmer aufräumen", priority = TaskPriority.MEDIUM).getOrThrow()
        val high = createTask(title = "Wichtiger Anruf", priority = TaskPriority.HIGH).getOrThrow()

        assertEquals(DismissTaskOutcome.MarkedIgnored(-1), dismissTask(low.id))
        assertEquals(-1, taskRepository.recordedEvents.last().scoreDelta)

        val mediumOutcome = dismissTask(medium.id)
        assertEquals(DismissTaskOutcome.MarkedIgnored(-2), mediumOutcome)
        assertEquals(TaskStatus.IGNORED, taskRepository.getTaskById(medium.id)?.status)
        assertEquals(ActivityEventType.IGNORED, taskRepository.recordedEvents.last().eventType)
        assertEquals(-2, taskRepository.recordedEvents.last().scoreDelta)

        assertEquals(DismissTaskOutcome.MarkedIgnored(-3), dismissTask(high.id))
        assertEquals(-3, taskRepository.recordedEvents.last().scoreDelta)
    }

    @Test
    fun `dismiss deletes permanently when configured as DELETE_PERMANENTLY`() = runTest {
        settingsRepository.setDismissBehavior(DismissBehavior.DELETE_PERMANENTLY)
        val task = createTask(title = "Notiz löschen").getOrThrow()

        val outcome = dismissTask(task.id)
        assertEquals(DismissTaskOutcome.Deleted, outcome)
        assertNull(taskRepository.getTaskById(task.id))
    }

    @Test
    fun `overdue check marks expired tasks MISSED with priority-weighted penalty respecting grace period`() = runTest {
        settingsRepository.setScoreRules(ScoreRulesConfig(missedGracePeriodMinutes = 5))
        val lowTask = createTask(
            title = "Blumen gießen",
            priority = TaskPriority.LOW,
            dueAtEpochMillis = 1_500_000L
        ).getOrThrow()
        val highTask = createTask(
            title = "Arzt anrufen",
            priority = TaskPriority.HIGH,
            dueAtEpochMillis = 1_500_000L
        ).getOrThrow()

        // Within grace period (due + 4 min): still OPEN
        timeProvider.currentTimeMillis = 1_500_000L + 4 * 60_000L
        assertEquals(0, processOverdueTasks())
        assertEquals(TaskStatus.OPEN, taskRepository.getTaskById(lowTask.id)?.status)
        assertEquals(TaskStatus.OPEN, taskRepository.getTaskById(highTask.id)?.status)

        // Past grace period (due + 5 min): becomes MISSED (-1 for LOW, -3 for HIGH)
        timeProvider.currentTimeMillis = 1_500_000L + 5 * 60_000L
        assertEquals(2, processOverdueTasks())
        val updatedHigh = taskRepository.getTaskById(highTask.id)
        assertNotNull(updatedHigh)
        assertEquals(TaskStatus.MISSED, updatedHigh?.status)
        val missedEvents = taskRepository.recordedEvents.filter { it.eventType == ActivityEventType.MISSED }
        assertEquals(-1, missedEvents.first { it.taskId == lowTask.id }.scoreDelta)
        assertEquals(-3, missedEvents.first { it.taskId == highTask.id }.scoreDelta)
    }

    @Test
    fun `completing recurring task awards points resets to OPEN and schedules next reminder`() = runTest {
        val initialDue = 1_500_000L
        val recurring = createTask(
            title = "Trinken-Erinnerung",
            priority = TaskPriority.HIGH,
            recurrence = RecurrenceRule.EVERY_FEW_HOURS,
            dueAtEpochMillis = initialDue,
            remindAtEpochMillis = initialDue
        ).getOrThrow()

        // Complete task after being snoozed once
        timeProvider.currentTimeMillis = 1_200_000L
        snoozeTask(recurring.id, snoozeMinutes = 10)
        timeProvider.currentTimeMillis = 1_400_000L

        val outcome = completeTask(recurring.id)
        assertTrue(outcome is CompleteTaskOutcome.Completed)
        val completedOutcome = outcome as CompleteTaskOutcome.Completed
        assertEquals(3, completedOutcome.pointsAwarded)

        val updated = taskRepository.getTaskById(recurring.id)!!
        assertEquals(TaskStatus.OPEN, updated.status)
        assertEquals(0, updated.snoozeCount)
        assertEquals(RecurrenceRule.EVERY_FEW_HOURS, updated.recurrence)
        assertTrue((updated.dueAtEpochMillis ?: 0L) > timeProvider.currentTimeMillis)
        assertEquals(updated.dueAtEpochMillis, updated.remindAtEpochMillis)
        assertEquals(
            updated.dueAtEpochMillis!! - AppSettings().reminderLeadTimeMinutes * 60_000L,
            reminderScheduler.scheduledReminders[recurring.id]
        )
        assertEquals(
            updated.dueAtEpochMillis!! + AppSettings().scoreRules.missedGracePeriodMinutes * 60_000L,
            reminderScheduler.scheduledOverdueChecks[recurring.id]
        )
        assertEquals(ActivityEventType.COMPLETED, taskRepository.recordedEvents.last().eventType)
        assertEquals(3, taskRepository.recordedEvents.last().scoreDelta)
    }

    @Test
    fun `overdue recurring task records MISSED penalty, stays OPEN, resets snoozeCount and schedules next cycle`() = runTest {
        settingsRepository.setScoreRules(ScoreRulesConfig(missedGracePeriodMinutes = 5))
        val initialDue = 1_500_000L
        val recurring = createTask(
            title = "Abendroutine",
            priority = TaskPriority.MEDIUM,
            recurrence = RecurrenceRule.DAILY,
            dueAtEpochMillis = initialDue,
            remindAtEpochMillis = initialDue
        ).getOrThrow()

        // Snooze once before missing
        timeProvider.currentTimeMillis = 1_200_000L
        snoozeTask(recurring.id, snoozeMinutes = 10, metadataReason = "notification_swiped")
        val swipeEvent = taskRepository.recordedEvents.last()
        assertEquals(ActivityEventType.SNOOZED, swipeEvent.eventType)
        assertTrue(swipeEvent.metadataJson?.contains("\"reason\":\"notification_swiped\"") == true)

        // Verify base dueAtEpochMillis did NOT drift on recurring task
        val snoozed = taskRepository.getTaskById(recurring.id)!!
        assertEquals(initialDue, snoozed.dueAtEpochMillis)
        assertEquals(1_800_000L, snoozed.remindAtEpochMillis)

        // Past max(dueAt, remindAt) + 5 min grace => 1_800_000 + 300_000 = 2_100_000
        timeProvider.currentTimeMillis = 2_200_000L
        val processedCount = processOverdueTasks()
        assertEquals(1, processedCount)

        val updated = taskRepository.getTaskById(recurring.id)!!
        assertEquals(TaskStatus.OPEN, updated.status)
        assertEquals(0, updated.snoozeCount)
        assertTrue((updated.dueAtEpochMillis ?: 0L) > timeProvider.currentTimeMillis)
        assertEquals(updated.dueAtEpochMillis, updated.remindAtEpochMillis)
        assertEquals(
            updated.dueAtEpochMillis!! - AppSettings().reminderLeadTimeMinutes * 60_000L,
            reminderScheduler.scheduledReminders[recurring.id]
        )

        val missedEvent = taskRepository.recordedEvents.last()
        assertEquals(ActivityEventType.MISSED, missedEvent.eventType)
        assertEquals(-2, missedEvent.scoreDelta)
    }

    @Test
    fun `rescheduleAllReminders honors notification enabled setting and lead time`() = runTest {
        val task = createTask(
            title = "Termin prüfen",
            dueAtEpochMillis = 2_000_000L,
            remindAtEpochMillis = 2_000_000L
        ).getOrThrow()
        val defaults = AppSettings()
        assertEquals(
            2_000_000L - defaults.reminderLeadTimeMinutes * 60_000L,
            reminderScheduler.scheduledReminders[task.id]
        )

        settingsRepository.setNotificationsEnabled(false)
        rescheduleAllReminders()
        assertNull(reminderScheduler.scheduledReminders[task.id])
        assertNotNull(reminderScheduler.scheduledOverdueChecks[task.id])

        settingsRepository.setNotificationsEnabled(true)
        rescheduleAllReminders()
        assertEquals(
            2_000_000L - defaults.reminderLeadTimeMinutes * 60_000L,
            reminderScheduler.scheduledReminders[task.id]
        )
        assertEquals(
            2_000_000L + defaults.scoreRules.missedGracePeriodMinutes * 60_000L,
            reminderScheduler.scheduledOverdueChecks[task.id]
        )

        settingsRepository.setReminderLeadTimeMinutes(5)
        rescheduleAllReminders()
        assertEquals(2_000_000L - 5 * 60_000L, reminderScheduler.scheduledReminders[task.id])

        settingsRepository.setScoreRules(ScoreRulesConfig(missedGracePeriodMinutes = 20))
        rescheduleAllReminders()
        assertEquals(2_000_000L + 20 * 60_000L, reminderScheduler.scheduledOverdueChecks[task.id])
    }
}
