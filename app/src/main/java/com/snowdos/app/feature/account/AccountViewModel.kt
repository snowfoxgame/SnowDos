package com.snowdos.app.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snowdos.app.domain.model.AuthState
import com.snowdos.app.domain.model.SyncStatus
import com.snowdos.app.domain.repository.AuthRepository
import com.snowdos.app.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AccountUiState(
    val authState: AuthState = AuthState.UnavailableStub("Bald verfügbar"),
    val syncStatus: SyncStatus = SyncStatus.LocalOnly
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    authRepository: AuthRepository,
    syncRepository: SyncRepository
) : ViewModel() {

    val uiState: StateFlow<AccountUiState> = combine(
        authRepository.observeAuthState(),
        syncRepository.observeSyncStatus()
    ) { auth, sync ->
        AccountUiState(authState = auth, syncStatus = sync)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountUiState()
    )
}
