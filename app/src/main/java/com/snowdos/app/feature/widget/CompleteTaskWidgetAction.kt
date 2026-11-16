package com.snowdos.app.feature.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.snowdos.app.MainActivity
import com.snowdos.app.domain.strategy.WidgetUpdateTrigger
import com.snowdos.app.domain.usecase.CompleteTaskOutcome
import com.snowdos.app.domain.usecase.CompleteTaskUseCase
import com.snowdos.app.platform.notification.ReminderNotificationManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class CompleteTaskWidgetAction : ActionCallback {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CompleteTaskWidgetEntryPoint {
        fun completeTaskUseCase(): CompleteTaskUseCase
        fun widgetUpdateTrigger(): WidgetUpdateTrigger
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[TaskIdKey] ?: return
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            CompleteTaskWidgetEntryPoint::class.java
        )
        when (entryPoint.completeTaskUseCase()(taskId)) {
            is CompleteTaskOutcome.ProofRequired -> {
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(ReminderNotificationManager.EXTRA_TASK_ID, taskId)
                    putExtra(ReminderNotificationManager.EXTRA_TRIGGER_PROOF, true)
                }
                context.startActivity(intent)
            }
            else -> entryPoint.widgetUpdateTrigger().updateAll()
        }
    }

    companion object {
        val TaskIdKey = ActionParameters.Key<String>("snowdos_widget_task_id")
    }
}
