package com.snowdos.app.platform.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snowdos.app.AppThemeViewModel
import com.snowdos.app.MainActivity
import com.snowdos.app.R
import com.snowdos.app.core.common.ProvideAppLanguage
import com.snowdos.app.core.common.formatCompactDateTime
import com.snowdos.app.core.common.labelRes
import com.snowdos.app.core.designsystem.SnowDosTheme
import com.snowdos.app.core.designsystem.SnowDosThemeTokens
import com.snowdos.app.core.designsystem.components.AppBackgroundContainer
import com.snowdos.app.core.designsystem.components.SnowGlassButton
import com.snowdos.app.core.designsystem.components.SnowGlassCard
import com.snowdos.app.core.designsystem.components.SnowGlassChip
import com.snowdos.app.domain.model.Task
import com.snowdos.app.domain.model.TaskPriority
import com.snowdos.app.domain.repository.TaskRepository
import com.snowdos.app.domain.usecase.CompleteTaskOutcome
import com.snowdos.app.domain.usecase.CompleteTaskUseCase
import com.snowdos.app.domain.usecase.SnoozeTaskUseCase
import com.snowdos.app.platform.notification.ReminderNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Full-screen alarm overlay shown on lockscreen or active screen when "Anti-Ignore-Power"
 * (intensive mode) triggers a task reminder. Forces a conscious choice to complete or snooze.
 */
@AndroidEntryPoint
class ReminderAlarmActivity : ComponentActivity() {

    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var completeTaskUseCase: CompleteTaskUseCase

    @Inject
    lateinit var snoozeTaskUseCase: SnoozeTaskUseCase

    @Inject
    lateinit var notificationManager: ReminderNotificationManager

    private val themeViewModel: AppThemeViewModel by viewModels()
    private val activeTaskId = MutableStateFlow<String?>(null)
    private val defaultSnoozeMinutes = MutableStateFlow(15)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenBehavior()
        enableEdgeToEdge()
        readIntent(intent)

        setContent {
            val isDark = isSystemInDarkTheme()
            LaunchedEffect(isDark) {
                themeViewModel.updateSystemDarkTheme(isDark)
            }
            val themeState by themeViewModel.themeState.collectAsStateWithLifecycle()
            val taskId by activeTaskId.collectAsStateWithLifecycle()
            val snoozeDefault by defaultSnoozeMinutes.collectAsStateWithLifecycle()

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
                    darkTheme = themeState.isEffectiveDarkTheme
                ) {
                    AppBackgroundContainer(
                        backgroundImageUri = themeState.activeBackgroundUri,
                        applyReadabilityScrim = themeState.applyReadabilityScrim,
                        darkTheme = themeState.isEffectiveDarkTheme
                    ) {
                        AlarmOverlayContent(
                            taskId = taskId,
                            defaultSnoozeMinutes = snoozeDefault,
                            taskRepository = taskRepository,
                            onComplete = { id ->
                                when (completeTaskUseCase(id)) {
                                    is CompleteTaskOutcome.Completed,
                                    CompleteTaskOutcome.NotActionable -> {
                                        notificationManager.cancelNotification(id)
                                        finishAndRemoveFromRecents()
                                    }
                                    is CompleteTaskOutcome.ProofRequired,
                                    is CompleteTaskOutcome.Rejected -> {
                                        notificationManager.cancelNotification(id)
                                        openMainAppForProof(id)
                                    }
                                }
                            },
                            onSnooze = { id, minutes ->
                                snoozeTaskUseCase(
                                    taskId = id,
                                    snoozeMinutes = minutes,
                                    metadataReason = "intensive_alarm_snooze"
                                )
                                notificationManager.cancelNotification(id)
                                finishAndRemoveFromRecents()
                            },
                            onTaskMissing = {
                                taskId?.let { notificationManager.cancelNotification(it) }
                                finishAndRemoveFromRecents()
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
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        activeTaskId.value = intent?.getStringExtra(ReminderNotificationManager.EXTRA_TASK_ID)
        val extraSnooze = intent?.getIntExtra(ReminderNotificationManager.EXTRA_DEFAULT_SNOOZE_MINUTES, 15) ?: 15
        defaultSnoozeMinutes.value = extraSnooze.coerceAtLeast(1)
    }

    private fun configureLockScreenBehavior() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun openMainAppForProof(taskId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        }
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReminderNotificationManager.EXTRA_TASK_ID, taskId)
            putExtra(ReminderNotificationManager.EXTRA_TRIGGER_PROOF, true)
        }
        startActivity(mainIntent)
        finishAndRemoveFromRecents()
    }

