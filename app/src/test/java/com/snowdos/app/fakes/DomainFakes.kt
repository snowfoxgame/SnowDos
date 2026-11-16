package com.snowdos.app.fakes

import com.snowdos.app.core.common.TimeProvider
import com.snowdos.app.domain.model.AppLanguage
import com.snowdos.app.domain.model.AppSettings
import com.snowdos.app.domain.model.DismissBehavior
import com.snowdos.app.domain.model.ScoreRulesConfig
import com.snowdos.app.domain.model.TabTransitionMode
import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.model.TaskActivityEvent
import com.snowdos.app.domain.model.ThemeMode
import com.snowdos.app.domain.repository.ActivityRepository
import com.snowdos.app.domain.repository.SettingsRepository
import com.snowdos.app.domain.repository.TaskRepository
import com.snowdos.app.domain.strategy.CompletionProofStrategy
import com.snowdos.app.domain.strategy.ProofInput
import com.snowdos.app.domain.strategy.ProofResult
import com.snowdos.app.domain.strategy.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeTimeProvider(var currentTimeMillis: Long = 1_000_000L) : TimeProvider {
    override fun nowEpochMillis(): Long = currentTimeMillis
}

class FakeTaskRepository : TaskRepository {
    private val tasksFlow = MutableStateFlow<List<Task>>(emptyList())
    val recordedEvents = mutableListOf<TaskActivityEvent>()

    override fun observeAllTasks(): Flow<List<Task>> = tasksFlow.asStateFlow()

    override suspend fun getTaskById(taskId: String): Task? =
        tasksFlow.value.firstOrNull { it.id == taskId }

    override suspend fun getActionableTasksWithDeadline(): List<Task> =
        tasksFlow.value.filter { it.status.isActionable && it.dueAtEpochMillis != null }

    override suspend fun getActionableTasksWithReminder(): List<Task> =
        tasksFlow.value.filter { it.status.isActionable && it.remindAtEpochMillis != null }

    override suspend fun insertTaskWithEvent(task: Task, event: TaskActivityEvent) {
        tasksFlow.update { it + task }
        recordedEvents.add(event)
    }

    override suspend fun updateTask(task: Task) {
        tasksFlow.update { list -> list.map { if (it.id == task.id) task else it } }
    }

    override suspend fun applyLifecycleTransition(updatedTask: Task, event: TaskActivityEvent) {
        updateTask(updatedTask)
        recordedEvents.add(event)
    }

    override suspend fun deleteTaskById(taskId: String) {
        tasksFlow.update { list -> list.filterNot { it.id == taskId } }
    }
}

class FakeActivityRepository : ActivityRepository {
    private val eventsFlow = MutableStateFlow<List<TaskActivityEvent>>(emptyList())

    override fun observeAllEvents(): Flow<List<TaskActivityEvent>> = eventsFlow.asStateFlow()

    override suspend fun insertEvent(event: TaskActivityEvent) {
        eventsFlow.update { listOf(event) + it }
    }

    override suspend fun deleteEventById(eventId: String) {
        eventsFlow.update { list -> list.filterNot { it.id == eventId } }
    }

    override suspend fun deleteAllEvents() {
        eventsFlow.value = emptyList()
    }
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val settingsFlow = MutableStateFlow(initial)

