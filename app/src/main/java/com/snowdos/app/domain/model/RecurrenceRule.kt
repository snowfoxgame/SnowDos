package com.snowdos.app.domain.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

enum class RecurrenceRule(
    val badgeLabel: String
) {
    NONE("Einmalig"),
    EVERY_FEW_HOURS("Alle 3 Std."),
    DAILY("Täglich"),
    EVERY_2_DAYS("Alle 2 Tage"),
    WEEKLY("Wöchentlich");

    val isRecurring: Boolean
        get() = this != NONE

    fun nextTimestampMillis(
        anchorMillis: Long?,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long? {
        if (!isRecurring) return null

        var candidate = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(anchorMillis ?: nowMillis),
            zoneId
        )
        do {
            candidate = stepForward(candidate)
        } while (candidate.toInstant().toEpochMilli() <= nowMillis)

        return candidate.toInstant().toEpochMilli()
    }

    /**
     * Computes the next cycle `(dueAtEpochMillis, remindAtEpochMillis)` anchored to the
     * original due timestamp so recurring schedules never drift forward after snoozes.
     */
    fun nextCycleTimestamps(
        dueAtEpochMillis: Long?,
        remindAtEpochMillis: Long?,
        wasSnoozed: Boolean,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Pair<Long?, Long?> {
        if (!isRecurring) return dueAtEpochMillis to remindAtEpochMillis

        if (dueAtEpochMillis == null && remindAtEpochMillis == null) {
            val next = nextTimestampMillis(nowMillis, nowMillis, zoneId)
            return next to next
        }
        val nextDue = dueAtEpochMillis?.let { nextTimestampMillis(it, nowMillis, zoneId) }
        val nextRemind = when {
            remindAtEpochMillis == null -> null
            wasSnoozed && nextDue != null -> nextDue
            dueAtEpochMillis != null && remindAtEpochMillis == dueAtEpochMillis -> nextDue
            else -> nextTimestampMillis(remindAtEpochMillis, nowMillis, zoneId)
        }
        return nextDue to nextRemind
    }

    private fun stepForward(dateTime: ZonedDateTime): ZonedDateTime = when (this) {
        NONE -> dateTime
        EVERY_FEW_HOURS -> dateTime.plusHours(3)
        DAILY -> dateTime.plusDays(1)
        EVERY_2_DAYS -> dateTime.plusDays(2)
        WEEKLY -> dateTime.plusWeeks(1)
    }
}
