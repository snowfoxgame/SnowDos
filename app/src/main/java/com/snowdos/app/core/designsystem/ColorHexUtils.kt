package com.snowdos.app.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Parses a 6-digit RGB hex string (with or without leading '#') into a Compose [Color].
 * Returns null if the input is not a valid 6-digit hex color.
 */
fun parseHexColorOrNull(raw: String): Color? {
    val cleaned = raw.trim().removePrefix("#")
    if (cleaned.length != 6) return null
    val rgb = cleaned.toLongOrNull(16) ?: return null
    return Color((0xFF000000L or rgb).toInt())
}

/**
 * Formats a Compose [Color] as uppercase `#RRGGBB`.
 */
fun Color.toHexString(): String {
    val rgb = toArgb() and 0x00FFFFFF
    return "#%06X".format(rgb)
}
