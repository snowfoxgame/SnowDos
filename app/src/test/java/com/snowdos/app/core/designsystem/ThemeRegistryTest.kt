package com.snowdos.app.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.snowdos.app.domain.model.EightBitVariant
import com.snowdos.app.domain.model.ThemeAppearanceConfig
import com.snowdos.app.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRegistryTest {

    @Test
    fun `parses valid hex colors and rejects invalid input`() {
        val parsed = parseHexColorOrNull("#ff5722")
        assertNotNull(parsed)
        assertEquals("#FF5722", parsed!!.toHexString())

        assertNotNull(parseHexColorOrNull("6750A4"))
        assertNull(parseHexColorOrNull("#12345"))
        assertNull(parseHexColorOrNull("#ZZZZZZ"))
    }

    @Test
    fun `resolves standard preset and custom accent theme configs`() {
        val presetConfig = ThemeRegistry.resolve(ThemeAppearanceConfig(mode = ThemeMode.PRESET))
        assertTrue(presetConfig is StandardMaterial3ThemeConfig)
        assertEquals("Normal", presetConfig.displayName)

        val customConfig = ThemeRegistry.resolve(
            ThemeAppearanceConfig(
                mode = ThemeMode.CUSTOM,
                customAccentHex = "#1E88E5",
                backgroundImageUri = "file:///tmp/bg.jpg"
            )
        )
        assertTrue(customConfig is CustomAccentThemeConfig)
        assertEquals(Color(0xFF1E88E5), customConfig.colorScheme(darkTheme = false).primary)
        assertTrue(customConfig.tokens(darkTheme = false).surface.cardAlpha < 1f)
    }

    @Test
    fun `registers all 18 presets with expected polarity and pattern flags`() {
        assertEquals(18, ThemeRegistry.presets.size)
        val titles = ThemeRegistry.presets.map { it.title }
        assertEquals(
            listOf(
                "Normal",
                "Winter",
                "Halloween",
                "Christmas",
                "Summer",
                "Autumn",
                "Spring",
                "Easter",
                "New Year",
                "Gaming",
                "8-Bit Cute",
                "Interactive Bubbles",
                "Cutesy Mini-City",
                "Weather & Sky",
                "8-Bit Cat",
                "8-Bit White Fox",
                "8-Bit Bunny",
                "8-Bit Snake"
            ),
            titles
        )

        val normal = ThemeRegistry.findPreset("standard_m3")
        assertTrue(normal.hasPatternBackground)

        val darkPresets = setOf("halloween", "christmas", "autumn", "new_year", "gaming", "interactive_bubbles")
        val specialPresetIds = setOf(
            "gaming",
            "8bit_cute",
            "weather_time",
            "8bit_cat",
            "8bit_fox",
            "8bit_bunny",
            "8bit_snake"
        )
        ThemeRegistry.presets.filter { it.id != "standard_m3" }.forEach { preset ->
            assertTrue(preset.hasPatternBackground)
            val spec = preset.paletteSpec
            assertNotNull(spec)
            assertEquals(preset.id in darkPresets, spec!!.isDark)

            val resolved = ThemeRegistry.resolve(
                ThemeAppearanceConfig(mode = ThemeMode.PRESET, presetId = preset.id)
            )
            if (preset.id !in specialPresetIds) {
                assertTrue(resolved is PresetThemeConfig)
                assertTrue(resolved.tokens(darkTheme = false).surface.cardAlpha < 1f)
            }
            assertEquals(preset.title, resolved.displayName)
        }
    }

    @Test
    fun `gaming preset applies custom LED accent and tinted frosted-glass borders`() {
        val appearance = ThemeAppearanceConfig(
            mode = ThemeMode.PRESET,
            presetId = "gaming",
            gamingAccentHex = "#FF2E93"
        )
        assertTrue(ThemeRegistry.isEffectiveDarkTheme(appearance, systemDarkTheme = false))

        val config = ThemeRegistry.resolve(appearance)
        assertTrue(config is GamingThemeConfig)
        assertEquals(Color(0xFFFF2E93), config.colorScheme(darkTheme = true).primary)

        val tokens = config.tokens(darkTheme = true)
        assertTrue(tokens.surface.glassAccentBorderTint)
        assertFalse(tokens.surface.usePixelRetroStyle)
        assertTrue(tokens.surface.blurRadius > 0.dp)
    }

    @Test
    fun `eight bit cute preset disables blur, switches variants, and uses pixel tokens`() {
        val pinkConfig = ThemeRegistry.resolve(
            ThemeAppearanceConfig(
                mode = ThemeMode.PRESET,
                presetId = "8bit_cute",
                eightBitVariant = EightBitVariant.PASTEL_PINK
            )
        )
        assertTrue(pinkConfig is EightBitCuteThemeConfig)
        assertEquals(Color(0xFFFFEEF4), pinkConfig.colorScheme(darkTheme = false).background)

        val blueConfig = ThemeRegistry.resolve(
            ThemeAppearanceConfig(
                mode = ThemeMode.PRESET,
                presetId = "8bit_cute",
                eightBitVariant = EightBitVariant.PASTEL_BLUE
            )
        )
        assertTrue(blueConfig is EightBitCuteThemeConfig)
        assertEquals(Color(0xFFECF5FF), blueConfig.colorScheme(darkTheme = false).background)

        val tokens = blueConfig.tokens(darkTheme = false)
        assertEquals(0.dp, tokens.surface.blurRadius)
        assertTrue(tokens.surface.usePixelRetroStyle)
        assertEquals(4.dp, tokens.surface.pixelShadowOffsetDp)
        assertEquals(2.dp, tokens.radius.card)
        assertEquals(0.dp, tokens.radius.chip)
        assertEquals(FontFamily.Monospace, blueConfig.typography().bodyLarge.fontFamily)
    }

    @Test
    fun `every preset is assigned to its expected ThemeCategory without duplicates`() {
        val grouped = ThemeRegistry.presets.groupBy({ it.category }, { it.id })
        assertEquals(
            setOf("standard_m3", "spring", "summer", "autumn", "winter"),
            grouped[ThemeCategory.NORMAL]?.toSet()
        )
        assertEquals(
            setOf("easter", "halloween", "christmas", "new_year"),
            grouped[ThemeCategory.EVENTS]?.toSet()
        )
        assertEquals(
            setOf("gaming", "8bit_cute", "mini_city", "8bit_cat", "8bit_fox", "8bit_bunny", "8bit_snake"),
            grouped[ThemeCategory.PLAYFUL]?.toSet()
        )
        assertEquals(
            setOf("interactive_bubbles", "weather_time"),
            grouped[ThemeCategory.INTERACTIVE]?.toSet()
        )
        assertEquals(
            ThemeRegistry.presets.size,
            grouped.values.sumOf { it.size }
        )
    }

    @Test
    fun `four 8bit pixel pet themes activate crisp pixel retro tokens and monospace typography`() {
        listOf("8bit_cat", "8bit_fox", "8bit_bunny", "8bit_snake").forEach { id ->
            val resolved = ThemeRegistry.resolve(
                ThemeAppearanceConfig(mode = ThemeMode.PRESET, presetId = id)
            )
            assertTrue(resolved is EightBitPixelThemeConfig)
            val tokens = resolved.tokens(darkTheme = false)
            assertEquals(0.dp, tokens.surface.blurRadius)
            assertTrue(tokens.surface.usePixelRetroStyle)
            assertEquals(4.dp, tokens.surface.pixelShadowOffsetDp)
            assertEquals(2.dp, tokens.radius.card)
            assertEquals(0.dp, tokens.radius.chip)
            assertEquals(FontFamily.Monospace, resolved.typography().bodyLarge.fontFamily)
        }
    }
}
