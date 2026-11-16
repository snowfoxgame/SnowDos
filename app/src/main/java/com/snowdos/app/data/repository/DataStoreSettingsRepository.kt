package com.snowdos.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.snowdos.app.data.local.datastore.SettingsPreferenceKeys
import com.snowdos.app.data.local.datastore.settingsDataStore
import com.snowdos.app.domain.model.AccountTabVisibility
import com.snowdos.app.domain.model.AppLanguage
import com.snowdos.app.domain.model.AppSettings
import com.snowdos.app.domain.model.DismissBehavior
import com.snowdos.app.domain.model.EightBitVariant
import com.snowdos.app.domain.model.ScoreRulesConfig
import com.snowdos.app.domain.model.TabTransitionMode
import com.snowdos.app.domain.model.ThemeAppearanceConfig
import com.snowdos.app.domain.model.ThemeMode
import com.snowdos.app.domain.model.WeatherConditionMapper
import com.snowdos.app.domain.model.WeatherSnapshot
import com.snowdos.app.domain.model.WeatherThemeStyle
import com.snowdos.app.domain.model.WidgetColorMode
import com.snowdos.app.domain.model.WidgetSurfaceStyle
import com.snowdos.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

class DataStoreSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) : SettingsRepository {

    private val dataStore = context.settingsDataStore

    override fun observeSettings(): Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            val defaults = AppSettings()
            val dismissRaw = prefs[SettingsPreferenceKeys.DISMISS_BEHAVIOR]
            val dismissBehavior = runCatching {
                dismissRaw?.let { DismissBehavior.valueOf(it) }
            }.getOrNull() ?: defaults.dismissBehavior

            val themeModeRaw = prefs[SettingsPreferenceKeys.THEME_MODE]
            val themeMode = runCatching {
                themeModeRaw?.let { ThemeMode.valueOf(it) }
            }.getOrNull()?.let { parsed ->
                if (parsed == ThemeMode.CUSTOM) ThemeMode.PRESET else parsed
            } ?: defaults.appearance.mode

            val tabTransitionRaw = prefs[SettingsPreferenceKeys.TAB_TRANSITION_MODE]
            val tabTransitionMode = runCatching {
                tabTransitionRaw?.let { TabTransitionMode.valueOf(it) }
            }.getOrNull() ?: defaults.tabTransitionMode

            val widgetColorModeRaw = prefs[SettingsPreferenceKeys.WIDGET_COLOR_MODE]
            val widgetColorMode = runCatching {
                widgetColorModeRaw?.let { WidgetColorMode.valueOf(it) }
            }.getOrNull() ?: defaults.widgetColorMode

            val widgetSurfaceStyleRaw = prefs[SettingsPreferenceKeys.WIDGET_SURFACE_STYLE]
            val widgetSurfaceStyle = runCatching {
                widgetSurfaceStyleRaw?.let { WidgetSurfaceStyle.valueOf(it) }
            }.getOrNull() ?: defaults.widgetSurfaceStyle

            val accountVisibilityRaw = prefs[SettingsPreferenceKeys.ACCOUNT_TAB_VISIBILITY]
            val accountTabVisibility = runCatching {
                accountVisibilityRaw?.let { AccountTabVisibility.valueOf(it) }
            }.getOrNull() ?: defaults.accountTabVisibility

            val appLanguage = AppLanguage.fromCode(prefs[SettingsPreferenceKeys.APP_LANGUAGE])
                ?: defaults.appLanguage