    override fun observeSettings(): Flow<AppSettings> = settingsFlow.asStateFlow()

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        settingsFlow.update { it.copy(notificationsEnabled = enabled) }
    }

    override suspend fun setAntiIgnorePowerEnabled(enabled: Boolean) {
        settingsFlow.update { it.copy(antiIgnorePowerEnabled = enabled) }
    }

    override suspend fun setReminderLeadTimeMinutes(minutes: Int) {
        settingsFlow.update { it.copy(reminderLeadTimeMinutes = minutes.coerceAtLeast(0)) }
    }

    override suspend fun setDefaultSnoozeMinutes(minutes: Int) {
        settingsFlow.update { it.copy(defaultSnoozeMinutes = minutes) }
    }

    override suspend fun setDismissBehavior(behavior: DismissBehavior) {
        settingsFlow.update { it.copy(dismissBehavior = behavior) }
    }

    override suspend fun setScoreRules(rules: ScoreRulesConfig) {
        settingsFlow.update { it.copy(scoreRules = rules) }
    }

    override suspend fun setTabTransitionMode(mode: TabTransitionMode) {
        settingsFlow.update { it.copy(tabTransitionMode = mode) }
    }

    override suspend fun setWidgetColorMode(mode: com.snowdos.app.domain.model.WidgetColorMode) {
        settingsFlow.update { it.copy(widgetColorMode = mode) }
    }

    override suspend fun setWidgetSurfaceStyle(style: com.snowdos.app.domain.model.WidgetSurfaceStyle) {
        settingsFlow.update { it.copy(widgetSurfaceStyle = style) }
    }

    override suspend fun setAccountTabVisibility(visibility: com.snowdos.app.domain.model.AccountTabVisibility) {
        settingsFlow.update { it.copy(accountTabVisibility = visibility) }
    }

    override suspend fun setAppLanguage(language: AppLanguage) {
        settingsFlow.update { it.copy(appLanguage = language) }
    }

    override suspend fun setThemeId(themeId: String) {
        settingsFlow.update {
            it.copy(
                themeId = themeId,
                appearance = it.appearance.copy(
                    mode = ThemeMode.PRESET,
                    presetId = themeId,
                    autoSeasonalThemeEnabled = false
                )
            )
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        settingsFlow.update {
            it.copy(
                appearance = it.appearance.copy(
                    mode = mode,
                    autoSeasonalThemeEnabled = if (mode == ThemeMode.CUSTOM) false else it.appearance.autoSeasonalThemeEnabled
                )
            )
        }
    }

    override suspend fun setCustomAccentHex(hex: String) {
        settingsFlow.update {
            it.copy(
                appearance = it.appearance.copy(
                    mode = ThemeMode.CUSTOM,
                    customAccentHex = hex,
                    autoSeasonalThemeEnabled = false
                )
            )
        }
    }

    override suspend fun setGamingAccentHex(hex: String) {
        settingsFlow.update {
            it.copy(
                themeId = "gaming",
                appearance = it.appearance.copy(
                    mode = ThemeMode.PRESET,
                    presetId = "gaming",
                    gamingAccentHex = hex,
                    autoSeasonalThemeEnabled = false
                )
            )
        }
    }

    override suspend fun setEightBitVariant(variant: com.snowdos.app.domain.model.EightBitVariant) {
        settingsFlow.update {
            it.copy(
                themeId = "8bit_cute",
                appearance = it.appearance.copy(
                    mode = ThemeMode.PRESET,
                    presetId = "8bit_cute",
                    eightBitVariant = variant,
                    autoSeasonalThemeEnabled = false
                )
            )
        }
    }

    override suspend fun setWeatherStyle(style: com.snowdos.app.domain.model.WeatherThemeStyle) {
        settingsFlow.update {
            it.copy(
                themeId = "weather_time",
                appearance = it.appearance.copy(
                    mode = ThemeMode.PRESET,
                    presetId = "weather_time",
                    weatherStyle = style,
                    autoSeasonalThemeEnabled = false
                )
            )
        }
    }

    override suspend fun setAutoSeasonalThemeEnabled(enabled: Boolean) {
        settingsFlow.update {
            it.copy(
                appearance = it.appearance.copy(
                    autoSeasonalThemeEnabled = enabled,
                    mode = if (enabled) ThemeMode.PRESET else it.appearance.mode
                )
            )
        }
    }

    override suspend fun saveWeatherSnapshot(snapshot: com.snowdos.app.domain.model.WeatherSnapshot) {
        settingsFlow.update { it.copy(weatherSnapshot = snapshot) }
    }

    override suspend fun setBackgroundImageUri(uri: String?) {
        settingsFlow.update { it.copy(appearance = it.appearance.copy(backgroundImageUri = uri)) }
    }

    override suspend fun setGlassOpacity(opacity: Float) {
        settingsFlow.update {
            it.copy(appearance = it.appearance.copy(glassOpacity = opacity.coerceIn(0.10f, 0.55f)))
        }
    }

    override suspend fun setDynamicAppIconEnabled(enabled: Boolean) {
        settingsFlow.update { it.copy(dynamicAppIconEnabled = enabled) }
    }

    override suspend fun setHasCompletedOnboarding(completed: Boolean) {
        settingsFlow.update { it.copy(hasCompletedOnboarding = completed) }
    }

    override suspend fun setReduceLiveBlur(enabled: Boolean) {
        settingsFlow.update { it.copy(reduceLiveBlur = enabled) }
    }

    override suspend fun setCrossAppSyncEnabled(enabled: Boolean) {
        settingsFlow.update { it.copy(crossAppSyncEnabled = enabled) }
    }

    override suspend fun setPartnerAppConnected(connected: Boolean) {
        settingsFlow.update { it.copy(partnerAppConnected = connected) }
    }

    override suspend fun recordLocalAppearanceModified(modifiedAtMillis: Long, syncId: String) {
        settingsFlow.update {
            it.copy(
                localModifiedAtMillis = modifiedAtMillis,
                lastPublishedSyncId = syncId
            )
        }
    }

    override suspend fun markLocalAppearanceSynced(revision: Long, syncId: String) {
        settingsFlow.update {
            it.copy(
                lastSyncedRevision = revision,
                lastAppliedSyncId = syncId,
                localModifiedAtMillis = revision,
                lastPublishedSyncId = syncId
            )
        }
    }

    override suspend fun updateLocalBackgroundHash(hash: String?) {
        settingsFlow.update { it.copy(localBackgroundHash = hash) }
    }

    override suspend fun applySyncedAppearance(
        appearance: com.snowdos.app.domain.model.ThemeAppearanceConfig,
        reduceLiveBlur: Boolean,
        syncRevision: Long,
        syncId: String,
        backgroundHash: String?
    ) {
        settingsFlow.update {
            it.copy(
                themeId = appearance.presetId,
                appearance = appearance,
                reduceLiveBlur = reduceLiveBlur,
                lastSyncedRevision = syncRevision,
                lastAppliedSyncId = syncId,
                localModifiedAtMillis = syncRevision,
                lastPublishedSyncId = syncId,
                localBackgroundHash = if (appearance.backgroundImageUri.isNullOrBlank()) null else backgroundHash
            )
        }
    }
}

