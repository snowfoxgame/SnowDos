package com.snowdos.app.core.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.snowdos.app.R
import com.snowdos.app.domain.model.AppLanguage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun formatCompactDateTime(
    epochMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): String {
    val language = LocalAppLanguage.current
    val todayLabel = stringResource(R.string.date_today)
    val tomorrowLabel = stringResource(R.string.date_tomorrow)
    return remember(epochMillis, language, zoneId, todayLabel, tomorrowLabel) {
        formatCompactDateTime(
            epochMillis = epochMillis,
            language = language,
            todayLabel = todayLabel,
            tomorrowLabel = tomorrowLabel,
            zoneId = zoneId
        )
    }
}

private val TIME_FORMATTER_DE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
private val TIME_FORMATTER_EN: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val DATE_TIME_FORMATTER_DE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM. HH:mm", Locale.GERMAN)
private val DATE_TIME_FORMATTER_EN: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH)

fun formatCompactDateTime(
    epochMillis: Long,
    language: AppLanguage,
    todayLabel: String = if (language == AppLanguage.GERMAN) "Heute" else "Today",
    tomorrowLabel: String = if (language == AppLanguage.GERMAN) "Morgen" else "Tomorrow",
    zoneId: ZoneId = ZoneId.systemDefault(),
    referenceDate: LocalDate = LocalDate.now(zoneId)
): String {
    val zoned = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
    val timeFormatter = if (language == AppLanguage.GERMAN) TIME_FORMATTER_DE else TIME_FORMATTER_EN
    val dateTimeFormatter = if (language == AppLanguage.GERMAN) DATE_TIME_FORMATTER_DE else DATE_TIME_FORMATTER_EN
    return when (zoned.toLocalDate()) {
        referenceDate -> "$todayLabel ${zoned.format(timeFormatter)}"
        referenceDate.plusDays(1) -> "$tomorrowLabel ${zoned.format(timeFormatter)}"
        else -> zoned.format(dateTimeFormatter)
    }
}
