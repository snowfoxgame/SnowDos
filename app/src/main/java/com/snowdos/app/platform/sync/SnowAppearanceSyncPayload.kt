package com.snowdos.app.platform.sync

import com.snowdos.app.domain.model.EightBitVariant
import com.snowdos.app.domain.model.ThemeAppearanceConfig
import com.snowdos.app.domain.model.ThemeMode
import com.snowdos.app.domain.model.WeatherThemeStyle
import java.util.Locale

data class SnowAppearanceSyncPayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val sourcePackage: String,
    val syncId: String,
    val updatedAtEpochMillis: Long,
    val themeMode: String,
    val presetId: String,
    val customAccentHex: String,
    val gamingAccentHex: String,
    val eightBitVariant: String,
    val glassOpacity: Float,
    val reduceLiveBlur: Boolean,
    val hasCustomBackground: Boolean,
    val customBackgroundHash: String? = null,
    val customBackgroundContentUri: String? = null,
    val weatherStyle: String = WeatherThemeStyle.CUTE_2D.name,
    val autoSeasonalThemeEnabled: Boolean = false
) {
    fun toAppearanceConfig(resolvedBackgroundImageUri: String?): ThemeAppearanceConfig {
        val parsedVariant = runCatching { EightBitVariant.valueOf(eightBitVariant) }.getOrNull()
            ?: EightBitVariant.PASTEL_PINK
        val parsedWeatherStyle = runCatching { WeatherThemeStyle.valueOf(weatherStyle) }.getOrNull()
            ?: WeatherThemeStyle.CUTE_2D
        return ThemeAppearanceConfig(
            mode = ThemeMode.PRESET,
            presetId = presetId.ifBlank { "standard_m3" },
            customAccentHex = customAccentHex.ifBlank { "#6750A4" },
            gamingAccentHex = gamingAccentHex.ifBlank { "#00E5FF" },
            eightBitVariant = parsedVariant,
            weatherStyle = parsedWeatherStyle,
            autoSeasonalThemeEnabled = autoSeasonalThemeEnabled,
            backgroundImageUri = resolvedBackgroundImageUri,
            glassOpacity = glassOpacity.coerceIn(0.10f, 0.55f)
        )
    }

    fun toJson(): String {
        val fields = listOf(
            "schemaVersion" to schemaVersion.toString(),
            "sourcePackage" to jsonString(sourcePackage),
            "syncId" to jsonString(syncId),
            "updatedAtEpochMillis" to updatedAtEpochMillis.toString(),
            "themeMode" to jsonString(themeMode),
            "presetId" to jsonString(presetId),
            "customAccentHex" to jsonString(customAccentHex),
            "gamingAccentHex" to jsonString(gamingAccentHex),
            "eightBitVariant" to jsonString(eightBitVariant),
            "glassOpacity" to String.format(Locale.US, "%.4f", glassOpacity.coerceIn(0.10f, 0.55f)),
            "reduceLiveBlur" to reduceLiveBlur.toString(),
            "hasCustomBackground" to hasCustomBackground.toString(),
            "customBackgroundHash" to (customBackgroundHash?.let(::jsonString) ?: "null"),
            "customBackgroundContentUri" to (customBackgroundContentUri?.let(::jsonString) ?: "null"),
            "weatherStyle" to jsonString(weatherStyle),
            "autoSeasonalThemeEnabled" to autoSeasonalThemeEnabled.toString()
        )
        return fields.joinToString(prefix = "{", postfix = "}", separator = ",") { (k, v) ->
            "${jsonString(k)}:$v"
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        fun fromJson(json: String?): SnowAppearanceSyncPayload? {
            if (json.isNullOrBlank()) return null
            val map = runCatching { SimpleJsonObjectParser.parse(json) }.getOrNull() ?: return null
            val schemaVersion = (map["schemaVersion"] as? Number)?.toInt() ?: return null
            val sourcePackage = (map["sourcePackage"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            val syncId = (map["syncId"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            val updatedAt = (map["updatedAtEpochMillis"] as? Number)?.toLong() ?: return null
            val themeMode = (map["themeMode"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            val presetId = (map["presetId"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            val customAccentHex = (map["customAccentHex"] as? String) ?: "#6750A4"
            val gamingAccentHex = (map["gamingAccentHex"] as? String) ?: "#00E5FF"
            val eightBitVariant = (map["eightBitVariant"] as? String) ?: EightBitVariant.PASTEL_PINK.name
            val glassOpacity = ((map["glassOpacity"] as? Number)?.toFloat() ?: 0.24f).coerceIn(0.10f, 0.55f)
            val reduceLiveBlur = (map["reduceLiveBlur"] as? Boolean) ?: false
            val hasCustomBackground = (map["hasCustomBackground"] as? Boolean) ?: false
            val customBackgroundHash = (map["customBackgroundHash"] as? String)?.takeIf { it.isNotBlank() }
            val customBackgroundContentUri = (map["customBackgroundContentUri"] as? String)?.takeIf { it.isNotBlank() }
            val weatherStyle = (map["weatherStyle"] as? String)?.takeIf { it.isNotBlank() }
                ?: WeatherThemeStyle.CUTE_2D.name
            val autoSeasonal = (map["autoSeasonalThemeEnabled"] as? Boolean) ?: false

            return SnowAppearanceSyncPayload(
                schemaVersion = schemaVersion,
                sourcePackage = sourcePackage,
                syncId = syncId,
                updatedAtEpochMillis = updatedAt,
                themeMode = themeMode,
                presetId = presetId,
                customAccentHex = customAccentHex,
                gamingAccentHex = gamingAccentHex,
                eightBitVariant = eightBitVariant,
                glassOpacity = glassOpacity,
                reduceLiveBlur = reduceLiveBlur,
                hasCustomBackground = hasCustomBackground,
                customBackgroundHash = customBackgroundHash,
                customBackgroundContentUri = customBackgroundContentUri,
                weatherStyle = weatherStyle,
                autoSeasonalThemeEnabled = autoSeasonal
            )
        }

        private fun jsonString(value: String): String = buildString(value.length + 2) {
            append('"')
            for (ch in value) {
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (ch.code < 0x20) {
                            append(String.format(Locale.US, "\\u%04x", ch.code))
                        } else {
                            append(ch)
                        }
                    }
                }
            }
            append('"')
        }
    }
}

enum class BackgroundSyncAction {
    NO_CHANGE,
    COPY_FROM_PARTNER,
    CLEAR_LOCAL
}

sealed interface SyncDecision {
    data class Apply(val backgroundAction: BackgroundSyncAction) : SyncDecision
    data class Ignore(val reason: RejectReason) : SyncDecision
}

enum class RejectReason {
    UNSUPPORTED_SCHEMA,
    SYNC_DISABLED,
    PARTNER_NOT_CONNECTED,
    OWN_OR_UNTRUSTED_SOURCE,
    DUPLICATE_SYNC_ID,
    STALE_REVISION,
    IDENTICAL_STATE
}

object ThemeSyncGuards {
    fun evaluateIncoming(
        payload: SnowAppearanceSyncPayload,
        ownPackage: String = PartnerAppConfig.OWN_PACKAGE,
        expectedPartnerPackage: String = PartnerAppConfig.PARTNER_PACKAGE,
        crossAppSyncEnabled: Boolean,
        partnerAppConnected: Boolean,
        lastAppliedSyncId: String,
        lastSyncedRevision: Long,
        currentAppearance: ThemeAppearanceConfig? = null,
        currentReduceLiveBlur: Boolean? = null,
        localBackgroundHash: String? = null
    ): SyncDecision {
        if (payload.schemaVersion != SnowAppearanceSyncPayload.CURRENT_SCHEMA_VERSION) {
            return SyncDecision.Ignore(RejectReason.UNSUPPORTED_SCHEMA)
        }
        if (!crossAppSyncEnabled) {
            return SyncDecision.Ignore(RejectReason.SYNC_DISABLED)
        }
        if (!partnerAppConnected) {
            return SyncDecision.Ignore(RejectReason.PARTNER_NOT_CONNECTED)
        }
        if (payload.sourcePackage == ownPackage || payload.sourcePackage != expectedPartnerPackage) {
            return SyncDecision.Ignore(RejectReason.OWN_OR_UNTRUSTED_SOURCE)
        }
        if (payload.syncId.isBlank() || payload.syncId == lastAppliedSyncId) {
            return SyncDecision.Ignore(RejectReason.DUPLICATE_SYNC_ID)
        }
        if (payload.updatedAtEpochMillis <= lastSyncedRevision) {
            return SyncDecision.Ignore(RejectReason.STALE_REVISION)
        }

        val backgroundAction = resolveBackgroundSyncAction(
            hasCustomBackground = payload.hasCustomBackground,
            remoteBackgroundHash = payload.customBackgroundHash,
            remoteContentUri = payload.customBackgroundContentUri,
            localBackgroundUri = currentAppearance?.backgroundImageUri,
            localBackgroundHash = localBackgroundHash
        )

        if (currentAppearance != null && currentReduceLiveBlur != null) {
            val sameVisualAppearance =
                currentAppearance.mode.name == payload.themeMode &&
                    currentAppearance.effectivePresetId() == payload.presetId &&
                    currentAppearance.customAccentHex.equals(payload.customAccentHex, ignoreCase = true) &&
                    currentAppearance.gamingAccentHex.equals(payload.gamingAccentHex, ignoreCase = true) &&
                    currentAppearance.eightBitVariant.name == payload.eightBitVariant &&
                    currentAppearance.weatherStyle.name == payload.weatherStyle &&
                    currentAppearance.autoSeasonalThemeEnabled == payload.autoSeasonalThemeEnabled &&
                    kotlin.math.abs(currentAppearance.glassOpacity - payload.glassOpacity) < 0.005f &&
                    currentReduceLiveBlur == payload.reduceLiveBlur &&
                    backgroundAction == BackgroundSyncAction.NO_CHANGE

            if (sameVisualAppearance) {
                return SyncDecision.Ignore(RejectReason.IDENTICAL_STATE)
            }
        }

        return SyncDecision.Apply(backgroundAction = backgroundAction)
    }

    internal fun resolveBackgroundSyncAction(
        hasCustomBackground: Boolean,
        remoteBackgroundHash: String?,
        remoteContentUri: String?,
        localBackgroundUri: String?,
        localBackgroundHash: String?
    ): BackgroundSyncAction {
        if (!hasCustomBackground) {
            return if (!localBackgroundUri.isNullOrBlank() || !localBackgroundHash.isNullOrBlank()) {
                BackgroundSyncAction.CLEAR_LOCAL
            } else {
                BackgroundSyncAction.NO_CHANGE
            }
        }
        if (remoteContentUri.isNullOrBlank()) {
            return BackgroundSyncAction.NO_CHANGE
        }
        val hashMatches = !remoteBackgroundHash.isNullOrBlank() &&
            remoteBackgroundHash == localBackgroundHash &&
            !localBackgroundUri.isNullOrBlank()
        return if (hashMatches) BackgroundSyncAction.NO_CHANGE else BackgroundSyncAction.COPY_FROM_PARTNER
    }
}

/**
 * Minimal self-contained JSON object parser (no Android `org.json` dependency so pure JVM unit
 * tests work cleanly without Robolectric/stub mocking).
 */
private class SimpleJsonObjectParser private constructor(private val src: String) {
    private var idx = 0

    companion object {
        fun parse(json: String): Map<String, Any?> {
            val parser = SimpleJsonObjectParser(json)
            parser.skipWhitespace()
            val obj = parser.readObject()
            parser.skipWhitespace()
            return obj
        }
    }

    private fun readObject(): Map<String, Any?> {
        expect('{')
        val result = linkedMapOf<String, Any?>()
        skipWhitespace()
        if (peek() == '}') {
            idx++
            return result
        }
        while (true) {
            skipWhitespace()
            val key = readString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            val value = readValue()
            result[key] = value
            skipWhitespace()
            when (peek()) {
                ',' -> idx++
                '}' -> {
                    idx++
                    return result
                }
                else -> error("Expected ',' or '}' at index $idx")
            }
        }
    }

    private fun readValue(): Any? {
        return when (val ch = peek()) {
            '"' -> readString()
            't' -> readLiteral("true", true)
            'f' -> readLiteral("false", false)
            'n' -> readLiteral("null", null)
            '-', in '0'..'9' -> readNumber()
            else -> error("Unexpected token '$ch' at index $idx")
        }
    }

    private fun readString(): String {
        expect('"')
        val sb = StringBuilder()
        while (idx < src.length) {
            val ch = src[idx++]
            when (ch) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (idx >= src.length) error("Unterminated escape")
                    when (val esc = src[idx++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            val hex = src.substring(idx, idx + 4)
                            idx += 4
                            sb.append(hex.toInt(16).toChar())
                        }
                        else -> error("Unknown escape '\\$esc'")
                    }
                }
                else -> sb.append(ch)
            }
        }
        error("Unterminated string")
    }

    private fun readNumber(): Number {
        val start = idx
        var isFloat = false
        if (src[idx] == '-') idx++
        while (idx < src.length) {
            val ch = src[idx]
            if (ch in '0'..'9') {
                idx++
            } else if (ch == '.' || ch == 'e' || ch == 'E' || ch == '+' || ch == '-') {
                if (ch == '.' || ch == 'e' || ch == 'E') isFloat = true
                idx++
            } else {
                break
            }
        }
        val raw = src.substring(start, idx)
        return if (isFloat) raw.toDouble() else raw.toLong()
    }

    private fun <T> readLiteral(literal: String, value: T): T {
        if (!src.startsWith(literal, idx)) error("Expected $literal at $idx")
        idx += literal.length
        return value
    }

    private fun expect(expected: Char) {
        if (idx >= src.length || src[idx] != expected) {
            error("Expected '$expected' at index $idx")
        }
        idx++
    }

    private fun peek(): Char {
        if (idx >= src.length) error("Unexpected EOF at $idx")
        return src[idx]
    }

    private fun skipWhitespace() {
        while (idx < src.length && src[idx].isWhitespace()) idx++
    }
}
