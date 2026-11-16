package com.snowdos.app.core.di

import android.content.Context
import androidx.room.Room
import com.snowdos.app.data.local.db.SnowDosDatabase
import com.snowdos.app.data.local.db.TaskActivityDao
import com.snowdos.app.data.local.db.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SnowDosDatabase =
        Room.databaseBuilder(
            context,
            SnowDosDatabase::class.java,
            "snowdos.db"
        )
            .addMigrations(*SnowDosDatabase.ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideTaskDao(database: SnowDosDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideTaskActivityDao(database: SnowDosDatabase): TaskActivityDao = database.taskActivityDao()
}
