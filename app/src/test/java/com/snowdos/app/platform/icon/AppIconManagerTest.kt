package com.snowdos.app.platform.icon

import android.content.pm.PackageManager
import com.snowdos.app.core.designsystem.ThemeRegistry
import com.snowdos.app.domain.model.AppSettings
import com.snowdos.app.domain.model.ThemeAppearanceConfig
import com.snowdos.app.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconManagerTest {

    @Test
    fun `dedicated icon presets match registered presets and fallback presets resolve to normal`() {
        val registeredIds = ThemeRegistry.presets.map { it.id }.toSet()
        AppIconPreset.entries.forEach { alias ->
            assertTrue(alias.presetId in registeredIds)
        }
        assertEquals(AppIconPreset.entries.size, AppIconPreset.entries.map { it.aliasClassName }.distinct().size)

        listOf("gaming", "8bit_cute", "8bit_cat", "8bit_fox", "8bit_bunny", "8bit_snake").forEach { specialId ->
            val resolved = AppIconPreset.resolveTarget(
                AppSettings(
                    dynamicAppIconEnabled = true,
                    appearance = ThemeAppearanceConfig(
                        mode = ThemeMode.PRESET,
                        presetId = specialId
                    )
                )
            )
            assertEquals(AppIconPreset.NORMAL, resolved)
        }
    }

    @Test
    fun `resolveTarget falls back to normal icon for custom mode or disabled setting`() {
        val customTarget = AppIconPreset.resolveTarget(
            AppSettings(
                dynamicAppIconEnabled = true,
                appearance = ThemeAppearanceConfig(
                    mode = ThemeMode.CUSTOM,
                    presetId = "winter"
                )
            )
        )
        assertEquals(AppIconPreset.NORMAL, customTarget)

        val disabledTarget = AppIconPreset.resolveTarget(
            AppSettings(
                dynamicAppIconEnabled = false,
                appearance = ThemeAppearanceConfig(
                    mode = ThemeMode.PRESET,
                    presetId = "christmas"
                )
            )
        )
        assertEquals(AppIconPreset.NORMAL, disabledTarget)
    }

    @Test
    fun `syncLauncherAlias always enables target before disabling previous alias with DONT_KILL_APP`() {
        val states = mutableMapOf<String, Int>().apply {
            for (preset in AppIconPreset.entries) {
                put(preset.aliasClassName, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            }
        }
        val calls = mutableListOf<Triple<String, Int, Int>>()

        val fakeController = object : AliasComponentController {
            override fun getEnabledState(aliasClassName: String): Int =
                states[aliasClassName] ?: PackageManager.COMPONENT_ENABLED_STATE_DEFAULT

            override fun setEnabledState(aliasClassName: String, newState: Int, flags: Int) {
                calls.add(Triple(aliasClassName, newState, flags))
                states[aliasClassName] = newState
                // Invariant: at least one alias must remain effectively enabled at every step
                val activeCount = AppIconPreset.entries.count { alias ->
                    when (states[alias.aliasClassName]) {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> alias.defaultEnabled
                        else -> false
                    }
                }
                assertTrue("At least one launcher alias must stay active", activeCount >= 1)
            }
        }

        AppIconManager.syncLauncherAlias(target = AppIconPreset.WINTER, controller = fakeController)

        assertEquals(2, calls.size)
        assertEquals(
            Triple(
                AppIconPreset.WINTER.aliasClassName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            ),
            calls[0]
        )
        assertEquals(
            Triple(
                AppIconPreset.NORMAL.aliasClassName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            ),
            calls[1]
        )

        // Second sync with same target should do zero IPC writes
        calls.clear()
        AppIconManager.syncLauncherAlias(target = AppIconPreset.WINTER, controller = fakeController)
        assertTrue(calls.isEmpty())
    }
}
