package com.snowdos.app.core.common

import com.snowdos.app.domain.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DateTimeFormattingTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val referenceDate: LocalDate = LocalDate.of(2026, 6, 15)

    private fun millisAt(date: LocalDate, hour: Int, minute: Int): Long =
        LocalDateTime.of(date.year, date.month, date.dayOfMonth, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun formatCompactDateTime_usesLocalizedTodayTomorrowAndDates() {
        val todayMillis = millisAt(referenceDate, 9, 30)
        val tomorrowMillis = millisAt(referenceDate.plusDays(1), 18, 45)
        val futureMillis = millisAt(referenceDate.plusDays(5), 14, 5)

        assertEquals(
            "Heute 09:30",
            formatCompactDateTime(
                epochMillis = todayMillis,
                language = AppLanguage.GERMAN,
                zoneId = zone,
                referenceDate = referenceDate
            )
        )
        assertEquals(
            "Today 09:30",
            formatCompactDateTime(
                epochMillis = todayMillis,
                language = AppLanguage.ENGLISH,
                zoneId = zone,
                referenceDate = referenceDate
            )
        )

        assertEquals(
            "Morgen 18:45",
            formatCompactDateTime(
                epochMillis = tomorrowMillis,
                language = AppLanguage.GERMAN,
                zoneId = zone,
                referenceDate = referenceDate
            )
        )
        assertEquals(
            "Tomorrow 18:45",
            formatCompactDateTime(
                epochMillis = tomorrowMillis,
                language = AppLanguage.ENGLISH,
                zoneId = zone,
                referenceDate = referenceDate
            )
        )

        assertEquals(
            "20.06. 14:05",
            formatCompactDateTime(
                epochMillis = futureMillis,
                language = AppLanguage.GERMAN,
                zoneId = zone,
                referenceDate = referenceDate
            )
        )
        assertEquals(
            "Jun 20, 14:05",
            formatCompactDateTime(
                epochMillis = futureMillis,
                language = AppLanguage.ENGLISH,
                zoneId = zone,
                referenceDate = referenceDate
            )
        )
    }
}
