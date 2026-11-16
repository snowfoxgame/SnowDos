package com.snowdos.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snowdos.app.core.common.ProvideAppLanguage
import com.snowdos.app.core.designsystem.SnowDosTheme
import com.snowdos.app.core.designsystem.components.AppBackgroundContainer
import com.snowdos.app.feature.navigation.AppLaunchTarget
import com.snowdos.app.feature.navigation.SnowDosNavHost
import com.snowdos.app.platform.icon.AppIconManager
import com.snowdos.app.platform.notification.ReminderNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appIconManager: AppIconManager

    private val themeViewModel: AppThemeViewModel by viewModels()
    private val pendingLaunchTarget = MutableStateFlow<AppLaunchTarget?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(appIconManager)
        enableEdgeToEdge()
        consumeLaunchIntent(intent)
        requestNotificationPermissionIfNeeded()
        setContent {
            val isDark = isSystemInDarkTheme()
            LaunchedEffect(isDark) {
                themeViewModel.updateSystemDarkTheme(isDark)
            }
            val themeState by themeViewModel.themeState.collectAsStateWithLifecycle()
            val launchTarget by pendingLaunchTarget.collectAsStateWithLifecycle()

            LaunchedEffect(themeState.isEffectiveDarkTheme) {
                val barStyle = if (themeState.isEffectiveDarkTheme) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }

            ProvideAppLanguage(language = themeState.appLanguage) {
                SnowDosTheme(
                    appearance = themeState.appearance,
                    darkTheme = themeState.isEffectiveDarkTheme,
                    reduceLiveBlur = themeState.reduceLiveBlur,
                    weatherIsDay = themeState.weatherIsDay,
                    weatherCondition = themeState.weatherCondition
                ) {
                    AppBackgroundContainer(
                        backgroundImageUri = themeState.activeBackgroundUri,
                        applyReadabilityScrim = themeState.applyReadabilityScrim,
                        darkTheme = themeState.isEffectiveDarkTheme,
                        reduceLiveBlur = themeState.reduceLiveBlur,
                        liveScene = themeState.liveScene
                    ) {
                        SnowDosNavHost(
                            launchTarget = launchTarget,
                            onLaunchTargetConsumed = {
                                pendingLaunchTarget.value = null
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLaunchIntent(intent)
    }

    private fun consumeLaunchIntent(intent: Intent?) {
        val currentIntent = intent ?: return
        val taskId = currentIntent.getStringExtra(ReminderNotificationManager.EXTRA_TASK_ID)
            ?.takeIf { it.isNotBlank() }
        if (taskId != null) {
            val triggerProof = currentIntent.getBooleanExtra(ReminderNotificationManager.EXTRA_TRIGGER_PROOF, false)
            currentIntent.removeExtra(ReminderNotificationManager.EXTRA_TASK_ID)
            currentIntent.removeExtra(ReminderNotificationManager.EXTRA_TRIGGER_PROOF)
            pendingLaunchTarget.value = AppLaunchTarget.TaskNotification(
                taskId = taskId,
                triggerProof = triggerProof
            )
            return
        }

        val openCreateTask = currentIntent.getBooleanExtra(EXTRA_OPEN_CREATE_TASK, false)
        val openTasksTab = currentIntent.getBooleanExtra(EXTRA_OPEN_TASKS_TAB, false)
        if (openCreateTask || openTasksTab) {
            currentIntent.removeExtra(EXTRA_OPEN_CREATE_TASK)
            currentIntent.removeExtra(EXTRA_OPEN_TASKS_TAB)
            pendingLaunchTarget.value = AppLaunchTarget.WidgetNavigation(
                openCreateTask = openCreateTask
            )
        }
    }

    companion object {
        const val EXTRA_OPEN_TASKS_TAB = "com.snowdos.app.extra.OPEN_TASKS_TAB"
        const val EXTRA_OPEN_CREATE_TASK = "com.snowdos.app.extra.OPEN_CREATE_TASK"
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
