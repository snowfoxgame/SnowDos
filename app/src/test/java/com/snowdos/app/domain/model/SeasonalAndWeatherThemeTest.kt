package com.snowdos.app.domain.model

import com.snowdos.app.core.designsystem.ThemeRegistry
import com.snowdos.app.core.designsystem.WeatherTimeThemeConfig
import com.snowdos.app.data.local.weather.OpenMeteoWeatherParser
import com.snowdos.app.platform.sync.PartnerAppConfig
import com.snowdos.app.platform.sync.SnowAppearanceSyncPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SeasonalAndWeatherThemeTest {

    @Test
    fun seasonalThemeResolver_resolvesHolidaysAndSeasonsAccurately() {
        assertEquals("new_year", SeasonalThemeResolver.resolvePresetId(LocalDate.of(2026, 12, 31)))
        assertEquals("new_year", SeasonalThemeResolver.resolvePresetId(LocalDate.of(2026, 1, 3)))
        assertEquals("christmas", SeasonalThemeResolver.resolvePresetId(LocalDate.of(2026, 12, 24)))
        assertEquals("winter", SeasonalThemeResolver.resolvePresetId(LocalDate.of(2026, 12, 28)))
        assertEquals("winter", SeasonalThemeResolver.resolvePresetId(LocalDate.of(2026, 2, 10)))
        assertEquals("halloween", SeasonalThemeResolver.resolvePresetId(LocalDate.of(2026, 10, 31)))

        // 2026 Easter Sunday is April 5; 2025 Easter Sunday is April 20
        assertEquals(LocalDate.of(2026, 4, 5), SeasonalThemeResolver.computeEasterSunday(2026))
        assertEquals(LocalDate.of(2025, 4, 20), SeasonalThemeResolver.computeEasterSunday(2025))
        assertEquals("easter", SeasonalThemeResolver.resolvePresetId(LocalDate.of(2026, 4, 3)))
        assertEquals("easter", SeasonalThemeResolver.resolvePresetId(LocalDate.of(2026, 4, 6)))

        assertEquals("spring", SeasonalThemeResolver.resolvePresetId(LocalDate.of(2026, 5, 10)))
        assertEquals("summer", SeasonalThemeResolver.resolvePresetId(LocalDate.of(2026, 7, 15)))
        assertEquals("autumn", SeasonalThemeResolver.resolvePresetId(LocalDate.of(2026, 10, 12)))
    }

    @Test
    fun wmoWeatherMapper_coversAllSupportedWeatherCodes() {
        assertEquals(WeatherConditionKind.CLEAR, WeatherConditionMapper.fromWmoCode(0))
        assertEquals(WeatherConditionKind.PARTLY_CLOUDY, WeatherConditionMapper.fromWmoCode(1))
        assertEquals(WeatherConditionKind.PARTLY_CLOUDY, WeatherConditionMapper.fromWmoCode(2))
        assertEquals(WeatherConditionKind.OVERCAST, WeatherConditionMapper.fromWmoCode(3))
        assertEquals(WeatherConditionKind.FOG, WeatherConditionMapper.fromWmoCode(45))
        assertEquals(WeatherConditionKind.FOG, WeatherConditionMapper.fromWmoCode(48))
        assertEquals(WeatherConditionKind.DRIZZLE, WeatherConditionMapper.fromWmoCode(51))
        assertEquals(WeatherConditionKind.DRIZZLE, WeatherConditionMapper.fromWmoCode(55))
        assertEquals(WeatherConditionKind.RAIN, WeatherConditionMapper.fromWmoCode(61))
        assertEquals(WeatherConditionKind.RAIN, WeatherConditionMapper.fromWmoCode(80))
        assertEquals(WeatherConditionKind.HEAVY_RAIN, WeatherConditionMapper.fromWmoCode(65))
        assertEquals(WeatherConditionKind.HEAVY_RAIN, WeatherConditionMapper.fromWmoCode(82))
        assertEquals(WeatherConditionKind.FREEZING_RAIN, WeatherConditionMapper.fromWmoCode(66))
        assertEquals(WeatherConditionKind.FREEZING_RAIN, WeatherConditionMapper.fromWmoCode(67))
        assertEquals(WeatherConditionKind.SNOW_LIGHT, WeatherConditionMapper.fromWmoCode(71))
        assertEquals(WeatherConditionKind.SNOW_LIGHT, WeatherConditionMapper.fromWmoCode(85))
        assertEquals(WeatherConditionKind.SNOW_HEAVY, WeatherConditionMapper.fromWmoCode(75))
        assertEquals(WeatherConditionKind.SNOW_HEAVY, WeatherConditionMapper.fromWmoCode(86))
        assertEquals(WeatherConditionKind.THUNDERSTORM_HAIL, WeatherConditionMapper.fromWmoCode(95))
        assertEquals(WeatherConditionKind.THUNDERSTORM_HAIL, WeatherConditionMapper.fromWmoCode(99))
    }

    @Test
    fun celestialArc_andTimeFallback_computeSunriseToSunsetArcLeftToRight() {
        val zone = ZoneId.of("Europe/Berlin")
        val noonMillis = LocalDateTime.of(2026, 6, 15, 13, 0).atZone(zone).toInstant().toEpochMilli()
        val noonSnap = WeatherConditionMapper.resolveTimeFallbackSnapshot(noonMillis, zone)
        assertTrue(noonSnap.isDay)
        assertFalse(noonSnap.isLocationBased)

        val arcStart = WeatherConditionMapper.computeCelestialState(
            nowMillis = noonSnap.sunriseEpochMillis,
            sunriseMillis = noonSnap.sunriseEpochMillis,
            sunsetMillis = noonSnap.sunsetEpochMillis
        )
        assertEquals(0f, arcStart.arcProgress, 0.01f)
        assertTrue(arcStart.isDay)

        val arcMid = WeatherConditionMapper.computeCelestialState(
            nowMillis = (noonSnap.sunriseEpochMillis + noonSnap.sunsetEpochMillis) / 2L,
            sunriseMillis = noonSnap.sunriseEpochMillis,
            sunsetMillis = noonSnap.sunsetEpochMillis
        )
        assertEquals(0.5f, arcMid.arcProgress, 0.01f)
        assertEquals(1f, arcMid.elevationNormalized, 0.02f)

        val arcEnd = WeatherConditionMapper.computeCelestialState(
            nowMillis = noonSnap.sunsetEpochMillis,
            sunriseMillis = noonSnap.sunriseEpochMillis,
            sunsetMillis = noonSnap.sunsetEpochMillis
        )
        assertEquals(1f, arcEnd.arcProgress, 0.01f)

        val midnightMillis = LocalDateTime.of(2026, 6, 15, 23, 30).atZone(zone).toInstant().toEpochMilli()
        val nightSnap = WeatherConditionMapper.resolveTimeFallbackSnapshot(midnightMillis, zone)
        assertFalse(nightSnap.isDay)
    }

    @Test
    fun openMeteoParser_extractsCurrentWeatherCodeAndSunTimes() {
        val sampleJson = """
            {
              "latitude": 52.52,
              "longitude": 13.41,
              "current": { "time": "2026-11-16T14:00", "is_day": 1, "weather_code": 65 },
              "daily": { "sunrise": ["2026-11-16T07:32"], "sunset": ["2026-11-16T16:15"] }
            }
        """.trimIndent()

        val parsed = OpenMeteoWeatherParser.parseResponse(
            json = sampleJson,
            latitude = 52.52,
            longitude = 13.41,
            zoneId = ZoneId.of("Europe/Berlin")
        )
        assertNotNull(parsed)
        assertEquals(65, parsed!!.wmoCode)
        assertEquals(WeatherConditionKind.HEAVY_RAIN, parsed.condition)
        assertTrue(parsed.isDay)
        assertTrue(parsed.isLocationBased)
        assertTrue(parsed.sunsetEpochMillis > parsed.sunriseEpochMillis)

        assertNull(OpenMeteoWeatherParser.parseResponse("{}", 0.0, 0.0))
    }

    @Test
    fun themeRegistryAndSyncPayload_handleNewPresetsAndV1BackwardsCompatibility() {
        val weatherConfig = ThemeRegistry.resolve(
            appearance = ThemeAppearanceConfig(
                mode = ThemeMode.PRESET,
                presetId = "weather_time",
                weatherStyle = WeatherThemeStyle.REALISTIC
            ),
            weatherIsDay = false,
            weatherCondition = WeatherConditionKind.THUNDERSTORM_HAIL
        )
        assertTrue(weatherConfig is WeatherTimeThemeConfig)

        val v1JsonWithoutNewFields = """
            {
              "schemaVersion":1,
              "sourcePackage":"com.snowsplit.app",
              "syncId":"legacy-sync-1",
              "updatedAtEpochMillis":12345,
              "themeMode":"PRESET",
              "presetId":"winter",
              "customAccentHex":"#6750A4",
              "gamingAccentHex":"#00E5FF",
              "eightBitVariant":"PASTEL_PINK",
              "glassOpacity":0.2400,
              "reduceLiveBlur":false,
              "hasCustomBackground":false
            }
        """.trimIndent()
        val parsedV1 = SnowAppearanceSyncPayload.fromJson(v1JsonWithoutNewFields)
        assertNotNull(parsedV1)
        assertEquals(WeatherThemeStyle.CUTE_2D.name, parsedV1!!.weatherStyle)
        assertFalse(parsedV1.autoSeasonalThemeEnabled)

        val newPayload = SnowAppearanceSyncPayload(
            sourcePackage = PartnerAppConfig.OWN_PACKAGE,
            syncId = "new-sync-2",
            updatedAtEpochMillis = 99999L,
            themeMode = ThemeMode.PRESET.name,
            presetId = "weather_time",
            customAccentHex = "#6750A4",
            gamingAccentHex = "#00E5FF",
            eightBitVariant = EightBitVariant.PASTEL_PINK.name,
            glassOpacity = 0.24f,
            reduceLiveBlur = false,
            hasCustomBackground = false,
            weatherStyle = WeatherThemeStyle.REALISTIC.name,
            autoSeasonalThemeEnabled = true
        )
        val roundTripped = SnowAppearanceSyncPayload.fromJson(newPayload.toJson())
        assertNotNull(roundTripped)
        assertEquals("REALISTIC", roundTripped!!.weatherStyle)
        assertTrue(roundTripped.autoSeasonalThemeEnabled)
    }
}
