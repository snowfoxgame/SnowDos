package com.snowdos.app.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.snowdos.app.domain.usecase.ProcessOverdueTasksUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class OverdueCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val processOverdueTasksUseCase: ProcessOverdueTasksUseCase
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            processOverdueTasksUseCase()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
