package com.snowdos.app.feature.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.snowdos.app.domain.strategy.WidgetUpdateTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlanceWidgetUpdateTrigger @Inject constructor(
    @param:ApplicationContext private val context: Context
) : WidgetUpdateTrigger {
    private val updateMutex = Mutex()
    private val pendingUpdate = AtomicBoolean(false)

    override suspend fun updateAll() {
        pendingUpdate.set(true)
        if (updateMutex.isLocked) {
            // An active update pass will pick up the pending flag and re-render with the latest state.
            return
        }
        updateMutex.withLock {
            while (pendingUpdate.getAndSet(false)) {
                try {
                    withContext(Dispatchers.IO) {
                        SnowDosTasksWidget().updateAll(context)
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    // Widget updates must never crash foreground task mutations.
                }
            }
        }
    }
}