            val presetId = prefs[SettingsPreferenceKeys.THEME_ID] ?: defaults.themeId
            val customAccentHex = prefs[SettingsPreferenceKeys.CUSTOM_ACCENT_HEX]
                ?: defaults.appearance.customAccentHex
            val gamingAccentHex = prefs[SettingsPreferenceKeys.GAMING_ACCENT_HEX]
                ?: defaults.appearance.gamingAccentHex
            val eightBitVariantRaw = prefs[SettingsPreferenceKeys.EIGHT_BIT_VARIANT]
            val eightBitVariant = runCatching {
                eightBitVariantRaw?.let { EightBitVariant.valueOf(it) }
            }.getOrNull() ?: defaults.appearance.eightBitVariant
            val weatherStyleRaw = prefs[SettingsPreferenceKeys.WEATHER_STYLE]
            val weatherStyle = runCatching {
                weatherStyleRaw?.let { WeatherThemeStyle.valueOf(it) }
            }.getOrNull() ?: defaults.appearance.weatherStyle
            val autoSeasonalThemeEnabled = prefs[SettingsPreferenceKeys.AUTO_SEASONAL_THEME_ENABLED]
                ?: defaults.appearance.autoSeasonalThemeEnabled
            val backgroundImageUri = prefs[SettingsPreferenceKeys.BACKGROUND_IMAGE_URI]
            val weatherFetchedMs = prefs[SettingsPreferenceKeys.WEATHER_FETCHED_MS]
            val cachedWeather = if (weatherFetchedMs != null) {
                val wmoCode = prefs[SettingsPreferenceKeys.WEATHER_WMO_CODE] ?: 0
                WeatherSnapshot(
                    wmoCode = wmoCode,
                    condition = WeatherConditionMapper.fromWmoCode(wmoCode),
                    isDay = prefs[SettingsPreferenceKeys.WEATHER_IS_DAY] ?: true,
                    sunriseEpochMillis = prefs[SettingsPreferenceKeys.WEATHER_SUNRISE_MS] ?: 0L,
                    sunsetEpochMillis = prefs[SettingsPreferenceKeys.WEATHER_SUNSET_MS] ?: 0L,
                    fetchedAtEpochMillis = weatherFetchedMs,
                    isLocationBased = prefs[SettingsPreferenceKeys.WEATHER_LOCATION_BASED] ?: false,
                    latitude = prefs[SettingsPreferenceKeys.WEATHER_LAT],
                    longitude = prefs[SettingsPreferenceKeys.WEATHER_LON]
                )
            } else {
                null
            }

