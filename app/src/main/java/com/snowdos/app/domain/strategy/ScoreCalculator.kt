package com.snowdos.app.domain.strategy

import com.snowdos.app.domain.model.ActivityEventType
import com.snowdos.app.domain.model.ScoreRulesConfig
import com.snowdos.app.domain.model.ScoreSummary
import com.snowdos.app.domain.model.TaskActivityEvent
import javax.inject.Inject
import kotlin.math.abs

interface ScoreCalculator {
    fun calculate(events: List<TaskActivityEvent>, rules: ScoreRulesConfig): ScoreSummary
}

/**
 * Priority-weighted reliability score: ratio of earned completion points to total
 * evaluated point weight (completed + missed + ignored), paired with cumulative net points.
 */
class ReliabilityPercentageScoreCalculator @Inject constructor() : ScoreCalculator {

    override fun calculate(
        events: List<TaskActivityEvent>,
        rules: ScoreRulesConfig
    ): ScoreSummary {
        var completed = 0
        var missed = 0
        var ignored = 0
        var completedWeight = 0
        var penaltyWeight = 0
        var netPoints = 0

        for (event in events) {
            when (event.eventType) {
                ActivityEventType.COMPLETED -> {
                    completed++
                    completedWeight += eventWeight(event.scoreDelta, abs(rules.completedPoints))
                    netPoints += event.scoreDelta
                }
                ActivityEventType.MISSED -> {
                    missed++
                    penaltyWeight += eventWeight(event.scoreDelta, abs(rules.missedPenalty))
                    netPoints += event.scoreDelta
                }
                ActivityEventType.IGNORED -> {
                    ignored++
                    penaltyWeight += eventWeight(event.scoreDelta, abs(rules.ignoredPenalty))
                    netPoints += event.scoreDelta
                }
                ActivityEventType.CREATED, ActivityEventType.SNOOZED -> Unit
            }
        }

        val totalEvaluated = completed + missed + ignored
        val hasData = totalEvaluated > 0
        val totalWeight = completedWeight + penaltyWeight
        val percentage = if (hasData && totalWeight > 0) {
            (completedWeight.toFloat() / totalWeight.toFloat()) * 100f
        } else {
            100f
        }

        return ScoreSummary(
            reliabilityScorePercent = percentage.coerceIn(0f, 100f),
            hasScoreData = hasData,
            completedCount = completed,
            missedCount = missed,
            ignoredCount = ignored,
            netPoints = netPoints
        )
    }

    private fun eventWeight(scoreDelta: Int, fallback: Int): Int {
        val deltaWeight = abs(scoreDelta)
        return if (deltaWeight > 0) deltaWeight else fallback.coerceAtLeast(1)
    }
}
