package com.snowdos.app.domain.usecase

import com.snowdos.app.domain.model.ScoreSummary
import com.snowdos.app.domain.model.TaskActivityEvent
import com.snowdos.app.domain.repository.ActivityRepository
import com.snowdos.app.domain.repository.SettingsRepository
import com.snowdos.app.domain.strategy.ScoreCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class ScoreStatsSnapshot(
    val summary: ScoreSummary,
    val events: List<TaskActivityEvent>
)

class ObserveScoreStatsUseCase @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val settingsRepository: SettingsRepository,
    private val scoreCalculator: ScoreCalculator
) {
    operator fun invoke(): Flow<ScoreStatsSnapshot> {
        return combine(
            activityRepository.observeAllEvents(),
            settingsRepository.observeSettings()
        ) { events, settings ->
            val summary = scoreCalculator.calculate(events, settings.scoreRules)
            ScoreStatsSnapshot(
                summary = summary,
                events = events
            )
        }
    }
}
