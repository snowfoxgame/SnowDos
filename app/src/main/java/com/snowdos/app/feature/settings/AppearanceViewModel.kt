package com.snowdos.app.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snowdos.app.R
import com.snowdos.app.core.common.UiText
import com.snowdos.app.data.local.BackgroundImageLoadException
import com.snowdos.app.data.local.BackgroundImageStorage
import com.snowdos.app.data.local.PresetBackgroundGenerator
import com.snowdos.app.data.local.weather.WeatherRepository
import com.snowdos.app.domain.model.EightBitVariant
import com.snowdos.app.domain.model.ThemeAppearanceConfig
import com.snowdos.app.domain.model.ThemeMode
import com.snowdos.app.domain.model.WeatherSnapshot
import com.snowdos.app.domain.model.WeatherThemeStyle
import com.snowdos.app.domain.repository.SettingsRepository
import com.snowdos.app.domain.strategy.WidgetUpdateTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppearanceUiState(
    val appearance: ThemeAppearanceConfig = ThemeAppearanceConfig(),
    val presetThumbnails: Map<String, String> = emptyMap(),
    val isImportingBackground: Boolean = false,
    val statusMessage: UiText? = null,
    val weatherSnapshot: WeatherSnapshot? = null,
    val hasLocationPermission: Boolean = false
)

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backgroundImageStorage: BackgroundImageStorage,
    private val presetBackgroundGenerator: PresetBackgroundGenerator,
    private val widgetUpdateTrigger: WidgetUpdateTrigger,
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    private val isImporting = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<UiText?>(null)
    private val presetThumbnails = MutableStateFlow<Map<String, String>>(emptyMap())
    private val locationPermissionState = MutableStateFlow(weatherRepository.hasLocationPermission())
    private var debouncedWidgetRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.observeSettings()
                .map { it.appearance.gamingAccentHex to it.appearance.eightBitVariant }
                .distinctUntilChanged()
                .collect { (gamingHex, eightBitVariant) ->
                    presetThumbnails.value = presetBackgroundGenerator.getOrCreateAllThumbnails(
                        gamingAccentHex = gamingHex,
                        eightBitVariant = eightBitVariant
                    )
                }
        }
    }

    val uiState: StateFlow<AppearanceUiState> = combine(
        settingsRepository.observeSettings(),
        presetThumbnails,
        isImporting,
        statusMessage,
        locationPermissionState
    ) { settings, thumbs, importing, message, hasLoc ->
        AppearanceUiState(
            appearance = settings.appearance,
            presetThumbnails = thumbs,
            isImportingBackground = importing,
            statusMessage = message,
            weatherSnapshot = settings.weatherSnapshot,
            hasLocationPermission = hasLoc
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppearanceUiState()
    )

    fun selectPreset(presetId: String) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(ThemeMode.PRESET)
            settingsRepository.setThemeId(presetId)
            refreshWidgetsNow()
        }
    }

    fun updateGamingAccentHex(hex: String) {
        viewModelScope.launch {
            settingsRepository.setGamingAccentHex(hex)
            scheduleDebouncedWidgetUpdate()
        }
    }

    fun selectEightBitVariant(variant: EightBitVariant) {
        viewModelScope.launch {
            settingsRepository.setEightBitVariant(variant)
            refreshWidgetsNow()
        }
    }

    fun selectWeatherStyle(style: WeatherThemeStyle) {
        viewModelScope.launch {
            settingsRepository.setWeatherStyle(style)
            refreshWidgetsNow()
        }
    }

    fun setAutoSeasonalTheme(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoSeasonalThemeEnabled(enabled)
            refreshWidgetsNow()
        }
    }

    fun refreshWeather(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            locationPermissionState.value = weatherRepository.hasLocationPermission()
            weatherRepository.getEffectiveWeather(forceRefresh = forceRefresh)
            refreshWidgetsNow()
        }
    }

    fun updateGlassOpacity(opacity: Float) {
        viewModelScope.launch {
            settingsRepository.setGlassOpacity(opacity)
            scheduleDebouncedWidgetUpdate()
        }
    }

    fun importBackgroundFromUri(uri: Uri) {
        viewModelScope.launch {
            isImporting.value = true
            statusMessage.value = null
            backgroundImageStorage.importFromUri(uri)
                .onSuccess { storedUri ->
                    val hash = backgroundImageStorage.computeHashForUri(storedUri)
                    settingsRepository.updateLocalBackgroundHash(hash)
                    settingsRepository.setThemeMode(ThemeMode.PRESET)
                    settingsRepository.setBackgroundImageUri(storedUri)
                    statusMessage.value = UiText.res(R.string.appearance_msg_bg_applied)
                    refreshWidgetsNow()
                }
                .onFailure { error ->
                    statusMessage.value = if (error is BackgroundImageLoadException) {
                        UiText.res(R.string.appearance_msg_bg_failed)
                    } else {
                        error.message?.let { UiText.Dynamic(it) }
                            ?: UiText.res(R.string.appearance_msg_bg_failed)
                    }
                }
            isImporting.value = false
        }
    }

    fun resetBackground() {
        viewModelScope.launch {
            backgroundImageStorage.clearStoredImages()
            settingsRepository.updateLocalBackgroundHash(null)
            settingsRepository.setBackgroundImageUri(null)
            statusMessage.value = UiText.res(R.string.appearance_msg_bg_reset)
            refreshWidgetsNow()
        }
    }

    fun consumeMessage() {
        statusMessage.value = null
    }

    private suspend fun refreshWidgetsNow() {
        debouncedWidgetRefreshJob?.cancel()
        debouncedWidgetRefreshJob = null
        widgetUpdateTrigger.updateAll()
    }

    private fun scheduleDebouncedWidgetUpdate() {
        debouncedWidgetRefreshJob?.cancel()
        debouncedWidgetRefreshJob = viewModelScope.launch {
            delay(WIDGET_REFRESH_DEBOUNCE_MS)
            widgetUpdateTrigger.updateAll()
        }
    }

    companion object {
        internal const val WIDGET_REFRESH_DEBOUNCE_MS = 350L
    }
}
