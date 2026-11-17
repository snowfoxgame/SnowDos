package com.snowdos.app.feature.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snowdos.app.domain.model.AccountTabVisibility
import com.snowdos.app.domain.model.TabTransitionMode
import com.snowdos.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppEntryViewModel @Inject constructor(
    settingsRepository: SettingsRepository
) : ViewModel() {

    val hasCompletedOnboarding: StateFlow<Boolean?> = settingsRepository.observeSettings()
        .map<_, Boolean?> { it.hasCompletedOnboarding }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val tabTransitionMode: StateFlow<TabTransitionMode> = settingsRepository.observeSettings()
        .map { it.tabTransitionMode }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TabTransitionMode.ANIMATED
        )

    val accountTabVisibility: StateFlow<AccountTabVisibility> = settingsRepository.observeSettings()
        .map { it.accountTabVisibility }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AccountTabVisibility.MOVE_TO_SETTINGS
        )
}
