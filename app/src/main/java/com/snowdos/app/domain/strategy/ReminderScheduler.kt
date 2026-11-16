package com.snowdos.app.domain.strategy

import com.snowdos.app.domain.model.AppSettings
import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.model.TaskStatus

/**
 * Schedules and cancels local reminders and overdue deadline checks.
 */
interface ReminderScheduler {
    fun scheduleReminder(task: Task)
    fun scheduleReminder(task: Task, triggerAtEpochMillis: Long) = scheduleReminder(task)
    fun cancelReminder(taskId: String)
    fun scheduleOverdueCheck(taskId: String, triggerAtEpochMillis: Long)
    fun ensurePeriodicOverdueMonitoring()
    fun canScheduleExactAlarms(): Boolean = true
}

/**
 * Returns the effective deadline timestamp, respecting snoozes that push beyond [Task.dueAtEpochMillis].
 */
fun resolveEffectiveDeadlineEpochMillis(task: Task): Long? {
    val dueAt = task.dueAtEpochMillis ?: return null
    val remindAt = task.remindAtEpochMillis
    return if (task.status == TaskStatus.SNOOZED && remindAt != null) {
        maxOf(dueAt, remindAt)
    } else {
        dueAt
    }
}

/**
 * Computes the alarm trigger timestamp for [task] using [settings] lead time and grace period,
 * or `null` when no reminder should be scheduled.
 *
 * When a non-snoozed task has a due timestamp, the configured [AppSettings.reminderLeadTimeMinutes]
 * is subtracted. If that target is already slightly in the past but [nowMillis] is still before
 * the overdue deadline, it returns a prompt trigger (`nowMillis + 1_000L`) so reminders are not lost.
 */
fun resolveReminderTriggerAtEpochMillis(
    task: Task,
    settings: AppSettings,
    nowMillis: Long
): Long? {
    if (!task.status.isActionable || !settings.notificationsEnabled) return null
    val baseRemindAt = task.remindAtEpochMillis ?: return null
    val leadTimeMillis = settings.reminderLeadTimeMinutes.coerceAtLeast(0) * 60_000L
    val targetTrigger = if (task.status != TaskStatus.SNOOZED && task.dueAtEpochMillis != null) {
        task.dueAtEpochMillis - leadTimeMillis
    } else {
        baseRemindAt
    }
    val effectiveDeadline = resolveEffectiveDeadlineEpochMillis(task)
    val graceMillis = settings.scoreRules.missedGracePeriodMinutes.coerceAtLeast(0) * 60_000L
    val overdueDeadline = effectiveDeadline?.plus(graceMillis)

    return when {
        overdueDeadline != null && nowMillis >= overdueDeadline -> null
        targetTrigger <= nowMillis -> nowMillis + 1_000L
        else -> targetTrigger
    }
}

/**
 * Schedules both reminder notification and deadline overdue check for an actionable [task]
 * according to [settings].
 */
fun ReminderScheduler.scheduleTaskCycle(
    task: Task,
    settings: AppSettings,
    nowMillis: Long = System.currentTimeMillis()
) {
    if (!task.status.isActionable) return
    resolveReminderTriggerAtEpochMillis(task, settings, nowMillis)?.let { triggerAt ->
        scheduleReminder(task, triggerAt)
    }
    val effectiveDeadline = resolveEffectiveDeadlineEpochMillis(task)
    if (effectiveDeadline != null) {
        val graceMillis = settings.scoreRules.missedGracePeriodMinutes.coerceAtLeast(0) * 60_000L
        scheduleOverdueCheck(task.id, effectiveDeadline + graceMillis)
    }
}