class FakeReminderScheduler : ReminderScheduler {
    val scheduledReminders = mutableMapOf<String, Long>()
    val scheduledOverdueChecks = mutableMapOf<String, Long>()
    val canceledTaskIds = mutableListOf<String>()
    var periodicMonitoringEnsured = false

    override fun scheduleReminder(task: Task) {
        task.remindAtEpochMillis?.let { scheduledReminders[task.id] = it }
    }

    override fun scheduleReminder(task: Task, triggerAtEpochMillis: Long) {
        scheduledReminders[task.id] = triggerAtEpochMillis
    }

    override fun cancelReminder(taskId: String) {
        scheduledReminders.remove(taskId)
        scheduledOverdueChecks.remove(taskId)
        canceledTaskIds.add(taskId)
    }

    override fun scheduleOverdueCheck(taskId: String, triggerAtEpochMillis: Long) {
        scheduledOverdueChecks[taskId] = triggerAtEpochMillis
    }

    override fun ensurePeriodicOverdueMonitoring() {
        periodicMonitoringEnsured = true
    }
}

class ConfigurableProofStrategy(
    var nextResult: ProofResult = ProofResult.Verified
) : CompletionProofStrategy {
    override suspend fun verify(task: Task, input: ProofInput): ProofResult = nextResult
}
