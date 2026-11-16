package com.snowdos.app.core.di

import com.snowdos.app.core.common.AndroidAppTextResolver
import com.snowdos.app.core.common.AppTextResolver
import com.snowdos.app.core.common.DefaultDispatcherProvider
import com.snowdos.app.core.common.DispatcherProvider
import com.snowdos.app.core.common.SystemTimeProvider
import com.snowdos.app.core.common.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CommonModule {

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = SystemTimeProvider()

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalizationModule {

    @Binds
    @Singleton
    abstract fun bindAppTextResolver(impl: AndroidAppTextResolver): AppTextResolver
}
