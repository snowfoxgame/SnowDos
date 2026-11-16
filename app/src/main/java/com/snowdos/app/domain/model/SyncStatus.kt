package com.snowdos.app.domain.model

/**
 * Synchronization status exposed by [com.snowdos.app.domain.repository.SyncRepository].
 */
sealed interface SyncStatus {
    data object LocalOnly : SyncStatus
    data class Idle(val lastSyncedAtEpochMillis: Long?) : SyncStatus
    data object Syncing : SyncStatus
    data class Error(val reason: String) : SyncStatus
}
