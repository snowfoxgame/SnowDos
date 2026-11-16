package com.snowdos.app.domain.strategy

/**
 * Triggers a refresh of active home screen widgets when tasks, scores, language, or widget settings change.
 */
interface WidgetUpdateTrigger {
    suspend fun updateAll()
}

object NoOpWidgetUpdateTrigger : WidgetUpdateTrigger {
    override suspend fun updateAll() = Unit
}
