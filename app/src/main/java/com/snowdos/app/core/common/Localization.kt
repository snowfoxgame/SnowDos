package com.snowdos.app.core.common

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.snowdos.app.R
import com.snowdos.app.core.designsystem.ThemeCategory
import com.snowdos.app.core.designsystem.ThemePreset
import com.snowdos.app.domain.model.ActivityEventType
import com.snowdos.app.domain.model.AppLanguage
import com.snowdos.app.domain.model.RecurrenceRule
import com.snowdos.app.domain.model.TaskPriority
import com.snowdos.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * UI-safe localized text abstraction so ViewModels do not embed raw copy.
 */
sealed interface UiText {
    data class StringResource(
        @param:StringRes val resId: Int,
        val args: List<Any> = emptyList()
    ) : UiText

    data class PluralResource(
        @param:PluralsRes val resId: Int,
        val quantity: Int,
        val args: List<Any> = emptyList()
    ) : UiText

    data class Dynamic(val value: String) : UiText

    companion object {
        fun res(@StringRes resId: Int, vararg args: Any): UiText =
            StringResource(resId = resId, args = args.toList())

        fun plural(@PluralsRes resId: Int, quantity: Int, vararg args: Any): UiText =
            PluralResource(resId = resId, quantity = quantity, args = args.toList())
    }
}

@Composable
@ReadOnlyComposable
fun UiText.asString(): String = when (this) {
    is UiText.StringResource -> {
        if (args.isEmpty()) stringResource(resId)
        else stringResource(resId, *args.toTypedArray())
    }
    is UiText.PluralResource -> {
        if (args.isEmpty()) pluralStringResource(resId, quantity)
        else pluralStringResource(resId, quantity, *args.toTypedArray())
    }
    is UiText.Dynamic -> value
}

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.StringResource -> {
        if (args.isEmpty()) context.getString(resId)
        else context.getString(resId, *args.toTypedArray())
    }
    is UiText.PluralResource -> {
        if (args.isEmpty()) context.resources.getQuantityString(resId, quantity)
        else context.resources.getQuantityString(resId, quantity, *args.toTypedArray())
    }
    is UiText.Dynamic -> value
}

/**
 * Resolves localized strings outside Compose (notifications, ViewModels, onboarding persistence).
 */
interface AppTextResolver {
    suspend fun currentLanguage(): AppLanguage

    fun getString(
        @StringRes resId: Int,
        language: AppLanguage,
        vararg args: Any
    ): String
}

@Singleton
class AndroidAppTextResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : AppTextResolver {
    override suspend fun currentLanguage(): AppLanguage =
        settingsRepository.observeSettings().first().appLanguage

    override fun getString(
        @StringRes resId: Int,
        language: AppLanguage,
        vararg args: Any
    ): String {
        val resources = context.localizedResourcesFor(language)
        return if (args.isEmpty()) {
            resources.getString(resId)
        } else {
            resources.getString(resId, *args)
        }
    }
}

/**
 * Pure-JVM fallback resolver used as default argument in ViewModels/strategies for unit tests
 * that instantiate classes without Android Context.
 */
object DefaultJvmTextResolver : AppTextResolver {
    override suspend fun currentLanguage(): AppLanguage = AppLanguage.GERMAN

    override fun getString(
        @StringRes resId: Int,
        language: AppLanguage,
        vararg args: Any
    ): String {
        val template = when (language) {
            AppLanguage.ENGLISH -> englishDefaults[resId] ?: germanDefaults[resId]
            AppLanguage.GERMAN -> germanDefaults[resId] ?: englishDefaults[resId]
        } ?: return ""
        return if (args.isEmpty()) template else String.format(language.locale, template, *args)
    }

