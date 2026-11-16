package com.snowdos.app.data.repository

import com.snowdos.app.domain.model.SyncStatus
import com.snowdos.app.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Offline-first local-only stub for [SyncRepository].
 */
class LocalOnlySyncRepository @Inject constructor() : SyncRepository {

    private val status = MutableStateFlow<SyncStatus>(SyncStatus.LocalOnly)

    override fun observeSyncStatus(): Flow<SyncStatus> = status.asStateFlow()

    override suspend fun syncNow(): Result<Unit> = Result.success(Unit)
}
