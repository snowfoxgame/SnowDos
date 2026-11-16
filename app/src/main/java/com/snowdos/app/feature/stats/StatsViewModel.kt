package com.snowdos.app.feature.stats

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snowdos.app.domain.model.ScoreSummary
import com.snowdos.app.domain.model.TaskActivityEvent
import com.snowdos.app.domain.repository.ActivityRepository
import com.snowdos.app.domain.strategy.NoOpWidgetUpdateTrigger
import com.snowdos.app.domain.strategy.WidgetUpdateTrigger
import com.snowdos.app.domain.usecase.ObserveScoreStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class StatsUiState(
    val summary: ScoreSummary = ScoreSummary(),
    val events: List<TaskActivityEvent> = emptyList()
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    observeScoreStatsUseCase: ObserveScoreStatsUseCase,
    private val activityRepository: ActivityRepository,
    private val widgetUpdateTrigger: WidgetUpdateTrigger
) : ViewModel() {

    constructor(
        observeScoreStatsUseCase: ObserveScoreStatsUseCase,
        activityRepository: ActivityRepository
    ) : this(
        observeScoreStatsUseCase = observeScoreStatsUseCase,
        activityRepository = activityRepository,
        widgetUpdateTrigger = NoOpWidgetUpdateTrigger
    )

    val uiState: StateFlow<StatsUiState> = observeScoreStatsUseCase()
        .map { snapshot ->
            StatsUiState(
                summary = snapshot.summary,
                events = snapshot.events.filter { it.eventType.isScoredEvent }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState()
        )

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            activityRepository.deleteEventById(eventId)
            widgetUpdateTrigger.updateAll()
        }
    }

    fun clearAllEvents() {
        viewModelScope.launch {
            activityRepository.deleteAllEvents()
            widgetUpdateTrigger.updateAll()
        }
    }
}
