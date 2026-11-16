package com.snowdos.app.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.snowdos.app.core.common.DispatcherProvider
import com.snowdos.app.domain.repository.SettingsRepository
import com.snowdos.app.domain.repository.TaskRepository
import com.snowdos.app.platform.notification.ReminderNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var notificationManager: ReminderNotificationManager

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderNotificationManager.EXTRA_TASK_ID) ?: return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + dispatcherProvider.io).launch {
            try {
                val settings = settingsRepository.observeSettings().first()
                if (!settings.notificationsEnabled) return@launch

                val task = taskRepository.getTaskById(taskId) ?: return@launch
                if (task.status.isActionable) {
                    notificationManager.showReminder(
                        task = task,
                        defaultSnoozeMinutes = settings.defaultSnoozeMinutes,
                        intensiveMode = settings.antiIgnorePowerEnabled,
                        language = settings.appLanguage
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER_REMINDER = "com.snowdos.app.ACTION_TRIGGER_REMINDER"
    }
}
