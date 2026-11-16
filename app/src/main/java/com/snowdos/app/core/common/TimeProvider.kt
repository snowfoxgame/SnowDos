package com.snowdos.app.core.common

/**
 * Abstraction over wall-clock time so domain logic and reminders stay deterministic in unit tests.
 */
interface TimeProvider {
    fun nowEpochMillis(): Long
}

class SystemTimeProvider : TimeProvider {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
