package com.snowdos.app.domain.model

enum class TaskStatus {
    OPEN,
    SNOOZED,
    COMPLETED,
    IGNORED,
    MISSED;

    val isActionable: Boolean
        get() = this == OPEN || this == SNOOZED

    val isTerminal: Boolean
        get() = !isActionable
}
