package com.snowdos.app.domain.model

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.util.Locale

enum class DismissBehavior {
    MARK_IGNORED,
    DELETE_PERMANENTLY
}

enum class ThemeMode {
    PRESET,
    CUSTOM
}

enum class EightBitVariant {
    PASTEL_PINK,
    PASTEL_BLUE
}

enum class TabTransitionMode {
    ANIMATED,
    INSTANT
}

enum class WidgetColorMode {
    FOLLOW_APP,
    LIGHT,
    DARK
}

enum class WidgetSurfaceStyle {
    GLASS,
    SOLID
}

enum class AccountTabVisibility {
    AUTO,
    ALWAYS_SHOW,
    MOVE_TO_SETTINGS
}

enum class AppLanguage(
    val code: String,
    val flagEmoji: String,
    val nativeName: String,
    val locale: Locale
) {
    GERMAN(
        code = "de",
        flagEmoji = "\uD83C\uDDE9\uD83C\uDDEA",
        nativeName = "Deutsch",
        locale = Locale.GERMAN
    ),
    ENGLISH(
        code = "en",
        flagEmoji = "\uD83C\uDDEC\uD83C\uDDE7",
        nativeName = "English",
        locale = Locale.ENGLISH
    );

    fun toggled(): AppLanguage = when (this) {
        GERMAN -> ENGLISH
        ENGLISH -> GERMAN
    }

    companion object {
        fun fromCode(code: String?): AppLanguage? =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }

        fun fromSystemLocale(): AppLanguage =
            if (Locale.getDefault().language.equals("de", ignoreCase = true)) GERMAN else ENGLISH
    }
}

@Immutable
data class ThemeAppearanceConfig(
    val mode: ThemeMode = ThemeMode.PRESET,
    val presetId: String = "standard_m3",
    val customAccentHex: String = "#6750A4",
    val gamingAccentHex: String = "#00E5FF",
    val eightBitVariant: EightBitVariant = EightBitVariant.PASTEL_PINK,
    val weatherStyle: WeatherThemeStyle = WeatherThemeStyle.CUTE_2D,
    val autoSeasonalThemeEnabled: Boolean = false,
    val backgroundImageUri: String? = null,
    val glassOpacity: Float = 0.24f
) {
    fun effectivePresetId(date: LocalDate = LocalDate.now()): String =
        if (mode == ThemeMode.PRESET && autoSeasonalThemeEnabled) {
            SeasonalThemeResolver.resolvePresetId(date)
        } else {
            presetId
        }
}

@Immutable
data class ScoreRulesConfig(
    val completedPoints: Int = 1,
    val ignoredPenalty: Int = -1,
    val missedPenalty: Int = -1,
    val missedGracePeriodMinutes: Int = 15
) {
    fun completedPointsFor(priority: TaskPriority): Int = completedPoints * priority.weight

    fun ignoredPenaltyFor(priority: TaskPriority): Int = ignoredPenalty * priority.weight

    fun missedPenaltyFor(priority: TaskPriority): Int = missedPenalty * priority.weight
}

@Immutable
data class AppSettings(
    val notificationsEnabled: Boolean = true,
    val antiIgnorePowerEnabled: Boolean = false,
    val reminderLeadTimeMinutes: Int = 10,
    val defaultSnoozeMinutes: Int = 15,
    val dismissBehavior: DismissBehavior = DismissBehavior.MARK_IGNORED,
    val scoreRules: ScoreRulesConfig = ScoreRulesConfig(),
    val themeId: String = "standard_m3",
    val appearance: ThemeAppearanceConfig = ThemeAppearanceConfig(),
    val dynamicAppIconEnabled: Boolean = true,
    val tabTransitionMode: TabTransitionMode = TabTransitionMode.ANIMATED,
    val widgetColorMode: WidgetColorMode = WidgetColorMode.FOLLOW_APP,
    val widgetSurfaceStyle: WidgetSurfaceStyle = WidgetSurfaceStyle.GLASS,
    val accountTabVisibility: AccountTabVisibility = AccountTabVisibility.MOVE_TO_SETTINGS,
    val appLanguage: AppLanguage = AppLanguage.fromSystemLocale(),
    val hasCompletedOnboarding: Boolean = false,
    val reduceLiveBlur: Boolean = false,
    val crossAppSyncEnabled: Boolean = true,
    val partnerAppConnected: Boolean = false,
    val lastSyncedRevision: Long = 0L,
    val lastAppliedSyncId: String = "",
    val localModifiedAtMillis: Long = 0L,
    val lastPublishedSyncId: String = "",
    val localBackgroundHash: String? = null,
    val weatherSnapshot: WeatherSnapshot? = null
)
