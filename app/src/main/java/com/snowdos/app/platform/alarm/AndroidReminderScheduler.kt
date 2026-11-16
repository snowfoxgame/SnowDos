package com.snowdos.app.platform.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.snowdos.app.core.common.TimeProvider
import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.strategy.ReminderScheduler
import com.snowdos.app.platform.notification.ReminderNotificationManager
import com.snowdos.app.platform.worker.OverdueCheckWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
    private val notificationManager: ReminderNotificationManager
) : ReminderScheduler {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    override fun canScheduleExactAlarms(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    override fun scheduleReminder(task: Task) {
        val remindAt = task.remindAtEpochMillis ?: return
        scheduleReminder(task, remindAt)
    }

    override fun scheduleReminder(task: Task, triggerAtEpochMillis: Long) {
        val manager = alarmManager ?: return
        val triggerAt = maxOf(triggerAtEpochMillis, timeProvider.nowEpochMillis() + 1_000L)
        val pendingIntent = buildReminderPendingIntent(task.id)

        if (canScheduleExactAlarms()) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                return
            } catch (_: SecurityException) {
                // Fall back to inexact allow-while-idle below.
            } catch (_: RuntimeException) {
                // OEM alarm limits or transient AlarmManager errors must not crash the UI.
            }
        }
        runCatching {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    override fun cancelReminder(taskId: String) {
        runCatching {
            alarmManager?.cancel(buildReminderPendingIntent(taskId))
        }
        runCatching {
            workManager.cancelUniqueWork(overdueWorkName(taskId))
        }
        runCatching {
            notificationManager.cancelNotification(taskId)
        }
    }

    override fun scheduleOverdueCheck(taskId: String, triggerAtEpochMillis: Long) {
        runCatching {
            val delayMillis = (triggerAtEpochMillis - timeProvider.nowEpochMillis()).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<OverdueCheckWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()

            workManager.enqueueUniqueWork(
                overdueWorkName(taskId),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override fun ensurePeriodicOverdueMonitoring() {
        runCatching {
            val request = PeriodicWorkRequestBuilder<OverdueCheckWorker>(15, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                PERIODIC_OVERDUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    private fun buildReminderPendingIntent(taskId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_TRIGGER_REMINDER
            putExtra(ReminderNotificationManager.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun overdueWorkName(taskId: String): String = "overdue_$taskId"

    companion object {
        private const val PERIODIC_OVERDUE_WORK_NAME = "snowdos_periodic_overdue"
    }
}
