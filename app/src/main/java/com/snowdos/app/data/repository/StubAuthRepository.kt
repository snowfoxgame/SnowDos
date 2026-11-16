package com.snowdos.app.data.repository

import com.snowdos.app.domain.model.AuthState
import com.snowdos.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Stub implementation of [AuthRepository] until account & login features are implemented.
 */
class StubAuthRepository @Inject constructor() : AuthRepository {

    private val state = MutableStateFlow<AuthState>(
        AuthState.UnavailableStub(
            message = "Account-Login, Cloud-Backup und Geräte-Sync folgen in einer späteren Version."
        )
    )

    override fun observeAuthState(): Flow<AuthState> = state.asStateFlow()

    override suspend fun signIn(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Account-Login ist noch nicht verfügbar."))

    override suspend fun signOut(): Result<Unit> =
        Result.success(Unit)
}
