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
import java.time.ZoneId
import javax.inject.Inject

/**
 * Escalating reminder notification strategy for "Anti-Ignore-Power" (intensive mode).
 * When intensive mode is disabled, it delegates directly to [StandardNotificationStrategy] for
 * normal, polite reminders.
 */
class EscalatingNotificationStrategy internal constructor(
    private val standardStrategy: StandardNotificationStrategy,
    private val textResolver: AppTextResolver,
    private val zoneId: ZoneId
) : NotificationStrategy {

    @Inject
    constructor(
        standardStrategy: StandardNotificationStrategy,
        textResolver: AppTextResolver
    ) : this(
        standardStrategy = standardStrategy,
        textResolver = textResolver,
        zoneId = ZoneId.systemDefault()
    )

    constructor(standardStrategy: StandardNotificationStrategy) : this(
        standardStrategy = standardStrategy,
        textResolver = DefaultJvmTextResolver,
        zoneId = ZoneId.systemDefault()
    )

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
        intensiveMode: Boolean
    ): NotificationConfig = buildConfig(
        task = task,
        defaultSnoozeMinutes = defaultSnoozeMinutes,
        intensiveMode = intensiveMode,
        language = AppLanguage.GERMAN
    )

    override fun buildConfig(
        task: Task,
        defaultSnoozeMinutes: Int,
        intensiveMode: Boolean,
        language: AppLanguage
    ): NotificationConfig {
        if (!intensiveMode) {
            return standardStrategy.buildConfig(
                task = task,
                defaultSnoozeMinutes = defaultSnoozeMinutes,
                intensiveMode = false,
                language = language
            )
        }

        val escalation = if (task.snoozeCount >= 1) {
            EscalationLevel.CRITICAL
        } else {
            EscalationLevel.PERSISTENT
        }

        val title = if (task.snoozeCount > 0) {
            textResolver.getString(
                R.string.notif_escalating_title_snoozed,
                language,
                task.snoozeCount,
                task.title
            )
        } else {
            textResolver.getString(
                R.string.notif_escalating_title_due,
                language,
                task.title
            )
        }

        val dueAtText = formatDueAtText(task, language, textResolver, zoneId)
        val body = buildString {
            if (dueAtText != null) {
                append(dueAtText)
                append(" · ")
            }
            append(textResolver.getString(R.string.notif_escalating_body_header, language))
            if (task.snoozeCount > 0) {
                append(
                    textResolver.getString(
                        R.string.notif_escalating_body_snoozed,
                        language,
                        task.snoozeCount
                    )
                )
            }
            if (task.notes.isNotBlank()) {
                append("\n")
                append(task.notes)
            } else {
                append(textResolver.getString(R.string.notif_escalating_body_footer, language))
            }
        }

        return NotificationConfig(
            title = title,
            body = body,
            ongoing = true,
            autoCancel = false,
            escalationLevel = escalation,
            actions = listOf(
                NotificationActionType.COMPLETE,
                NotificationActionType.SNOOZE
            ),
            fullScreenAlarm = true
        )
    }
}