    private val germanDefaults: Map<Int, String> = mapOf(
        R.string.template_shower_title to "Duschrhythmus",
        R.string.template_shower_notes to "Regelmäßige Auszeit zum Frischmachen",
        R.string.template_meals_title to "Essrhythmus",
        R.string.template_meals_notes to "Bewusst Zeit für eine vollwertige Mahlzeit nehmen",
        R.string.template_hydrate_title to "Trinken-Erinnerung",
        R.string.template_hydrate_notes to "Ein Glas Wasser für Kopf & Energie",
        R.string.template_brush_teeth_title to "Zähneputzen",
        R.string.template_brush_teeth_notes to "Abendroutine abschließen",
        R.string.template_bedtime_title to "Schlafenszeit",
        R.string.template_bedtime_notes to "Bildschirme weglegen und zur Ruhe kommen",
        R.string.template_tidy_up_title to "Aufräumen",
        R.string.template_tidy_up_notes to "10 Minuten Grundordnung im Zimmer schaffen",
        R.string.template_laundry_title to "Wäsche & Haushalt",
        R.string.template_laundry_notes to "Wäsche sammeln, waschen und falten",
        R.string.template_trash_title to "Müll rausbringen",
        R.string.template_trash_notes to "Restmüll, Papier oder Bio rechtzeitig leeren",
        R.string.template_study_title to "Hausaufgaben / Lernen",
        R.string.template_study_notes to "Feste Lerneinheit ohne Ablenkung",
        R.string.template_project_title to "Projekt- / Fokuszeit",
        R.string.template_project_notes to "Konzentriert an einem wichtigen Ziel arbeiten",
        R.string.template_daily_plan_title to "Tagesplan checken",
        R.string.template_daily_plan_notes to "Kurzer Überblick über Prioritäten und Termine",
        R.string.template_movement_title to "Kurze Bewegungspause",
        R.string.template_movement_notes to "Aufstehen, strecken oder kurz frische Luft schnappen",
        R.string.notif_due_at to "Fällig um %1\$s",
        R.string.notif_standard_title_snoozed to "Erinnerung (%1\$dx verschoben)",
        R.string.notif_standard_default_body to "Aufgabe fällig – jetzt erledigen oder bewusst verschieben.",
        R.string.notif_escalating_title_snoozed to "Dringend (%1\$dx geschoben): %2\$s",
        R.string.notif_escalating_title_due to "Anti-Ignore-Alarm: %1\$s",
        R.string.notif_escalating_body_header to "Intensiv-Modus aktiv",
        R.string.notif_escalating_body_snoozed to " · Bereits %1\$dx verschoben",
        R.string.notif_escalating_body_footer to " – bitte jetzt erledigen oder bewusst snoozen."
    )

    private val englishDefaults: Map<Int, String> = mapOf(
        R.string.template_shower_title to "Shower routine",
        R.string.template_shower_notes to "Regular reset to feel fresh and energized",
        R.string.template_meals_title to "Meal schedule",
        R.string.template_meals_notes to "Take mindful time for a proper meal",
        R.string.template_hydrate_title to "Hydration reminder",
        R.string.template_hydrate_notes to "A glass of water for focus and energy",
        R.string.template_brush_teeth_title to "Brush teeth",
        R.string.template_brush_teeth_notes to "Wrap up your evening care routine",
        R.string.template_bedtime_title to "Wind down for bed",
        R.string.template_bedtime_notes to "Put screens away and get ready to sleep",
        R.string.template_tidy_up_title to "Quick tidy-up",
        R.string.template_tidy_up_notes to "10-minute reset to keep your space clear",
        R.string.template_laundry_title to "Laundry & chores",
        R.string.template_laundry_notes to "Collect, wash, and fold laundry",
        R.string.template_trash_title to "Take out trash",
        R.string.template_trash_notes to "Empty general waste, paper, or organics on time",
        R.string.template_study_title to "Study / Homework",
        R.string.template_study_notes to "Dedicated distraction-free study block",
        R.string.template_project_title to "Deep focus session",
        R.string.template_project_notes to "Work steadily on an important goal",
        R.string.template_daily_plan_title to "Daily plan check-in",
        R.string.template_daily_plan_notes to "Quick review of today's priorities and schedule",
        R.string.template_movement_title to "Movement break",
        R.string.template_movement_notes to "Stand up, stretch, or get a breath of fresh air",
        R.string.notif_due_at to "Due at %1\$s",
        R.string.notif_standard_title_snoozed to "Reminder (snoozed %1\$dx)",
        R.string.notif_standard_default_body to "Task due now — complete it or snooze with intention.",
        R.string.notif_escalating_title_snoozed to "Urgent (snoozed %1\$dx): %2\$s",
        R.string.notif_escalating_title_due to "Anti-Ignore Alarm: %1\$s",
        R.string.notif_escalating_body_header to "Intensive Mode active",
        R.string.notif_escalating_body_snoozed to " · Already snoozed %1\$dx",
        R.string.notif_escalating_body_footer to " — complete now or snooze intentionally."
    )
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.fromSystemLocale() }

private class LocalizedContextWrapper(
    base: Context,
    private val localizedResources: Resources
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
    override fun getAssets(): AssetManager = localizedResources.assets
}

fun Context.localizedResourcesFor(language: AppLanguage): Resources {
    val config = Configuration(resources.configuration).apply {
        setLocale(language.locale)
        setLayoutDirection(language.locale)
    }
    return createConfigurationContext(config).resources
}

