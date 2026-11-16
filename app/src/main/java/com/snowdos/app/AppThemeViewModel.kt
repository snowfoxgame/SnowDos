package com.snowdos.app

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snowdos.app.core.designsystem.ThemeRegistry
import com.snowdos.app.core.designsystem.components.backgrounds.LiveBackgroundScene
import com.snowdos.app.data.local.PresetBackgroundGenerator
import com.snowdos.app.data.local.weather.WeatherRepository
import com.snowdos.app.domain.model.AppLanguage
import com.snowdos.app.domain.model.ThemeAppearanceConfig
import com.snowdos.app.domain.model.ThemeMode
import com.snowdos.app.domain.model.WeatherConditionKind
import com.snowdos.app.domain.model.WeatherConditionMapper
import com.snowdos.app.domain.model.WeatherSnapshot
import com.snowdos.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ResolvedAppThemeState(
    val appearance: ThemeAppearanceConfig = ThemeAppearanceConfig(),
    val appLanguage: AppLanguage = AppLanguage.fromSystemLocale(),
    val activeBackgroundUri: String? = null,
    val liveScene: LiveBackgroundScene = LiveBackgroundScene.None,
    val applyReadabilityScrim: Boolean = true,
    val isEffectiveDarkTheme: Boolean = true,
    val reduceLiveBlur: Boolean = false,
    val weatherIsDay: Boolean = true,
    val weatherCondition: WeatherConditionKind = WeatherConditionKind.CLEAR
)

private data class ThemeInputSnapshot(
    val appearance: ThemeAppearanceConfig,
    val appLanguage: AppLanguage,
    val reduceLiveBlur: Boolean,
    val weatherSnapshot: WeatherSnapshot?,
    val systemDark: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppThemeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val presetBackgroundGenerator: PresetBackgroundGenerator,
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    private val systemDarkTheme = MutableStateFlow(true)
    private val liveWeatherOverride = MutableStateFlow<WeatherSnapshot?>(null)

    fun updateSystemDarkTheme(isDark: Boolean) {
        systemDarkTheme.value = isDark
    }

    fun refreshWeatherIfNeeded() {
        viewModelScope.launch {
            liveWeatherOverride.value = weatherRepository.getEffectiveWeather(forceRefresh = false)
        }
    }

    val themeState: StateFlow<ResolvedAppThemeState> = combine(
        settingsRepository.observeSettings(),
        systemDarkTheme,
        liveWeatherOverride
    ) { settings, isDark, freshWeather ->
        ThemeInputSnapshot(
            appearance = settings.appearance,
            appLanguage = settings.appLanguage,
            reduceLiveBlur = settings.reduceLiveBlur,
            weatherSnapshot = freshWeather ?: settings.weatherSnapshot,
            systemDark = isDark
        )
    }
        .distinctUntilChanged()
        .mapLatest { input ->
            val appearance = input.appearance
            val resolvedPresetId = appearance.effectivePresetId()
            val weatherSnapshot = if (appearance.mode == ThemeMode.PRESET && resolvedPresetId == "weather_time") {
                input.weatherSnapshot ?: weatherRepository.getEffectiveWeather(forceRefresh = false)
            } else {
                input.weatherSnapshot ?: WeatherConditionMapper.resolveTimeFallbackSnapshot()
            }
            val effectiveDarkTheme = ThemeRegistry.isEffectiveDarkTheme(
                appearance = appearance,
                systemDarkTheme = input.systemDark,
                weatherIsDay = weatherSnapshot.isDay,
                weatherCondition = weatherSnapshot.condition
            )
            val customGalleryUri = appearance.backgroundImageUri?.takeIf { it.isNotBlank() }
            if (customGalleryUri != null) {
                return@mapLatest ResolvedAppThemeState(
                    appearance = appearance,
                    appLanguage = input.appLanguage,
                    activeBackgroundUri = customGalleryUri,
                    liveScene = LiveBackgroundScene.None,
                    applyReadabilityScrim = true,
                    isEffectiveDarkTheme = effectiveDarkTheme,
                    reduceLiveBlur = input.reduceLiveBlur
                )
            }
            when (appearance.mode) {
                ThemeMode.PRESET -> {
                    when (resolvedPresetId) {
                        "interactive_bubbles" -> ResolvedAppThemeState(
                            appearance = appearance,
                            appLanguage = input.appLanguage,
                            activeBackgroundUri = null,
                            liveScene = LiveBackgroundScene.InteractiveBubbles,
                            applyReadabilityScrim = false,
                            isEffectiveDarkTheme = effectiveDarkTheme,
                            reduceLiveBlur = input.reduceLiveBlur
                        )
                        "mini_city" -> ResolvedAppThemeState(
                            appearance = appearance,
                            appLanguage = input.appLanguage,
                            activeBackgroundUri = null,
                            liveScene = LiveBackgroundScene.MiniCity,
                            applyReadabilityScrim = false,
                            isEffectiveDarkTheme = effectiveDarkTheme,
                            reduceLiveBlur = input.reduceLiveBlur
                        )
                        "weather_time" -> ResolvedAppThemeState(
                            appearance = appearance,
                            appLanguage = input.appLanguage,
                            activeBackgroundUri = null,
                            liveScene = LiveBackgroundScene.WeatherTime(
                                style = appearance.weatherStyle,
                                snapshot = weatherSnapshot
                            ),
                            applyReadabilityScrim = false,
                            isEffectiveDarkTheme = effectiveDarkTheme,
                            reduceLiveBlur = input.reduceLiveBlur,
                            weatherIsDay = weatherSnapshot.isDay,
                            weatherCondition = weatherSnapshot.condition
                        )
                        else -> {
                            val preset = ThemeRegistry.findPreset(resolvedPresetId)
                            val presetUri = if (preset.hasPatternBackground) {
                                presetBackgroundGenerator.getOrCreateBackgroundUri(
                                    presetId = preset.id,
                                    darkTheme = effectiveDarkTheme,
                                    gamingAccentHex = appearance.gamingAccentHex,
                                    eightBitVariant = appearance.eightBitVariant
                                )
                            } else {
                                null
                            }
                            ResolvedAppThemeState(
                                appearance = appearance,
                                appLanguage = input.appLanguage,
                                activeBackgroundUri = presetUri,
                                liveScene = LiveBackgroundScene.None,
                                applyReadabilityScrim = false,
                                isEffectiveDarkTheme = effectiveDarkTheme,
                                reduceLiveBlur = input.reduceLiveBlur
                            )
                        }
                    }
                }
                ThemeMode.CUSTOM -> {
                    ResolvedAppThemeState(
                        appearance = appearance,
                        appLanguage = input.appLanguage,
                        activeBackgroundUri = appearance.backgroundImageUri,
                        liveScene = LiveBackgroundScene.None,
                        applyReadabilityScrim = true,
                        isEffectiveDarkTheme = effectiveDarkTheme,
                        reduceLiveBlur = input.reduceLiveBlur
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ResolvedAppThemeState()
        )
}
