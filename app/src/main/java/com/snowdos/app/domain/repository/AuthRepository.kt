package com.snowdos.app.domain.repository

import com.snowdos.app.domain.model.AuthState
import kotlinx.coroutines.flow.Flow

/**
 * Extension point for future authentication and user profile features (Tab 2).
 */
interface AuthRepository {
    fun observeAuthState(): Flow<AuthState>
    suspend fun signIn(): Result<Unit>
    suspend fun signOut(): Result<Unit>
}
