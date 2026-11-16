package com.snowdos.app.feature.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.snowdos.app.R
import com.snowdos.app.core.designsystem.components.FloatingNavIconAnimation
import com.snowdos.app.domain.model.AccountTabVisibility

enum class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val iconAnimation: FloatingNavIconAnimation
) {
    STATS(
        route = "stats",
        labelRes = R.string.nav_stats,
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
        iconAnimation = FloatingNavIconAnimation.STATS_BARS
    ),
    TASKS(
        route = "tasks",
        labelRes = R.string.nav_tasks,
        selectedIcon = Icons.Filled.CheckCircle,
        unselectedIcon = Icons.Outlined.CheckCircle,
        iconAnimation = FloatingNavIconAnimation.TASKS_CHECKMARK
    ),
    ACCOUNT(
        route = "account",
        labelRes = R.string.nav_account,
        selectedIcon = Icons.Filled.AccountCircle,
        unselectedIcon = Icons.Outlined.AccountCircle,
        iconAnimation = FloatingNavIconAnimation.ACCOUNT_NOD
    ),
    SETTINGS(
        route = "settings",
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        iconAnimation = FloatingNavIconAnimation.SETTINGS_SPIN
    )
}

object TopLevelNavPolicy {
    const val AUTO_MIN_SCREEN_WIDTH_DP = 380
    const val AUTO_MAX_FONT_SCALE = 1.15f

    fun isAccountTabVisibleInNav(
        visibility: AccountTabVisibility,
        screenWidthDp: Int,
        fontScale: Float
    ): Boolean = when (visibility) {
        AccountTabVisibility.AUTO ->
            screenWidthDp >= AUTO_MIN_SCREEN_WIDTH_DP && fontScale <= AUTO_MAX_FONT_SCALE
        AccountTabVisibility.ALWAYS_SHOW -> true
        AccountTabVisibility.MOVE_TO_SETTINGS -> false
    }

    fun activeDestinations(showAccountInNav: Boolean): List<TopLevelDestination> =
        if (showAccountInNav) {
            TopLevelDestination.entries
        } else {
            TopLevelDestination.entries.filter { it != TopLevelDestination.ACCOUNT }
        }

    fun resolveTargetIndex(
        previousDestination: TopLevelDestination?,
        newDestinations: List<TopLevelDestination>
    ): Int {
        if (newDestinations.isEmpty()) return 0
        val mapped = previousDestination?.let { newDestinations.indexOf(it) } ?: -1
        if (mapped >= 0) return mapped
        val homeIndex = newDestinations.indexOf(TopLevelDestination.TASKS)
        return if (homeIndex >= 0) homeIndex else 0
    }
}
