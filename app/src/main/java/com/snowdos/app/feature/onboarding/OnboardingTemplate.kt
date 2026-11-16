package com.snowdos.app.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Shower
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import com.snowdos.app.R
import com.snowdos.app.domain.model.RecurrenceRule
import com.snowdos.app.domain.model.TaskPriority
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class OnboardingCategory(@param:StringRes val titleRes: Int) {
    BODY_DAILY(R.string.onboarding_cat_body_daily),
    ORDER(R.string.onboarding_cat_order),
    FOCUS_DUTIES(R.string.onboarding_cat_focus_duties)
}

data class OnboardingTaskTemplate(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val notesRes: Int,
    val category: OnboardingCategory,
    val recurrence: RecurrenceRule,
    val priority: TaskPriority,
    val defaultHour: Int,
    val defaultMinute: Int,
    val icon: ImageVector
) {
    fun computeInitialTriggerMillis(
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        if (recurrence == RecurrenceRule.EVERY_FEW_HOURS) {
            return nowMillis + 3 * 60 * 60_000L
        }
        val nowZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        val todayTarget = nowZdt.toLocalDate()
            .atTime(LocalTime.of(defaultHour, defaultMinute))
            .atZone(zoneId)
        val scheduled = if (todayTarget.toInstant().toEpochMilli() > nowMillis) {
            todayTarget
        } else {
            todayTarget.plusDays(1)
        }
        return scheduled.toInstant().toEpochMilli()
    }
}

object OnboardingTemplateCatalog {
    val templates: List<OnboardingTaskTemplate> = listOf(
        // Body & Daily Care
        OnboardingTaskTemplate(
            id = "shower",
            titleRes = R.string.template_shower_title,
            notesRes = R.string.template_shower_notes,
            category = OnboardingCategory.BODY_DAILY,
            recurrence = RecurrenceRule.EVERY_2_DAYS,
            priority = TaskPriority.MEDIUM,
            defaultHour = 19,
            defaultMinute = 0,
            icon = Icons.Outlined.Shower
        ),
        OnboardingTaskTemplate(
            id = "meals",
            titleRes = R.string.template_meals_title,
            notesRes = R.string.template_meals_notes,
            category = OnboardingCategory.BODY_DAILY,
            recurrence = RecurrenceRule.DAILY,
            priority = TaskPriority.MEDIUM,
            defaultHour = 12,
            defaultMinute = 30,
            icon = Icons.Outlined.Restaurant
        ),
        OnboardingTaskTemplate(
            id = "hydrate",
            titleRes = R.string.template_hydrate_title,
            notesRes = R.string.template_hydrate_notes,
            category = OnboardingCategory.BODY_DAILY,
            recurrence = RecurrenceRule.EVERY_FEW_HOURS,
            priority = TaskPriority.LOW,
            defaultHour = 10,
            defaultMinute = 0,
            icon = Icons.Outlined.WaterDrop
        ),
        OnboardingTaskTemplate(
            id = "brush_teeth",
            titleRes = R.string.template_brush_teeth_title,
            notesRes = R.string.template_brush_teeth_notes,
            category = OnboardingCategory.BODY_DAILY,
            recurrence = RecurrenceRule.DAILY,
            priority = TaskPriority.HIGH,
            defaultHour = 21,
            defaultMinute = 30,
            icon = Icons.Outlined.Spa
        ),
        OnboardingTaskTemplate(
            id = "bedtime",
            titleRes = R.string.template_bedtime_title,
            notesRes = R.string.template_bedtime_notes,
            category = OnboardingCategory.BODY_DAILY,
            recurrence = RecurrenceRule.DAILY,
            priority = TaskPriority.HIGH,
            defaultHour = 22,
            defaultMinute = 30,
            icon = Icons.Outlined.Bedtime
        ),

        // Home & Order
        OnboardingTaskTemplate(
            id = "tidy_up",
            titleRes = R.string.template_tidy_up_title,
            notesRes = R.string.template_tidy_up_notes,
            category = OnboardingCategory.ORDER,
            recurrence = RecurrenceRule.DAILY,
            priority = TaskPriority.MEDIUM,
            defaultHour = 18,
            defaultMinute = 30,
            icon = Icons.Outlined.CleaningServices
        ),
        OnboardingTaskTemplate(
            id = "laundry_chores",
            titleRes = R.string.template_laundry_title,
            notesRes = R.string.template_laundry_notes,
            category = OnboardingCategory.ORDER,
            recurrence = RecurrenceRule.WEEKLY,
            priority = TaskPriority.MEDIUM,
            defaultHour = 11,
            defaultMinute = 0,
            icon = Icons.Outlined.LocalLaundryService
        ),
        OnboardingTaskTemplate(
            id = "trash_out",
            titleRes = R.string.template_trash_title,
            notesRes = R.string.template_trash_notes,
            category = OnboardingCategory.ORDER,
            recurrence = RecurrenceRule.WEEKLY,
            priority = TaskPriority.HIGH,
            defaultHour = 19,
            defaultMinute = 0,
            icon = Icons.Outlined.DeleteOutline
        ),

        // Focus & Goals
        OnboardingTaskTemplate(
            id = "study_homework",
            titleRes = R.string.template_study_title,
            notesRes = R.string.template_study_notes,
            category = OnboardingCategory.FOCUS_DUTIES,
            recurrence = RecurrenceRule.DAILY,
            priority = TaskPriority.HIGH,
            defaultHour = 16,
            defaultMinute = 0,
            icon = Icons.AutoMirrored.Outlined.MenuBook
        ),
        OnboardingTaskTemplate(
            id = "project_focus",
            titleRes = R.string.template_project_title,
            notesRes = R.string.template_project_notes,
            category = OnboardingCategory.FOCUS_DUTIES,
            recurrence = RecurrenceRule.DAILY,
            priority = TaskPriority.MEDIUM,
            defaultHour = 14,
            defaultMinute = 0,
            icon = Icons.Outlined.Timer
        ),
        OnboardingTaskTemplate(
            id = "daily_plan_check",
            titleRes = R.string.template_daily_plan_title,
            notesRes = R.string.template_daily_plan_notes,
            category = OnboardingCategory.FOCUS_DUTIES,
            recurrence = RecurrenceRule.DAILY,
            priority = TaskPriority.MEDIUM,
            defaultHour = 9,
            defaultMinute = 0,
            icon = Icons.Outlined.Checklist
        ),
        OnboardingTaskTemplate(
            id = "movement_break",
            titleRes = R.string.template_movement_title,
            notesRes = R.string.template_movement_notes,
            category = OnboardingCategory.FOCUS_DUTIES,
            recurrence = RecurrenceRule.DAILY,
            priority = TaskPriority.LOW,
            defaultHour = 15,
            defaultMinute = 0,
            icon = Icons.AutoMirrored.Outlined.DirectionsRun
        )
    )
}
