package com.snowdos.app.domain.repository

import com.snowdos.app.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Extension point for future cloud or desktop synchronization while remaining offline-first.
 */
interface SyncRepository {
    fun observeSyncStatus(): Flow<SyncStatus>
    suspend fun syncNow(): Result<Unit>
}
