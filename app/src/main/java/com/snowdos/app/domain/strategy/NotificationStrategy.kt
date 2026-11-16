package com.snowdos.app.domain.strategy

import com.snowdos.app.domain.model.AppLanguage
import com.snowdos.app.domain.model.Task

enum class EscalationLevel {
    NORMAL,
    PERSISTENT,
    CRITICAL
}

enum class NotificationActionType {
    COMPLETE,
    SNOOZE,
    DISMISS
}

data class NotificationConfig(
    val title: String,
    val body: String,
    val ongoing: Boolean,
    val autoCancel: Boolean,
    val escalationLevel: EscalationLevel,
    val actions: List<NotificationActionType>,
    val fullScreenAlarm: Boolean = false
)

/**
 * Extension point for reminder escalation & anti-dismissal notification behavior.
 */
interface NotificationStrategy {
    fun buildConfig(task: Task, defaultSnoozeMinutes: Int): NotificationConfig

    fun buildConfig(
        task: Task,
        defaultSnoozeMinutes: Int,
        intensiveMode: Boolean
    ): NotificationConfig = buildConfig(task, defaultSnoozeMinutes)

    fun buildConfig(
        task: Task,
        defaultSnoozeMinutes: Int,
        intensiveMode: Boolean,
        language: AppLanguage
    ): NotificationConfig = buildConfig(task, defaultSnoozeMinutes, intensiveMode)
}
