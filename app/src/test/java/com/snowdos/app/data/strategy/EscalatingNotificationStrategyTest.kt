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

class EscalatingNotificationStrategyTest {

    private val strategy = EscalatingNotificationStrategy(
        standardStrategy = StandardNotificationStrategy(DefaultJvmTextResolver, ZoneOffset.UTC),
        textResolver = DefaultJvmTextResolver,
        zoneId = ZoneOffset.UTC
    )

    @Test
    fun `when intensiveMode is false, delegates to polite standard notification`() {
        val task = Task(
            id = "t1",
            title = "Wasser trinken",
            notes = "1 Glas",
            status = TaskStatus.OPEN,
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 100L
        )

        val config = strategy.buildConfig(task, defaultSnoozeMinutes = 15, intensiveMode = false)
        assertEquals("Wasser trinken", config.title)
        assertFalse(config.ongoing)
        assertTrue(config.autoCancel)
        assertFalse(config.fullScreenAlarm)
        assertEquals(EscalationLevel.NORMAL, config.escalationLevel)
    }

    @Test
    fun `when intensiveMode is true, enables ongoing persistent full screen alarm and escalates on snooze`() {
        val task = Task(
            id = "t2",
            title = "Medizin nehmen",
            status = TaskStatus.OPEN,
            snoozeCount = 0,
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 100L
        )

        val firstTrigger = strategy.buildConfig(task, defaultSnoozeMinutes = 15, intensiveMode = true)
        assertTrue(firstTrigger.ongoing)
        assertFalse(firstTrigger.autoCancel)
        assertTrue(firstTrigger.fullScreenAlarm)
        assertEquals(EscalationLevel.PERSISTENT, firstTrigger.escalationLevel)
        assertEquals(
            listOf(NotificationActionType.COMPLETE, NotificationActionType.SNOOZE),
            firstTrigger.actions
        )

        val afterSnooze = strategy.buildConfig(
            task.copy(status = TaskStatus.SNOOZED, snoozeCount = 2),
            defaultSnoozeMinutes = 15,
            intensiveMode = true
        )
        assertTrue(afterSnooze.ongoing)
        assertTrue(afterSnooze.fullScreenAlarm)
        assertEquals(EscalationLevel.CRITICAL, afterSnooze.escalationLevel)
        assertTrue(afterSnooze.title.contains("2x geschoben"))
    }

    @Test
    fun `includes bilingual due time in intensive notification body`() {
        val dueAtMillis = LocalDateTime.of(2026, 11, 14, 9, 5).toInstant(ZoneOffset.UTC).toEpochMilli()
        val task = Task(
            id = "t3",
            title = "Standup",
            status = TaskStatus.OPEN,
            dueAtEpochMillis = dueAtMillis,
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 100L
        )

        val deConfig = strategy.buildConfig(task, defaultSnoozeMinutes = 10, intensiveMode = true, language = AppLanguage.GERMAN)
        assertTrue(deConfig.body.startsWith("Fällig um 09:05"))

        val enConfig = strategy.buildConfig(task, defaultSnoozeMinutes = 10, intensiveMode = true, language = AppLanguage.ENGLISH)
        assertTrue(enConfig.body.startsWith("Due at 09:05"))
    }
}
