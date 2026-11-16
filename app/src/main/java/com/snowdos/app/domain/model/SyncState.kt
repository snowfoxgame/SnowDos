package com.snowdos.app.domain.model

/**
 * Tracks local vs. remote synchronization state for future [com.snowdos.app.domain.repository.SyncRepository].
 */
enum class SyncState {
    LOCAL_ONLY,
    PENDING_SYNC,
    SYNCED,
    CONFLICT
}
