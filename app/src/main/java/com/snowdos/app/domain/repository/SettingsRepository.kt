package com.snowdos.app.domain.repository

import com.snowdos.app.domain.model.AccountTabVisibility
import com.snowdos.app.domain.model.AppLanguage
import com.snowdos.app.domain.model.AppSettings
import com.snowdos.app.domain.model.DismissBehavior
import com.snowdos.app.domain.model.EightBitVariant
import com.snowdos.app.domain.model.ScoreRulesConfig
import com.snowdos.app.domain.model.TabTransitionMode
import com.snowdos.app.domain.model.ThemeAppearanceConfig
import com.snowdos.app.domain.model.ThemeMode
import com.snowdos.app.domain.model.WeatherSnapshot
import com.snowdos.app.domain.model.WeatherThemeStyle
import com.snowdos.app.domain.model.WidgetColorMode
import com.snowdos.app.domain.model.WidgetSurfaceStyle
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setAntiIgnorePowerEnabled(enabled: Boolean)
    suspend fun setReminderLeadTimeMinutes(minutes: Int)
    suspend fun setDefaultSnoozeMinutes(minutes: Int)
    suspend fun setDismissBehavior(behavior: DismissBehavior)
    suspend fun setScoreRules(rules: ScoreRulesConfig)
    suspend fun setTabTransitionMode(mode: TabTransitionMode)
    suspend fun setWidgetColorMode(mode: WidgetColorMode)
    suspend fun setWidgetSurfaceStyle(style: WidgetSurfaceStyle)
    suspend fun setAccountTabVisibility(visibility: AccountTabVisibility)
    suspend fun setAppLanguage(language: AppLanguage)
    suspend fun setThemeId(themeId: String)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setCustomAccentHex(hex: String)
    suspend fun setGamingAccentHex(hex: String)
    suspend fun setEightBitVariant(variant: EightBitVariant)
    suspend fun setWeatherStyle(style: WeatherThemeStyle)
    suspend fun setAutoSeasonalThemeEnabled(enabled: Boolean)
    suspend fun saveWeatherSnapshot(snapshot: WeatherSnapshot)
    suspend fun setBackgroundImageUri(uri: String?)
    suspend fun setGlassOpacity(opacity: Float)
    suspend fun setDynamicAppIconEnabled(enabled: Boolean)
    suspend fun setHasCompletedOnboarding(completed: Boolean)
    suspend fun setReduceLiveBlur(enabled: Boolean)
    suspend fun setCrossAppSyncEnabled(enabled: Boolean)
    suspend fun setPartnerAppConnected(connected: Boolean)
    suspend fun recordLocalAppearanceModified(modifiedAtMillis: Long, syncId: String)
    suspend fun markLocalAppearanceSynced(revision: Long, syncId: String)
    suspend fun updateLocalBackgroundHash(hash: String?)
    suspend fun applySyncedAppearance(
        appearance: ThemeAppearanceConfig,
        reduceLiveBlur: Boolean,
        syncRevision: Long,
        syncId: String,
        backgroundHash: String?
    )
}
