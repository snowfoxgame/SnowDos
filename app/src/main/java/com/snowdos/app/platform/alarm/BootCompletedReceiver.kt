package com.snowdos.app.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.snowdos.app.core.common.DispatcherProvider
import com.snowdos.app.core.common.TimeProvider
import com.snowdos.app.domain.repository.SettingsRepository
import com.snowdos.app.domain.repository.TaskRepository
import com.snowdos.app.domain.strategy.ReminderScheduler
import com.snowdos.app.domain.strategy.WidgetUpdateTrigger
import com.snowdos.app.domain.strategy.scheduleTaskCycle
import com.snowdos.app.domain.usecase.ProcessOverdueTasksUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var processOverdueTasksUseCase: ProcessOverdueTasksUseCase

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var timeProvider: TimeProvider

    @Inject
    lateinit var widgetUpdateTrigger: WidgetUpdateTrigger

    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + dispatcherProvider.io).launch {
            try {
                processOverdueTasksUseCase()
                reminderScheduler.ensurePeriodicOverdueMonitoring()

                val settings = settingsRepository.observeSettings().first()
                val now = timeProvider.nowEpochMillis()
                val actionableTasks = (taskRepository.getActionableTasksWithReminder() +
                    taskRepository.getActionableTasksWithDeadline())
                    .distinctBy { it.id }
                actionableTasks.forEach { task ->
                    reminderScheduler.scheduleTaskCycle(task, settings, now)
                }
                widgetUpdateTrigger.updateAll()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
