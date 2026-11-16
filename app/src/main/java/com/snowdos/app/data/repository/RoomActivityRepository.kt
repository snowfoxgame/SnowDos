package com.snowdos.app.data.repository

import com.snowdos.app.data.local.db.TaskActivityDao
import com.snowdos.app.data.local.mapper.toDomain
import com.snowdos.app.data.local.mapper.toEntity
import com.snowdos.app.domain.model.TaskActivityEvent
import com.snowdos.app.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomActivityRepository @Inject constructor(
    private val activityDao: TaskActivityDao
) : ActivityRepository {

    override fun observeAllEvents(): Flow<List<TaskActivityEvent>> =
        activityDao.observeAllDesc().map { entities -> entities.map { it.toDomain() } }

    override suspend fun insertEvent(event: TaskActivityEvent) {
        activityDao.insert(event.toEntity())
    }

    override suspend fun deleteEventById(eventId: String) {
        activityDao.deleteById(eventId)
    }

    override suspend fun deleteAllEvents() {
        activityDao.deleteAll()
    }
}
