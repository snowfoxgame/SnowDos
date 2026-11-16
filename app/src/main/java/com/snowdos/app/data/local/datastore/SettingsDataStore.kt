package com.snowdos.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "snowdos_settings")

object SettingsPreferenceKeys {
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val ANTI_IGNORE_POWER_ENABLED = booleanPreferencesKey("anti_ignore_power_enabled")
    val REMINDER_LEAD_TIME_MINUTES = intPreferencesKey("reminder_lead_time_minutes")
    val DEFAULT_SNOOZE_MINUTES = intPreferencesKey("default_snooze_minutes")
    val DISMISS_BEHAVIOR = stringPreferencesKey("dismiss_behavior")
    val COMPLETED_POINTS = intPreferencesKey("completed_points")
    val IGNORED_PENALTY = intPreferencesKey("ignored_penalty")
    val MISSED_PENALTY = intPreferencesKey("missed_penalty")
    val MISSED_GRACE_MINUTES = intPreferencesKey("missed_grace_minutes")
    val THEME_ID = stringPreferencesKey("theme_id")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val CUSTOM_ACCENT_HEX = stringPreferencesKey("custom_accent_hex")
    val GAMING_ACCENT_HEX = stringPreferencesKey("gaming_accent_hex")
    val EIGHT_BIT_VARIANT = stringPreferencesKey("eight_bit_variant")
    val BACKGROUND_IMAGE_URI = stringPreferencesKey("background_image_uri")
    val GLASS_OPACITY = floatPreferencesKey("glass_opacity")
    val DYNAMIC_APP_ICON_ENABLED = booleanPreferencesKey("dynamic_app_icon_enabled")
    val TAB_TRANSITION_MODE = stringPreferencesKey("tab_transition_mode")
    val WIDGET_COLOR_MODE = stringPreferencesKey("widget_color_mode")
    val WIDGET_SURFACE_STYLE = stringPreferencesKey("widget_surface_style")
    val ACCOUNT_TAB_VISIBILITY = stringPreferencesKey("account_tab_visibility")
    val APP_LANGUAGE = stringPreferencesKey("app_language")
    val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
    val REDUCE_LIVE_BLUR = booleanPreferencesKey("reduce_live_blur")
    val CROSS_APP_SYNC_ENABLED = booleanPreferencesKey("cross_app_sync_enabled")
    val PARTNER_APP_CONNECTED = booleanPreferencesKey("partner_app_connected")
    val LAST_SYNCED_REVISION = longPreferencesKey("last_synced_revision")
    val LAST_APPLIED_SYNC_ID = stringPreferencesKey("last_applied_sync_id")
    val LOCAL_MODIFIED_AT_MILLIS = longPreferencesKey("local_modified_at_millis")
    val LAST_PUBLISHED_SYNC_ID = stringPreferencesKey("last_published_sync_id")
    val LOCAL_BACKGROUND_HASH = stringPreferencesKey("local_background_hash")
    val WEATHER_STYLE = stringPreferencesKey("weather_style")
    val AUTO_SEASONAL_THEME_ENABLED = booleanPreferencesKey("auto_seasonal_theme_enabled")
    val WEATHER_WMO_CODE = intPreferencesKey("weather_wmo_code")
    val WEATHER_IS_DAY = booleanPreferencesKey("weather_is_day")
    val WEATHER_SUNRISE_MS = longPreferencesKey("weather_sunrise_ms")
    val WEATHER_SUNSET_MS = longPreferencesKey("weather_sunset_ms")
    val WEATHER_FETCHED_MS = longPreferencesKey("weather_fetched_ms")
    val WEATHER_LOCATION_BASED = booleanPreferencesKey("weather_location_based")
    val WEATHER_LAT = doublePreferencesKey("weather_lat")
    val WEATHER_LON = doublePreferencesKey("weather_lon")
}