/**
 * Overrides Compose [LocalConfiguration], [LocalContext] resources, [LocalAppLanguage], and
 * [Locale.setDefault] dynamically so switching between German and English updates every
 * composable live without destroying the Activity.
 */
@Composable
fun ProvideAppLanguage(
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current

    val localizedConfiguration = remember(baseConfiguration, language) {
        Locale.setDefault(language.locale)
        Configuration(baseConfiguration).apply {
            setLocale(language.locale)
            setLayoutDirection(language.locale)
        }
    }

    val localizedContext = remember(baseContext, localizedConfiguration) {
        val configResources = baseContext.createConfigurationContext(localizedConfiguration).resources
        LocalizedContextWrapper(baseContext, configResources)
    }

    CompositionLocalProvider(
        LocalAppLanguage provides language,
        LocalConfiguration provides localizedConfiguration,
        LocalContext provides localizedContext,
        content = content
    )
}

@get:StringRes
val TaskPriority.labelRes: Int
    get() = when (this) {
        TaskPriority.LOW -> R.string.priority_low
        TaskPriority.MEDIUM -> R.string.priority_medium
        TaskPriority.HIGH -> R.string.priority_high
    }

@get:StringRes
val RecurrenceRule.labelRes: Int
    get() = when (this) {
        RecurrenceRule.NONE -> R.string.recurrence_none
        RecurrenceRule.EVERY_FEW_HOURS -> R.string.recurrence_every_few_hours
        RecurrenceRule.DAILY -> R.string.recurrence_daily
        RecurrenceRule.EVERY_2_DAYS -> R.string.recurrence_every_2_days
        RecurrenceRule.WEEKLY -> R.string.recurrence_weekly
    }

@get:StringRes
val ActivityEventType.labelRes: Int
    get() = when (this) {
        ActivityEventType.COMPLETED -> R.string.stats_event_completed
        ActivityEventType.MISSED -> R.string.stats_event_missed
        ActivityEventType.IGNORED -> R.string.stats_event_ignored
        ActivityEventType.SNOOZED -> R.string.stats_event_snoozed
        ActivityEventType.CREATED -> R.string.stats_event_created
    }

@get:StringRes
val ThemePreset.subtitleRes: Int
    get() = when (id) {
        "standard_m3" -> R.string.preset_subtitle_standard
        "winter" -> R.string.preset_subtitle_winter
        "halloween" -> R.string.preset_subtitle_halloween
        "christmas" -> R.string.preset_subtitle_christmas
        "summer" -> R.string.preset_subtitle_summer
        "autumn" -> R.string.preset_subtitle_autumn
        "spring" -> R.string.preset_subtitle_spring
        "easter" -> R.string.preset_subtitle_easter
        "new_year" -> R.string.preset_subtitle_new_year
        "gaming" -> R.string.preset_subtitle_gaming
        "8bit_cute" -> R.string.preset_subtitle_8bit_cute
        "interactive_bubbles" -> R.string.preset_subtitle_bubbles
        "mini_city" -> R.string.preset_subtitle_mini_city
        "weather_time" -> R.string.preset_subtitle_weather_time
        "8bit_cat" -> R.string.preset_subtitle_8bit_cat
        "8bit_fox" -> R.string.preset_subtitle_8bit_fox
        "8bit_bunny" -> R.string.preset_subtitle_8bit_bunny
        "8bit_snake" -> R.string.preset_subtitle_8bit_snake
        else -> R.string.preset_subtitle_standard
    }

fun ThemePreset.localizedTitle(context: android.content.Context): String = when (id) {
    "interactive_bubbles" -> context.getString(R.string.preset_title_bubbles)
    "mini_city" -> context.getString(R.string.preset_title_mini_city)
    "weather_time" -> context.getString(R.string.preset_title_weather_time)
    "8bit_cat" -> context.getString(R.string.preset_title_8bit_cat)
    "8bit_fox" -> context.getString(R.string.preset_title_8bit_fox)
    "8bit_bunny" -> context.getString(R.string.preset_title_8bit_bunny)
    "8bit_snake" -> context.getString(R.string.preset_title_8bit_snake)
    else -> title
}

@get:StringRes
val ThemeCategory.labelRes: Int
    get() = when (this) {
        ThemeCategory.NORMAL -> R.string.appearance_category_normal
        ThemeCategory.EVENTS -> R.string.appearance_category_events
        ThemeCategory.PLAYFUL -> R.string.appearance_category_playful
        ThemeCategory.INTERACTIVE -> R.string.appearance_category_interactive
    }
