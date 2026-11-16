package com.snowdos.app.data.strategy

import com.snowdos.app.R
import com.snowdos.app.core.common.AppTextResolver
import com.snowdos.app.core.common.DefaultJvmTextResolver
import com.snowdos.app.domain.model.AppLanguage
import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.strategy.EscalationLevel
import com.snowdos.app.domain.strategy.NotificationActionType
import com.snowdos.app.domain.strategy.NotificationConfig
import com.snowdos.app.domain.strategy.NotificationStrategy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val DUE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun formatDueAtText(
    task: Task,
    language: AppLanguage,
    textResolver: AppTextResolver,
    zoneId: ZoneId
): String? {
    val dueAt = task.dueAtEpochMillis ?: return null
    val timeText = Instant.ofEpochMilli(dueAt)
        .atZone(zoneId)
        .format(DUE_TIME_FORMATTER)
    return textResolver.getString(R.string.notif_due_at, language, timeText)
}

/**
 * Standard polite reminder strategy with complete and snooze actions.
 */
class StandardNotificationStrategy internal constructor(
    private val textResolver: AppTextResolver,
    private val zoneId: ZoneId
) : NotificationStrategy {

    @Inject
    constructor(textResolver: AppTextResolver) : this(
        textResolver = textResolver,
        zoneId = ZoneId.systemDefault()
    )

    constructor() : this(DefaultJvmTextResolver, ZoneId.systemDefault())

    override fun buildConfig(task: Task, defaultSnoozeMinutes: Int): NotificationConfig =
        buildConfig(
            task = task,
            defaultSnoozeMinutes = defaultSnoozeMinutes,
            intensiveMode = false,
            language = AppLanguage.GERMAN
        )

    override fun buildConfig(
        task: Task,
        defaultSnoozeMinutes: Int,
        intensiveMode: Boolean,
        language: AppLanguage
    ): NotificationConfig {
        val escalation = when {
            task.snoozeCount >= 3 -> EscalationLevel.CRITICAL
            task.snoozeCount >= 1 -> EscalationLevel.PERSISTENT
            else -> EscalationLevel.NORMAL
        }
        val dueAtText = formatDueAtText(task, language, textResolver, zoneId)
        val body = buildString {
            if (dueAtText != null) {
                append(dueAtText)
                if (task.snoozeCount > 0) {
                    append(" · ")
                    append(
                        textResolver.getString(
                            R.string.notif_standard_title_snoozed,
                            language,
                            task.snoozeCount
                        )
                    )
                }
                if (task.notes.isNotBlank()) {
                    append("\n")
                    append(task.notes)
                }
            } else if (task.snoozeCount > 0) {
                append(
                    textResolver.getString(
                        R.string.notif_standard_title_snoozed,
                        language,
                        task.snoozeCount
                    )
                )
            } else {
                append(
                    task.notes.ifBlank {
                        textResolver.getString(R.string.notif_standard_default_body, language)
                    }
                )
            }
        }
        return NotificationConfig(
            title = task.title,
            body = body,
            ongoing = escalation != EscalationLevel.NORMAL,
            autoCancel = true,
            escalationLevel = escalation,
            actions = listOf(
                NotificationActionType.COMPLETE,
                NotificationActionType.SNOOZE
            )
        )
    }
}
