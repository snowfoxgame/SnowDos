package com.snowdos.app.domain.repository

import com.snowdos.app.domain.model.TaskActivityEvent
import kotlinx.coroutines.flow.Flow

interface ActivityRepository {
    fun observeAllEvents(): Flow<List<TaskActivityEvent>>
    suspend fun insertEvent(event: TaskActivityEvent)
    suspend fun deleteEventById(eventId: String)
    suspend fun deleteAllEvents()
}
