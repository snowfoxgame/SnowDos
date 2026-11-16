package com.snowdos.app.core.di

import com.snowdos.app.data.strategy.EscalatingNotificationStrategy
import com.snowdos.app.data.strategy.SimpleTapCompletionProofStrategy
import com.snowdos.app.domain.strategy.CompletionProofStrategy
import com.snowdos.app.domain.strategy.NotificationStrategy
import com.snowdos.app.domain.strategy.ReliabilityPercentageScoreCalculator
import com.snowdos.app.domain.strategy.ReminderScheduler
import com.snowdos.app.domain.strategy.ScoreCalculator
import com.snowdos.app.domain.strategy.WidgetUpdateTrigger
import com.snowdos.app.feature.widget.GlanceWidgetUpdateTrigger
import com.snowdos.app.platform.alarm.AndroidReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StrategyModule {

    @Binds
    @Singleton
    abstract fun bindCompletionProofStrategy(
        impl: SimpleTapCompletionProofStrategy
    ): CompletionProofStrategy

    @Binds
    @Singleton
    abstract fun bindNotificationStrategy(
        impl: EscalatingNotificationStrategy
    ): NotificationStrategy

    @Binds
    @Singleton
    abstract fun bindScoreCalculator(
        impl: ReliabilityPercentageScoreCalculator
    ): ScoreCalculator

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(
        impl: AndroidReminderScheduler
    ): ReminderScheduler

    @Binds
    @Singleton
    abstract fun bindWidgetUpdateTrigger(
        impl: GlanceWidgetUpdateTrigger
    ): WidgetUpdateTrigger
}