            AppSettings(
                notificationsEnabled = prefs[SettingsPreferenceKeys.NOTIFICATIONS_ENABLED]
                    ?: defaults.notificationsEnabled,
                // Intensive full-screen mode is temporarily locked off while experimental.
                antiIgnorePowerEnabled = false,
                reminderLeadTimeMinutes = (prefs[SettingsPreferenceKeys.REMINDER_LEAD_TIME_MINUTES]
                    ?: defaults.reminderLeadTimeMinutes).coerceAtLeast(0),
                defaultSnoozeMinutes = prefs[SettingsPreferenceKeys.DEFAULT_SNOOZE_MINUTES]
                    ?: defaults.defaultSnoozeMinutes,
                dismissBehavior = dismissBehavior,
                scoreRules = ScoreRulesConfig(
                    completedPoints = prefs[SettingsPreferenceKeys.COMPLETED_POINTS]
                        ?: defaults.scoreRules.completedPoints,
                    ignoredPenalty = prefs[SettingsPreferenceKeys.IGNORED_PENALTY]
                        ?: defaults.scoreRules.ignoredPenalty,
                    missedPenalty = prefs[SettingsPreferenceKeys.MISSED_PENALTY]
                        ?: defaults.scoreRules.missedPenalty,
                    missedGracePeriodMinutes = prefs[SettingsPreferenceKeys.MISSED_GRACE_MINUTES]
                        ?: defaults.scoreRules.missedGracePeriodMinutes
                ),
                themeId = presetId,
                appearance = ThemeAppearanceConfig(
                    mode = themeMode,
                    presetId = presetId,
                    customAccentHex = customAccentHex,
                    gamingAccentHex = gamingAccentHex,
                    eightBitVariant = eightBitVariant,
                    weatherStyle = weatherStyle,
                    autoSeasonalThemeEnabled = autoSeasonalThemeEnabled,
                    backgroundImageUri = backgroundImageUri,
                    glassOpacity = (prefs[SettingsPreferenceKeys.GLASS_OPACITY]
                        ?: defaults.appearance.glassOpacity).coerceIn(0.10f, 0.55f)
                ),
                dynamicAppIconEnabled = prefs[SettingsPreferenceKeys.DYNAMIC_APP_ICON_ENABLED]
                    ?: defaults.dynamicAppIconEnabled,
                tabTransitionMode = tabTransitionMode,
                widgetColorMode = widgetColorMode,
                widgetSurfaceStyle = widgetSurfaceStyle,
                accountTabVisibility = accountTabVisibility,
                appLanguage = appLanguage,
                hasCompletedOnboarding = prefs[SettingsPreferenceKeys.HAS_COMPLETED_ONBOARDING]
                    ?: defaults.hasCompletedOnboarding,
                reduceLiveBlur = prefs[SettingsPreferenceKeys.REDUCE_LIVE_BLUR]
                    ?: defaults.reduceLiveBlur,
                crossAppSyncEnabled = prefs[SettingsPreferenceKeys.CROSS_APP_SYNC_ENABLED]
                    ?: defaults.crossAppSyncEnabled,
                partnerAppConnected = prefs[SettingsPreferenceKeys.PARTNER_APP_CONNECTED]
                    ?: defaults.partnerAppConnected,
                lastSyncedRevision = prefs[SettingsPreferenceKeys.LAST_SYNCED_REVISION]
                    ?: defaults.lastSyncedRevision,
                lastAppliedSyncId = prefs[SettingsPreferenceKeys.LAST_APPLIED_SYNC_ID]
                    ?: defaults.lastAppliedSyncId,
                localModifiedAtMillis = prefs[SettingsPreferenceKeys.LOCAL_MODIFIED_AT_MILLIS]
                    ?: defaults.localModifiedAtMillis,
                lastPublishedSyncId = prefs[SettingsPreferenceKeys.LAST_PUBLISHED_SYNC_ID]
                    ?: defaults.lastPublishedSyncId,
                localBackgroundHash = prefs[SettingsPreferenceKeys.LOCAL_BACKGROUND_HASH],
                weatherSnapshot = cachedWeather
            )
        }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.NOTIFICATIONS_ENABLED] = enabled }
    }

    override suspend fun setAntiIgnorePowerEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.ANTI_IGNORE_POWER_ENABLED] = false }
    }

    override suspend fun setReminderLeadTimeMinutes(minutes: Int) {
        dataStore.edit { it[SettingsPreferenceKeys.REMINDER_LEAD_TIME_MINUTES] = minutes.coerceAtLeast(0) }
    }

    override suspend fun setDefaultSnoozeMinutes(minutes: Int) {
        dataStore.edit { it[SettingsPreferenceKeys.DEFAULT_SNOOZE_MINUTES] = minutes.coerceAtLeast(1) }
    }

    override suspend fun setDismissBehavior(behavior: DismissBehavior) {
        dataStore.edit { it[SettingsPreferenceKeys.DISMISS_BEHAVIOR] = behavior.name }
    }

    override suspend fun setScoreRules(rules: ScoreRulesConfig) {
        dataStore.edit { prefs ->
            prefs[SettingsPreferenceKeys.COMPLETED_POINTS] = rules.completedPoints
            prefs[SettingsPreferenceKeys.IGNORED_PENALTY] = rules.ignoredPenalty
            prefs[SettingsPreferenceKeys.MISSED_PENALTY] = rules.missedPenalty
            prefs[SettingsPreferenceKeys.MISSED_GRACE_MINUTES] = rules.missedGracePeriodMinutes.coerceAtLeast(0)
        }
    }

    override suspend fun setThemeId(themeId: String) {
        dataStore.edit {
            it[SettingsPreferenceKeys.THEME_ID] = themeId
            it[SettingsPreferenceKeys.THEME_MODE] = ThemeMode.PRESET.name
            it[SettingsPreferenceKeys.AUTO_SEASONAL_THEME_ENABLED] = false
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit {
            it[SettingsPreferenceKeys.THEME_MODE] = mode.name
            if (mode == ThemeMode.CUSTOM) {
                it[SettingsPreferenceKeys.AUTO_SEASONAL_THEME_ENABLED] = false
            }
        }
    }

    override suspend fun setCustomAccentHex(hex: String) {
        dataStore.edit {
            it[SettingsPreferenceKeys.CUSTOM_ACCENT_HEX] = hex
            it[SettingsPreferenceKeys.THEME_MODE] = ThemeMode.CUSTOM.name
            it[SettingsPreferenceKeys.AUTO_SEASONAL_THEME_ENABLED] = false
        }
    }

    override suspend fun setGamingAccentHex(hex: String) {
        dataStore.edit {
            it[SettingsPreferenceKeys.GAMING_ACCENT_HEX] = hex
            it[SettingsPreferenceKeys.THEME_ID] = "gaming"
            it[SettingsPreferenceKeys.THEME_MODE] = ThemeMode.PRESET.name
            it[SettingsPreferenceKeys.AUTO_SEASONAL_THEME_ENABLED] = false
        }
    }

    override suspend fun setEightBitVariant(variant: EightBitVariant) {
        dataStore.edit {
            it[SettingsPreferenceKeys.EIGHT_BIT_VARIANT] = variant.name
            it[SettingsPreferenceKeys.THEME_ID] = "8bit_cute"
            it[SettingsPreferenceKeys.THEME_MODE] = ThemeMode.PRESET.name
            it[SettingsPreferenceKeys.AUTO_SEASONAL_THEME_ENABLED] = false
        }
    }

    override suspend fun setWeatherStyle(style: WeatherThemeStyle) {
        dataStore.edit {
            it[SettingsPreferenceKeys.WEATHER_STYLE] = style.name
            it[SettingsPreferenceKeys.THEME_ID] = "weather_time"
            it[SettingsPreferenceKeys.THEME_MODE] = ThemeMode.PRESET.name
            it[SettingsPreferenceKeys.AUTO_SEASONAL_THEME_ENABLED] = false
        }
    }

    override suspend fun setAutoSeasonalThemeEnabled(enabled: Boolean) {
        dataStore.edit {
            it[SettingsPreferenceKeys.AUTO_SEASONAL_THEME_ENABLED] = enabled
            if (enabled) {
                it[SettingsPreferenceKeys.THEME_MODE] = ThemeMode.PRESET.name
            }
        }
    }

    override suspend fun saveWeatherSnapshot(snapshot: WeatherSnapshot) {
        dataStore.edit { prefs ->
            prefs[SettingsPreferenceKeys.WEATHER_WMO_CODE] = snapshot.wmoCode
            prefs[SettingsPreferenceKeys.WEATHER_IS_DAY] = snapshot.isDay
            prefs[SettingsPreferenceKeys.WEATHER_SUNRISE_MS] = snapshot.sunriseEpochMillis
            prefs[SettingsPreferenceKeys.WEATHER_SUNSET_MS] = snapshot.sunsetEpochMillis
            prefs[SettingsPreferenceKeys.WEATHER_FETCHED_MS] = snapshot.fetchedAtEpochMillis
            prefs[SettingsPreferenceKeys.WEATHER_LOCATION_BASED] = snapshot.isLocationBased
            if (snapshot.latitude != null && snapshot.longitude != null) {
                prefs[SettingsPreferenceKeys.WEATHER_LAT] = snapshot.latitude
                prefs[SettingsPreferenceKeys.WEATHER_LON] = snapshot.longitude
            }
        }
    }

    override suspend fun setBackgroundImageUri(uri: String?) {
        dataStore.edit { prefs ->
            if (uri == null) {
                prefs.remove(SettingsPreferenceKeys.BACKGROUND_IMAGE_URI)
            } else {
                prefs[SettingsPreferenceKeys.BACKGROUND_IMAGE_URI] = uri
            }
        }
    }

    override suspend fun setGlassOpacity(opacity: Float) {
        dataStore.edit {
            it[SettingsPreferenceKeys.GLASS_OPACITY] = opacity.coerceIn(0.10f, 0.55f)
        }
    }

    override suspend fun setDynamicAppIconEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.DYNAMIC_APP_ICON_ENABLED] = enabled }
    }

    override suspend fun setTabTransitionMode(mode: TabTransitionMode) {
        dataStore.edit { it[SettingsPreferenceKeys.TAB_TRANSITION_MODE] = mode.name }
    }

    override suspend fun setWidgetColorMode(mode: WidgetColorMode) {
        dataStore.edit { it[SettingsPreferenceKeys.WIDGET_COLOR_MODE] = mode.name }
    }

    override suspend fun setWidgetSurfaceStyle(style: WidgetSurfaceStyle) {
        dataStore.edit { it[SettingsPreferenceKeys.WIDGET_SURFACE_STYLE] = style.name }
    }

    override suspend fun setAccountTabVisibility(visibility: AccountTabVisibility) {
        dataStore.edit { it[SettingsPreferenceKeys.ACCOUNT_TAB_VISIBILITY] = visibility.name }
    }

    override suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { it[SettingsPreferenceKeys.APP_LANGUAGE] = language.code }
    }

    override suspend fun setHasCompletedOnboarding(completed: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.HAS_COMPLETED_ONBOARDING] = completed }
    }

    override suspend fun setReduceLiveBlur(enabled: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.REDUCE_LIVE_BLUR] = enabled }
    }

    override suspend fun setCrossAppSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.CROSS_APP_SYNC_ENABLED] = enabled }
    }

    override suspend fun setPartnerAppConnected(connected: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.PARTNER_APP_CONNECTED] = connected }
    }

    override suspend fun recordLocalAppearanceModified(modifiedAtMillis: Long, syncId: String) {
        dataStore.edit { prefs ->
            prefs[SettingsPreferenceKeys.LOCAL_MODIFIED_AT_MILLIS] = modifiedAtMillis
            prefs[SettingsPreferenceKeys.LAST_PUBLISHED_SYNC_ID] = syncId
        }
    }

    override suspend fun markLocalAppearanceSynced(revision: Long, syncId: String) {
        dataStore.edit { prefs ->
            prefs[SettingsPreferenceKeys.LAST_SYNCED_REVISION] = revision
            prefs[SettingsPreferenceKeys.LAST_APPLIED_SYNC_ID] = syncId
            prefs[SettingsPreferenceKeys.LOCAL_MODIFIED_AT_MILLIS] = revision
            prefs[SettingsPreferenceKeys.LAST_PUBLISHED_SYNC_ID] = syncId
        }
    }

    override suspend fun updateLocalBackgroundHash(hash: String?) {
        dataStore.edit { prefs ->
            if (hash.isNullOrBlank()) {
                prefs.remove(SettingsPreferenceKeys.LOCAL_BACKGROUND_HASH)
            } else {
                prefs[SettingsPreferenceKeys.LOCAL_BACKGROUND_HASH] = hash
            }
        }
    }

    override suspend fun applySyncedAppearance(
        appearance: ThemeAppearanceConfig,
        reduceLiveBlur: Boolean,
        syncRevision: Long,
        syncId: String,
        backgroundHash: String?
    ) {
        dataStore.edit { prefs ->
            prefs[SettingsPreferenceKeys.THEME_ID] = appearance.presetId
            prefs[SettingsPreferenceKeys.THEME_MODE] = appearance.mode.name
            prefs[SettingsPreferenceKeys.CUSTOM_ACCENT_HEX] = appearance.customAccentHex
            prefs[SettingsPreferenceKeys.GAMING_ACCENT_HEX] = appearance.gamingAccentHex
            prefs[SettingsPreferenceKeys.EIGHT_BIT_VARIANT] = appearance.eightBitVariant.name
            prefs[SettingsPreferenceKeys.WEATHER_STYLE] = appearance.weatherStyle.name
            prefs[SettingsPreferenceKeys.AUTO_SEASONAL_THEME_ENABLED] = appearance.autoSeasonalThemeEnabled
            prefs[SettingsPreferenceKeys.GLASS_OPACITY] = appearance.glassOpacity.coerceIn(0.10f, 0.55f)
            prefs[SettingsPreferenceKeys.REDUCE_LIVE_BLUR] = reduceLiveBlur
            prefs[SettingsPreferenceKeys.LAST_SYNCED_REVISION] = syncRevision
            prefs[SettingsPreferenceKeys.LAST_APPLIED_SYNC_ID] = syncId
            prefs[SettingsPreferenceKeys.LOCAL_MODIFIED_AT_MILLIS] = syncRevision
            prefs[SettingsPreferenceKeys.LAST_PUBLISHED_SYNC_ID] = syncId

            val bgUri = appearance.backgroundImageUri
            if (bgUri.isNullOrBlank()) {
                prefs.remove(SettingsPreferenceKeys.BACKGROUND_IMAGE_URI)
                prefs.remove(SettingsPreferenceKeys.LOCAL_BACKGROUND_HASH)
            } else {
                prefs[SettingsPreferenceKeys.BACKGROUND_IMAGE_URI] = bgUri
                if (backgroundHash.isNullOrBlank()) {
                    prefs.remove(SettingsPreferenceKeys.LOCAL_BACKGROUND_HASH)
                } else {
                    prefs[SettingsPreferenceKeys.LOCAL_BACKGROUND_HASH] = backgroundHash
                }
            }
        }
    }
}
