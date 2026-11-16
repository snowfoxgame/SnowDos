package com.snowdos.app.platform.icon

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.snowdos.app.domain.model.AppSettings
import com.snowdos.app.domain.model.ThemeMode
import com.snowdos.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AppIconPreset(
    val presetId: String,
    val aliasClassName: String,
    val defaultEnabled: Boolean = false
) {
    NORMAL("standard_m3", "com.snowdos.app.MainActivityNormal", defaultEnabled = true),
    WINTER("winter", "com.snowdos.app.MainActivityWinter"),
    HALLOWEEN("halloween", "com.snowdos.app.MainActivityHalloween"),
    CHRISTMAS("christmas", "com.snowdos.app.MainActivityChristmas"),
    SUMMER("summer", "com.snowdos.app.MainActivitySummer"),
    AUTUMN("autumn", "com.snowdos.app.MainActivityAutumn"),
    SPRING("spring", "com.snowdos.app.MainActivitySpring"),
    EASTER("easter", "com.snowdos.app.MainActivityEaster"),
    NEW_YEAR("new_year", "com.snowdos.app.MainActivityNewYear");

    companion object {
        fun resolveTarget(settings: AppSettings): AppIconPreset {
            if (!settings.dynamicAppIconEnabled) return NORMAL
            if (settings.appearance.mode == ThemeMode.CUSTOM) return NORMAL
            val resolvedPresetId = settings.appearance.effectivePresetId()
            return entries.firstOrNull { it.presetId == resolvedPresetId } ?: NORMAL
        }
    }
}

internal interface AliasComponentController {
    fun getEnabledState(aliasClassName: String): Int
    fun setEnabledState(aliasClassName: String, newState: Int, flags: Int)
}

/**
 * Safely switches launcher `<activity-alias>` icons when the app moves to the background (`onStop`)
 * so rapid preset browsing never interrupts or restarts the foreground UI.
 */
@Singleton
class AppIconManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val applyMutex = Mutex()
    private val observingStarted = AtomicBoolean(false)

    @Volatile
    private var desiredIconPreset: AppIconPreset? = null

    private val androidController = object : AliasComponentController {
        override fun getEnabledState(aliasClassName: String): Int {
            val component = ComponentName(context.packageName, aliasClassName)
            return context.packageManager.getComponentEnabledSetting(component)
        }

        override fun setEnabledState(aliasClassName: String, newState: Int, flags: Int) {
            val component = ComponentName(context.packageName, aliasClassName)
            context.packageManager.setComponentEnabledSetting(component, newState, flags)
        }
    }

    fun startObservingSettings() {
        if (!observingStarted.compareAndSet(false, true)) return
        scope.launch {
            settingsRepository.observeSettings().collect { settings ->
                desiredIconPreset = AppIconPreset.resolveTarget(settings)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        val activity = owner as? Activity
        if (activity?.isChangingConfigurations == true) return
        applyPendingIconChangeAsync()
    }

    fun applyPendingIconChangeAsync() {
        scope.launch {
            applyPendingIconNow()
        }
    }

    suspend fun applyPendingIconNow() {
        applyMutex.withLock {
            val settings = settingsRepository.observeSettings().first()
            val target = AppIconPreset.resolveTarget(settings)
            desiredIconPreset = target
            runCatching {
                syncLauncherAlias(target = target, controller = androidController)
            }
        }
    }

    suspend fun onAppearanceSynced(isAppInForeground: Boolean) {
        val settings = settingsRepository.observeSettings().first()
        desiredIconPreset = AppIconPreset.resolveTarget(settings)
        if (!isAppInForeground) {
            applyPendingIconNow()
        }
    }

    companion object {
        internal fun syncLauncherAlias(
            target: AppIconPreset,
            controller: AliasComponentController
        ) {
            val effectivelyEnabled = AppIconPreset.entries.filter { alias ->
                isEffectivelyEnabled(alias, controller.getEnabledState(alias.aliasClassName))
            }

            // Already cleanly pointing only to target — avoid unnecessary PackageManager IPC.
            if (effectivelyEnabled.size == 1 && effectivelyEnabled.first() == target) {
                return
            }

            // 1. Always enable the new alias BEFORE disabling previous ones so at least one
            // LAUNCHER entry remains active at every moment.
            val targetState = controller.getEnabledState(target.aliasClassName)
            if (targetState != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                controller.setEnabledState(
                    aliasClassName = target.aliasClassName,
                    newState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    flags = PackageManager.DONT_KILL_APP
                )
            }

            // 2. Disable all other previously enabled aliases using DONT_KILL_APP.
            for (alias in effectivelyEnabled) {
                if (alias != target) {
                    controller.setEnabledState(
                        aliasClassName = alias.aliasClassName,
                        newState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        flags = PackageManager.DONT_KILL_APP
                    )
                }
            }
        }

        private fun isEffectivelyEnabled(alias: AppIconPreset, state: Int): Boolean = when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> alias.defaultEnabled
            else -> false
        }
    }
}
