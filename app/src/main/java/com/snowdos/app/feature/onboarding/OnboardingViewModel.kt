package com.snowdos.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snowdos.app.core.common.AppTextResolver
import com.snowdos.app.core.common.DefaultJvmTextResolver
import com.snowdos.app.core.common.TimeProvider
import com.snowdos.app.domain.model.AppLanguage
import com.snowdos.app.domain.repository.SettingsRepository
import com.snowdos.app.domain.strategy.NoOpWidgetUpdateTrigger
import com.snowdos.app.domain.strategy.WidgetUpdateTrigger
import com.snowdos.app.domain.usecase.CreateTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val selectedTemplateIds: Set<String> = emptySet(),
    val isFinishing: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.fromSystemLocale(),
    val selectedThemeId: String = "standard_m3"
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val createTaskUseCase: CreateTaskUseCase,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    private val textResolver: AppTextResolver,
    private val widgetUpdateTrigger: WidgetUpdateTrigger
) : ViewModel() {

    constructor(
        createTaskUseCase: CreateTaskUseCase,
        settingsRepository: SettingsRepository,
        timeProvider: TimeProvider
    ) : this(
        createTaskUseCase = createTaskUseCase,
        settingsRepository = settingsRepository,
        timeProvider = timeProvider,
        textResolver = DefaultJvmTextResolver,
        widgetUpdateTrigger = NoOpWidgetUpdateTrigger
    )

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                _uiState.update {
                    it.copy(
                        appLanguage = settings.appLanguage,
                        selectedThemeId = settings.themeId
                    )
                }
            }
        }
    }

    fun toggleTemplate(templateId: String) {
        _uiState.update { state ->
            val updated = if (templateId in state.selectedTemplateIds) {
                state.selectedTemplateIds - templateId
            } else {
                state.selectedTemplateIds + templateId
            }
            state.copy(selectedTemplateIds = updated)
        }
    }

    fun toggleLanguage() {
        viewModelScope.launch {
            val current = settingsRepository.observeSettings().first().appLanguage
            settingsRepository.setAppLanguage(current.toggled())
            widgetUpdateTrigger.updateAll()
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            settingsRepository.setAppLanguage(language)
            widgetUpdateTrigger.updateAll()
        }
    }

    fun selectThemePreset(presetId: String) {
        viewModelScope.launch {
            settingsRepository.setThemeId(presetId)
            widgetUpdateTrigger.updateAll()
        }
    }

    fun skipOnboarding(onDone: () -> Unit) {
        if (_uiState.value.isFinishing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isFinishing = true) }
            settingsRepository.setHasCompletedOnboarding(true)
            onDone()
        }
    }

    fun completeOnboarding(onDone: () -> Unit) {
        if (_uiState.value.isFinishing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isFinishing = true) }
            val selectedIds = _uiState.value.selectedTemplateIds
            val language = settingsRepository.observeSettings().first().appLanguage
            val now = timeProvider.nowEpochMillis()
            val chosenTemplates = OnboardingTemplateCatalog.templates.filter { it.id in selectedIds }

            for (template in chosenTemplates) {
                val triggerAt = template.computeInitialTriggerMillis(now)
                createTaskUseCase(
                    title = textResolver.getString(template.titleRes, language),
                    notes = textResolver.getString(template.notesRes, language),
                    priority = template.priority,
                    recurrence = template.recurrence,
                    dueAtEpochMillis = triggerAt,
                    remindAtEpochMillis = triggerAt
                )
            }

            settingsRepository.setHasCompletedOnboarding(true)
            onDone()
        }
    }
}
