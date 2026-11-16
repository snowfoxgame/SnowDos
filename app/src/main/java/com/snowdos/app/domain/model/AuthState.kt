package com.snowdos.app.domain.model

/**
 * Authentication state exposed by [com.snowdos.app.domain.repository.AuthRepository].
 */
sealed interface AuthState {
    data class UnavailableStub(val message: String) : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(
        val userId: String,
        val displayName: String,
        val email: String?
    ) : AuthState
}
