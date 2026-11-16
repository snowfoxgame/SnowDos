package com.snowdos.app.feature.tutorial

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.snowdos.app.R
import com.snowdos.app.feature.navigation.TopLevelDestination

enum class TutorialTargetId {
    ADD_TASK,
    SEARCH_AND_FILTER,
    TASK_ACTIONS,
    STATS_OVERVIEW,
    SETTINGS_HIGHLIGHTS
}

enum class TutorialStep(
    val targetId: TutorialTargetId,
    val destination: TopLevelDestination,
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
    val cutoutPaddingDp: Int = 8,
    val isCircularCutout: Boolean = false
) {
    ADD_TASK(
        targetId = TutorialTargetId.ADD_TASK,
        destination = TopLevelDestination.TASKS,
        titleRes = R.string.tutorial_step_add_title,
        bodyRes = R.string.tutorial_step_add_desc,
        cutoutPaddingDp = 10,
        isCircularCutout = true
    ),
    SEARCH_AND_FILTER(
        targetId = TutorialTargetId.SEARCH_AND_FILTER,
        destination = TopLevelDestination.TASKS,
        titleRes = R.string.tutorial_step_search_title,
        bodyRes = R.string.tutorial_step_search_desc,
        cutoutPaddingDp = 8
    ),
    TASK_ACTIONS(
        targetId = TutorialTargetId.TASK_ACTIONS,
        destination = TopLevelDestination.TASKS,
        titleRes = R.string.tutorial_step_actions_title,
        bodyRes = R.string.tutorial_step_actions_desc,
        cutoutPaddingDp = 8
    ),
    STATS_OVERVIEW(
        targetId = TutorialTargetId.STATS_OVERVIEW,
        destination = TopLevelDestination.STATS,
        titleRes = R.string.tutorial_step_stats_title,
        bodyRes = R.string.tutorial_step_stats_desc,
        cutoutPaddingDp = 8
    ),
    SETTINGS_HIGHLIGHTS(
        targetId = TutorialTargetId.SETTINGS_HIGHLIGHTS,
        destination = TopLevelDestination.SETTINGS,
        titleRes = R.string.tutorial_step_settings_title,
        bodyRes = R.string.tutorial_step_settings_desc,
        cutoutPaddingDp = 8
    );

    companion object {
        val ordered: List<TutorialStep> = entries
    }
}

/**
 * Tracks root-space bounding boxes for tutorial spotlight targets rendered inside [HorizontalPager]
 * and chrome overlays (such as the floating `+` button).
 */
@Stable
class TutorialTargetRegistry {
    private val targets = mutableStateMapOf<TutorialTargetId, Rect>()
    var isEnabled by mutableStateOf(false)

    fun update(id: TutorialTargetId, boundsInRoot: Rect) {
        if (!isEnabled) return
        if (boundsInRoot.width > 0f && boundsInRoot.height > 0f) {
            targets[id] = boundsInRoot
        }
    }

    fun get(id: TutorialTargetId): Rect? = targets[id]
}

val LocalTutorialTargetRegistry = compositionLocalOf<TutorialTargetRegistry?> { null }

/**
 * Registers this composable's root bounds in [LocalTutorialTargetRegistry] so the interactive
 * spotlight tutorial can punch a cutout and anchor an explanation card near it.
 * Disabled completely when no tutorial is active so Pager swipes incur zero layout callbacks.
 */
fun Modifier.tutorialTarget(id: TutorialTargetId): Modifier = composed {
    val registry = LocalTutorialTargetRegistry.current
    if (registry == null || !registry.isEnabled) {
        this
    } else {
        onGloballyPositioned { coords ->
            if (coords.isAttached) {
                registry.update(id, coords.boundsInRoot())
            }
        }
    }
}