    private fun finishAndRemoveFromRecents() {
        finishAndRemoveTask()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmOverlayContent(
    taskId: String?,
    defaultSnoozeMinutes: Int,
    taskRepository: TaskRepository,
    onComplete: suspend (String) -> Unit,
    onSnooze: suspend (String, Int) -> Unit,
    onTaskMissing: () -> Unit
) {
    val tokens = SnowDosThemeTokens.current
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var selectedSnoozeMinutes by remember(defaultSnoozeMinutes) {
        mutableIntStateOf(defaultSnoozeMinutes)
    }

    val loadedTaskState by produceState<Pair<Boolean, Task?>>(initialValue = false to null, taskId) {
        value = if (taskId.isNullOrBlank()) {
            true to null
        } else {
            true to taskRepository.getTaskById(taskId)
        }
    }
    val (hasLoaded, task) = loadedTaskState

    LaunchedEffect(hasLoaded, task) {
        if (hasLoaded && (task == null || !task.status.isActionable)) {
            onTaskMissing()
        }
    }

    // Intensive alarm cannot be silently dismissed with system back.
    BackHandler(enabled = true) {}

    val activeTask = task ?: return

    val infiniteTransition = rememberInfiniteTransition(label = "alarmPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alarmIconPulse"
    )

    val snoozeOptions = remember(defaultSnoozeMinutes) {
        listOf(5, 10, 15, 30, 60)
            .plus(defaultSnoozeMinutes)
            .distinct()
            .sorted()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(tokens.spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        SnowGlassCard(
            shape = RoundedCornerShape(tokens.radius.sheet),
            alpha = (tokens.surface.cardAlpha + 0.14f).coerceIn(0.30f, 0.56f),
            highlighted = true,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(tokens.spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
            ) {
                SnowGlassCard(
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    alpha = tokens.surface.selectedAlpha,
                    highlighted = true,
                    modifier = Modifier
                        .size(84.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Text(
                    text = if (activeTask.snoozeCount > 0) {
                        stringResource(R.string.alarm_badge_snoozed, activeTask.snoozeCount)
                    } else {
                        stringResource(R.string.alarm_badge_due)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = activeTask.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                if (activeTask.notes.isNotBlank()) {
                    Text(
                        text = activeTask.notes,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                activeTask.dueAtEpochMillis?.let { dueAt ->
                    val priorityLabel = stringResource(activeTask.priority.labelRes)
                    Text(
                        text = stringResource(
                            R.string.alarm_due_priority,
                            formatCompactDateTime(dueAt),
                            priorityLabel
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(tokens.spacing.xs))

                SnowGlassButton(
                    highlighted = true,
                    enabled = !isProcessing,
                    onClick = {
                        if (!isProcessing) {
                            isProcessing = true
                            scope.launch { onComplete(activeTask.id) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.alarm_complete_now))
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
                ) {
                    Text(
                        text = stringResource(R.string.alarm_snooze_prompt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        snoozeOptions.forEach { minutes ->
                            val label = if (minutes >= 60 && minutes % 60 == 0) {
                                stringResource(R.string.label_hours_short, minutes / 60)
                            } else {
                                stringResource(R.string.label_minutes_short, minutes)
                            }
                            SnowGlassChip(
                                label = label,
                                selected = selectedSnoozeMinutes == minutes,
                                onClick = { selectedSnoozeMinutes = minutes }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(tokens.spacing.xs))

                    SnowGlassButton(
                        enabled = !isProcessing,
                        onClick = {
                            if (!isProcessing) {
                                isProcessing = true
                                scope.launch { onSnooze(activeTask.id, selectedSnoozeMinutes) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Snooze,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.alarm_snooze_cta, selectedSnoozeMinutes))
                    }
                }
            }
        }
    }
}
