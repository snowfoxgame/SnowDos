package com.snowdos.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.snowdos.app.domain.strategy.ReminderScheduler
import com.snowdos.app.platform.icon.AppIconManager
import com.snowdos.app.platform.notification.ReminderNotificationManager
import com.snowdos.app.platform.sync.ThemeSyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SnowDosApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    lateinit var notificationManager: ReminderNotificationManager

    @Inject
    lateinit var appIconManager: AppIconManager

    @Inject
    lateinit var themeSyncManager: ThemeSyncManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notificationManager.ensureChannel()
        reminderScheduler.ensurePeriodicOverdueMonitoring()
        appIconManager.startObservingSettings()
        themeSyncManager.initialize()
    }
}
