package com.snowdos.app.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.snowdos.app.MainActivity
import com.snowdos.app.core.common.DispatcherProvider
import com.snowdos.app.domain.usecase.CompleteTaskOutcome
import com.snowdos.app.domain.usecase.CompleteTaskUseCase
import com.snowdos.app.domain.usecase.DismissTaskUseCase
import com.snowdos.app.domain.usecase.SnoozeTaskUseCase
import com.snowdos.app.platform.notification.ReminderNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var completeTaskUseCase: CompleteTaskUseCase

    @Inject
    lateinit var snoozeTaskUseCase: SnoozeTaskUseCase

    @Inject
    lateinit var dismissTaskUseCase: DismissTaskUseCase

    @Inject
    lateinit var notificationManager: ReminderNotificationManager

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderNotificationManager.EXTRA_TASK_ID) ?: return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + dispatcherProvider.io).launch {
            try {
                when (intent.action) {
                    ACTION_COMPLETE -> {
                        when (completeTaskUseCase(taskId)) {
                            is CompleteTaskOutcome.Completed,
                            CompleteTaskOutcome.NotActionable -> notificationManager.cancelNotification(taskId)
                            is CompleteTaskOutcome.ProofRequired,
                            is CompleteTaskOutcome.Rejected -> {
                                val openIntent = Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    putExtra(ReminderNotificationManager.EXTRA_TASK_ID, taskId)
                                    putExtra(ReminderNotificationManager.EXTRA_TRIGGER_PROOF, true)
                                }
                                context.startActivity(openIntent)
                            }
                        }
                    }
                    ACTION_SNOOZE -> {
                        snoozeTaskUseCase(taskId)
                        notificationManager.cancelNotification(taskId)
                    }
                    ACTION_DISMISS -> {
                        dismissTaskUseCase(taskId)
                        notificationManager.cancelNotification(taskId)
                    }
                    ACTION_NOTIFICATION_SWIPED -> {
                        snoozeTaskUseCase(taskId, metadataReason = "notification_swiped")
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.snowdos.app.ACTION_NOTIFICATION_COMPLETE"
        const val ACTION_SNOOZE = "com.snowdos.app.ACTION_NOTIFICATION_SNOOZE"
        const val ACTION_DISMISS = "com.snowdos.app.ACTION_NOTIFICATION_DISMISS"
        const val ACTION_NOTIFICATION_SWIPED = "com.snowdos.app.ACTION_NOTIFICATION_SWIPED"
    }
}
