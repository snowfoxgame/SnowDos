package com.snowdos.app.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snowdos.app.domain.model.AccountTabVisibility
import com.snowdos.app.domain.model.AppLanguage
import com.snowdos.app.domain.model.AppSettings
import com.snowdos.app.domain.model.DismissBehavior
import com.snowdos.app.domain.model.ScoreRulesConfig
import com.snowdos.app.domain.model.TabTransitionMode
import com.snowdos.app.domain.model.WidgetColorMode
import com.snowdos.app.domain.model.WidgetSurfaceStyle
import com.snowdos.app.domain.repository.SettingsRepository
import com.snowdos.app.domain.strategy.ReminderScheduler
import com.snowdos.app.domain.strategy.WidgetUpdateTrigger
import com.snowdos.app.domain.usecase.RescheduleAllRemindersUseCase
import com.snowdos.app.platform.notification.ReminderNotificationManager
import com.snowdos.app.platform.sync.ThemeSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val canScheduleExactAlarms: Boolean = true,
    val canUseFullScreenIntent: Boolean = true
)

sealed interface SettingsAction {
    data class SetNotificationsEnabled(val enabled: Boolean) : SettingsAction
    data class SetAntiIgnorePowerEnabled(val enabled: Boolean) : SettingsAction
    data class SetReminderLeadTimeMinutes(val minutes: Int) : SettingsAction
    data class SetDefaultSnoozeMinutes(val minutes: Int) : SettingsAction
    data class SetDismissBehavior(val behavior: DismissBehavior) : SettingsAction
    data class SetScoreRules(val rules: ScoreRulesConfig) : SettingsAction
    data class SetDynamicAppIconEnabled(val enabled: Boolean) : SettingsAction
    data class SetCrossAppSyncEnabled(val enabled: Boolean) : SettingsAction
    data class SetReduceLiveBlur(val enabled: Boolean) : SettingsAction
    data class SetTabTransitionMode(val mode: TabTransitionMode) : SettingsAction
    data class SetWidgetColorMode(val mode: WidgetColorMode) : SettingsAction
    data class SetWidgetSurfaceStyle(val style: WidgetSurfaceStyle) : SettingsAction
    data class SetAccountTabVisibility(val visibility: AccountTabVisibility) : SettingsAction
    data class SetAppLanguage(val language: AppLanguage) : SettingsAction
    data object RefreshAlarmPermission : SettingsAction
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val notificationManager: ReminderNotificationManager,
    private val rescheduleAllRemindersUseCase: RescheduleAllRemindersUseCase,
    private val widgetUpdateTrigger: WidgetUpdateTrigger,
    private val themeSyncManager: ThemeSyncManager
) : ViewModel() {

    private val canScheduleExactAlarms = MutableStateFlow(true)
    private val canUseFullScreenIntent = MutableStateFlow(true)

    init {
        onAction(SettingsAction.RefreshAlarmPermission)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.observeSettings(),
        canScheduleExactAlarms,
        canUseFullScreenIntent
    ) { settings, canExact, canFullScreen ->
        SettingsUiState(
            settings = settings,
            canScheduleExactAlarms = canExact,
            canUseFullScreenIntent = canFullScreen
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(
            canScheduleExactAlarms = canScheduleExactAlarms.value,
            canUseFullScreenIntent = canUseFullScreenIntent.value
        )
    )

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.RefreshAlarmPermission -> {
                viewModelScope.launch(Dispatchers.IO) {
                    canScheduleExactAlarms.value = reminderScheduler.canScheduleExactAlarms()
                    canUseFullScreenIntent.value = notificationManager.canUseFullScreenIntent()
                }
            }
            else -> viewModelScope.launch {
                when (action) {
                    is SettingsAction.SetNotificationsEnabled -> {
                        settingsRepository.setNotificationsEnabled(action.enabled)
                        rescheduleAllRemindersUseCase()
                    }
                    is SettingsAction.SetAntiIgnorePowerEnabled -> {
                        // Temporarily locked off while intensive full-screen mode is experimental.
                        settingsRepository.setAntiIgnorePowerEnabled(false)
                    }
                    is SettingsAction.SetReminderLeadTimeMinutes -> {
                        settingsRepository.setReminderLeadTimeMinutes(action.minutes)
                        rescheduleAllRemindersUseCase()
                    }
                    is SettingsAction.SetDefaultSnoozeMinutes ->
                        settingsRepository.setDefaultSnoozeMinutes(action.minutes)
                    is SettingsAction.SetDismissBehavior ->
                        settingsRepository.setDismissBehavior(action.behavior)
                    is SettingsAction.SetScoreRules -> {
                        val previousGrace = settingsRepository.observeSettings()
                            .first().scoreRules.missedGracePeriodMinutes
                        settingsRepository.setScoreRules(action.rules)
                        if (action.rules.missedGracePeriodMinutes != previousGrace) {
                            rescheduleAllRemindersUseCase()
                        }
                        widgetUpdateTrigger.updateAll()
                    }
                    is SettingsAction.SetDynamicAppIconEnabled ->
                        settingsRepository.setDynamicAppIconEnabled(action.enabled)
                    is SettingsAction.SetCrossAppSyncEnabled -> {
                        settingsRepository.setCrossAppSyncEnabled(action.enabled)
                        if (action.enabled) {
                            themeSyncManager.onCrossAppSyncEnabled()
                        }
                    }
                    is SettingsAction.SetReduceLiveBlur ->
                        settingsRepository.setReduceLiveBlur(action.enabled)
                    is SettingsAction.SetTabTransitionMode ->
                        settingsRepository.setTabTransitionMode(action.mode)
                    is SettingsAction.SetWidgetColorMode -> {
                        settingsRepository.setWidgetColorMode(action.mode)
                        widgetUpdateTrigger.updateAll()
                    }
                    is SettingsAction.SetWidgetSurfaceStyle -> {
                        settingsRepository.setWidgetSurfaceStyle(action.style)
                        widgetUpdateTrigger.updateAll()
                    }
                    is SettingsAction.SetAccountTabVisibility ->
                        settingsRepository.setAccountTabVisibility(action.visibility)
                    is SettingsAction.SetAppLanguage -> {
                        settingsRepository.setAppLanguage(action.language)
                        notificationManager.ensureChannel(action.language)
                        widgetUpdateTrigger.updateAll()
                    }
                    SettingsAction.RefreshAlarmPermission -> Unit
                }
            }
        }
    }
}
