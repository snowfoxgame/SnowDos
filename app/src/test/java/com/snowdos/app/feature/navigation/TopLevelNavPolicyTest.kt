package com.snowdos.app.feature.navigation

import com.snowdos.app.domain.model.AccountTabVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelNavPolicyTest {

    @Test
    fun `auto visibility respects width and fontScale thresholds`() {
        assertFalse(
            TopLevelNavPolicy.isAccountTabVisibleInNav(
                visibility = AccountTabVisibility.AUTO,
                screenWidthDp = 359,
                fontScale = 1.0f
            )
        )
        assertFalse(
            TopLevelNavPolicy.isAccountTabVisibleInNav(
                visibility = AccountTabVisibility.AUTO,
                screenWidthDp = 379,
                fontScale = 1.0f
            )
        )
        assertTrue(
            TopLevelNavPolicy.isAccountTabVisibleInNav(
                visibility = AccountTabVisibility.AUTO,
                screenWidthDp = 380,
                fontScale = 1.15f
            )
        )
        assertFalse(
            TopLevelNavPolicy.isAccountTabVisibleInNav(
                visibility = AccountTabVisibility.AUTO,
                screenWidthDp = 420,
                fontScale = 1.2f
            )
        )
    }

    @Test
    fun `explicit visibility overrides display heuristics`() {
        assertTrue(
            TopLevelNavPolicy.isAccountTabVisibleInNav(
                visibility = AccountTabVisibility.ALWAYS_SHOW,
                screenWidthDp = 320,
                fontScale = 1.3f
            )
        )
        assertFalse(
            TopLevelNavPolicy.isAccountTabVisibleInNav(
                visibility = AccountTabVisibility.MOVE_TO_SETTINGS,
                screenWidthDp = 800,
                fontScale = 0.85f
            )
        )
    }

    @Test
    fun `activeDestinations and resolveTargetIndex keep home and remap safely between 3 and 4 tabs`() {
        val fourTabs = TopLevelNavPolicy.activeDestinations(showAccountInNav = true)
        val threeTabs = TopLevelNavPolicy.activeDestinations(showAccountInNav = false)

        assertEquals(
            listOf(
                TopLevelDestination.STATS,
                TopLevelDestination.TASKS,
                TopLevelDestination.ACCOUNT,
                TopLevelDestination.SETTINGS
            ),
            fourTabs
        )
        assertEquals(
            listOf(
                TopLevelDestination.STATS,
                TopLevelDestination.TASKS,
                TopLevelDestination.SETTINGS
            ),
            threeTabs
        )

        // Switching from 4 to 3 tabs while on SETTINGS maps cleanly from index 3 -> 2.
        assertEquals(2, TopLevelNavPolicy.resolveTargetIndex(TopLevelDestination.SETTINGS, threeTabs))
        // Switching back from 3 to 4 tabs while on SETTINGS maps cleanly from index 2 -> 3.
        assertEquals(3, TopLevelNavPolicy.resolveTargetIndex(TopLevelDestination.SETTINGS, fourTabs))
        // When ACCOUNT disappears from nav bar, fallback lands on TASKS (home tab).
        assertEquals(1, TopLevelNavPolicy.resolveTargetIndex(TopLevelDestination.ACCOUNT, threeTabs))
        // Start destination TASKS stays index 1 in both layouts (centered in 3-tab layout).
        assertEquals(1, fourTabs.indexOf(TopLevelDestination.TASKS))
        assertEquals(1, threeTabs.indexOf(TopLevelDestination.TASKS))
    }
}
