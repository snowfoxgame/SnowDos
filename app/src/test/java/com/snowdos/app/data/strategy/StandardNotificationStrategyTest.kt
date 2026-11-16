package com.snowdos.app.data.strategy

import com.snowdos.app.core.common.DefaultJvmTextResolver
import com.snowdos.app.domain.model.AppLanguage
import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.model.TaskStatus
import com.snowdos.app.domain.strategy.EscalationLevel
import com.snowdos.app.domain.strategy.NotificationActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class StandardNotificationStrategyTest {

    private val strategy = StandardNotificationStrategy(DefaultJvmTextResolver, ZoneOffset.UTC)

    @Test
    fun `builds standard notification config with complete and snooze actions`() {
        val task = Task(
            id = "t1",
            title = "Projekt-Status senden",
            notes = "An Teamleiter",
            status = TaskStatus.OPEN,
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 100L
        )

        val config = strategy.buildConfig(task, defaultSnoozeMinutes = 15)
        assertEquals("Projekt-Status senden", config.title)
        assertEquals("An Teamleiter", config.body)
        assertFalse(config.ongoing)
        assertEquals(EscalationLevel.NORMAL, config.escalationLevel)
        assertEquals(
            listOf(NotificationActionType.COMPLETE, NotificationActionType.SNOOZE),
            config.actions
        )
    }

    @Test
    fun `includes snooze count in body when task was snoozed`() {
        val task = Task(
            id = "t2",
            title = "Rechnung zahlen",
            status = TaskStatus.SNOOZED,
            snoozeCount = 2,
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 100L
        )

        val config = strategy.buildConfig(task, defaultSnoozeMinutes = 10)
        assertTrue(config.body.contains("2x verschoben"))
    }

    @Test
    fun `shows formatted bilingual due time in notification body when dueAt is set`() {
        val dueAtMillis = LocalDateTime.of(2026, 11, 14, 16, 15).toInstant(ZoneOffset.UTC).toEpochMilli()
        val task = Task(
            id = "t3",
            title = "Zahnarzttermin",
            notes = "Versichertenkarte mitnehmen",
            status = TaskStatus.OPEN,
            dueAtEpochMillis = dueAtMillis,
            remindAtEpochMillis = dueAtMillis,
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 100L
        )

        val deConfig = strategy.buildConfig(
            task = task,
            defaultSnoozeMinutes = 15,
            intensiveMode = false,
            language = AppLanguage.GERMAN
        )
        assertEquals("Zahnarzttermin", deConfig.title)
        assertEquals("Fällig um 16:15\nVersichertenkarte mitnehmen", deConfig.body)

        val enConfig = strategy.buildConfig(
            task = task,
            defaultSnoozeMinutes = 15,
            intensiveMode = false,
            language = AppLanguage.ENGLISH
        )
        assertEquals("Due at 16:15\nVersichertenkarte mitnehmen", enConfig.body)
    }
}
