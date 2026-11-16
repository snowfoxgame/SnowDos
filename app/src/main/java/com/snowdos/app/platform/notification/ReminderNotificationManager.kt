package com.snowdos.app.platform.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.snowdos.app.MainActivity
import com.snowdos.app.R
import com.snowdos.app.core.common.localizedResourcesFor
import com.snowdos.app.domain.model.AppLanguage
import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.strategy.EscalationLevel
import com.snowdos.app.domain.strategy.NotificationActionType
import com.snowdos.app.domain.strategy.NotificationStrategy
import com.snowdos.app.platform.alarm.NotificationActionReceiver
import com.snowdos.app.platform.alarm.ReminderAlarmActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationStrategy: NotificationStrategy
) {

    fun ensureChannel(language: AppLanguage = AppLanguage.fromSystemLocale()) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val resources = context.localizedResourcesFor(language)
        val standardChannel = NotificationChannel(
            CHANNEL_ID,
            resources.getString(R.string.notif_channel_standard_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = resources.getString(R.string.notif_channel_standard_desc)
        }
        manager.createNotificationChannel(standardChannel)

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val intensiveChannel = NotificationChannel(
            INTENSIVE_CHANNEL_ID,
            resources.getString(R.string.notif_channel_intensive_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = resources.getString(R.string.notif_channel_intensive_desc)
            setSound(alarmUri, audioAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 450L, 200L, 450L, 200L, 700L)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(intensiveChannel)
    }

    fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.canUseFullScreenIntent()
    }

    fun showReminder(
        task: Task,
        defaultSnoozeMinutes: Int,
        intensiveMode: Boolean = false,
        language: AppLanguage = AppLanguage.fromSystemLocale()
    ) {
        if (!hasNotificationPermission()) return
        ensureChannel(language)
        val resources = context.localizedResourcesFor(language)

        val config = notificationStrategy.buildConfig(
            task = task,
            defaultSnoozeMinutes = defaultSnoozeMinutes,
            intensiveMode = intensiveMode,
            language = language
        )
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TASK_ID, task.id)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            task.id.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priority = when (config.escalationLevel) {
            EscalationLevel.NORMAL -> NotificationCompat.PRIORITY_HIGH
            EscalationLevel.PERSISTENT -> NotificationCompat.PRIORITY_MAX
            EscalationLevel.CRITICAL -> NotificationCompat.PRIORITY_MAX
        }

        val channelId = if (config.fullScreenAlarm) INTENSIVE_CHANNEL_ID else CHANNEL_ID
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_snowdos)
            .setContentTitle(config.title)
            .setContentText(config.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(config.body))
            .setPriority(priority)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(config.ongoing)
            .setAutoCancel(config.autoCancel)

        if (config.fullScreenAlarm) {
            val alarmIntent = Intent(context, ReminderAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_TASK_ID, task.id)
                putExtra(EXTRA_DEFAULT_SNOOZE_MINUTES, defaultSnoozeMinutes)
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                task.id.hashCode() * 31 + 10,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(fullScreenPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setVibrate(longArrayOf(0L, 450L, 200L, 450L, 200L, 700L))
        } else {
            builder
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(contentPendingIntent)
                .setDeleteIntent(
                    buildActionPendingIntent(
                        taskId = task.id,
                        action = NotificationActionReceiver.ACTION_NOTIFICATION_SWIPED,
                        requestCodeOffset = 4
                    )
                )
        }

        config.actions.forEach { actionType ->
            when (actionType) {
                NotificationActionType.COMPLETE -> {
                    builder.addAction(
                        0,
                        resources.getString(R.string.notif_action_complete),
                        buildActionPendingIntent(
                            taskId = task.id,
                            action = NotificationActionReceiver.ACTION_COMPLETE,
                            requestCodeOffset = 1
                        )
                    )
                }
                NotificationActionType.SNOOZE -> {
                    builder.addAction(
                        0,
                        resources.getString(R.string.notif_action_snooze, defaultSnoozeMinutes),
                        buildActionPendingIntent(
                            taskId = task.id,
                            action = NotificationActionReceiver.ACTION_SNOOZE,
                            requestCodeOffset = 2
                        )
                    )
                }
                NotificationActionType.DISMISS -> {
                    builder.addAction(
                        0,
                        resources.getString(R.string.notif_action_dismiss),
                        buildActionPendingIntent(
                            taskId = task.id,
                            action = NotificationActionReceiver.ACTION_DISMISS,
                            requestCodeOffset = 3
                        )
                    )
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(notificationIdFor(task.id), builder.build())
    }

    fun cancelNotification(taskId: String) {
        NotificationManagerCompat.from(context).cancel(notificationIdFor(taskId))
    }

    private fun buildActionPendingIntent(
        taskId: String,
        action: String,
        requestCodeOffset: Int
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode() * 31 + requestCodeOffset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun notificationIdFor(taskId: String): Int = taskId.hashCode()

    companion object {
        const val CHANNEL_ID = "snowdos_reminders"
        const val INTENSIVE_CHANNEL_ID = "snowdos_reminders_intensive"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TRIGGER_PROOF = "extra_trigger_proof"
        const val EXTRA_DEFAULT_SNOOZE_MINUTES = "extra_default_snooze_minutes"
    }
}
