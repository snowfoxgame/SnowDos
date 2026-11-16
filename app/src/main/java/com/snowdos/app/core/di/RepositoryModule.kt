package com.snowdos.app.core.di

import com.snowdos.app.data.repository.DataStoreSettingsRepository
import com.snowdos.app.data.repository.LocalOnlySyncRepository
import com.snowdos.app.data.repository.RoomActivityRepository
import com.snowdos.app.data.repository.RoomTaskRepository
import com.snowdos.app.data.repository.StubAuthRepository
import com.snowdos.app.domain.repository.ActivityRepository
import com.snowdos.app.domain.repository.AuthRepository
import com.snowdos.app.domain.repository.SettingsRepository
import com.snowdos.app.domain.repository.SyncRepository
import com.snowdos.app.domain.repository.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: RoomTaskRepository): TaskRepository

    @Binds
    @Singleton
    abstract fun bindActivityRepository(impl: RoomActivityRepository): ActivityRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: StubAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: LocalOnlySyncRepository): SyncRepository
}
