package com.snowdos.app.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class ScoreSummary(
    val reliabilityScorePercent: Float = 100f,
    val hasScoreData: Boolean = false,
    val completedCount: Int = 0,
    val missedCount: Int = 0,
    val ignoredCount: Int = 0,
    val netPoints: Int = 0
)
