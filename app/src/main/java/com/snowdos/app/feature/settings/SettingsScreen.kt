package com.snowdos.app.feature.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BlurOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Scoreboard
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snowdos.app.BuildConfig
import com.snowdos.app.R
import com.snowdos.app.core.designsystem.LocalThemeConfig
import com.snowdos.app.core.designsystem.SnowDosThemeTokens
import com.snowdos.app.core.designsystem.components.SnowFrostedDialog
import com.snowdos.app.core.designsystem.components.SnowGlassButton
import com.snowdos.app.core.designsystem.components.SnowGlassCard
import com.snowdos.app.core.designsystem.components.SnowBlurScope
import com.snowdos.app.core.designsystem.components.glassReadySurface
import com.snowdos.app.core.designsystem.components.snowPressFeedback
import com.snowdos.app.feature.tutorial.TutorialTargetId
import com.snowdos.app.feature.tutorial.tutorialTarget
import com.snowdos.app.feature.widget.WidgetAppearanceControls
import com.snowdos.app.feature.widget.widgetSummaryText
import com.snowdos.app.domain.model.AppLanguage
import com.snowdos.app.domain.model.DismissBehavior
import com.snowdos.app.domain.model.ScoreRulesConfig
import com.snowdos.app.domain.model.TabTransitionMode
import com.snowdos.app.domain.model.TaskPriority
import com.snowdos.app.domain.model.ThemeAppearanceConfig
import com.snowdos.app.domain.model.ThemeMode
import com.snowdos.app.domain.model.WidgetColorMode
import com.snowdos.app.domain.model.WidgetSurfaceStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class ActiveSettingsDialog {
    NONE,
    LEAD_TIME,
    SNOOZE_MINUTES,
    DISMISS_BEHAVIOR,
    GRACE_PERIOD,
    SCORE_RULES,
    LANGUAGE,
    TAB_TRANSITION,
    WIDGET
}

@Composable
fun SettingsScreen(
    showCompactAccountCard: Boolean = false,
    onOpenAccount: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenOnboarding: () -> Unit = {},
    onStartTutorial: () -> Unit = {},
    isTutorialHighlightActive: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = uiState.settings
    val tokens = SnowDosThemeTokens.current
    val themeConfig = LocalThemeConfig.current
    val context = LocalContext.current
    val transparentListItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

    var permissionRefreshTick by remember { mutableIntStateOf(0) }
    var showAntiIgnorePermissionsDialog by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember { mutableStateOf(true) }
    var isIgnoringBatteryOptimization by remember { mutableStateOf(false) }
    var hasDndPolicyAccess by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        permissionRefreshTick++
        viewModel.onAction(SettingsAction.RefreshAlarmPermission)
    }

    LifecycleResumeEffect(Unit) {
        permissionRefreshTick++
        viewModel.onAction(SettingsAction.RefreshAlarmPermission)
        onPauseOrDispose { }
    }

    LaunchedEffect(permissionRefreshTick, context) {
        val (notif, battery, dnd) = withContext(Dispatchers.IO) {
            val runtimeGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            val notifEnabled = runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
            val batteryIgnored = context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            val dndAccess = context.getSystemService(NotificationManager::class.java)
                ?.isNotificationPolicyAccessGranted ?: false
            Triple(notifEnabled, batteryIgnored, dndAccess)
        }
        hasNotificationPermission = notif
        isIgnoringBatteryOptimization = battery
        hasDndPolicyAccess = dnd
    }
    val missingCorePowerPermissions = !hasNotificationPermission ||
        !uiState.canScheduleExactAlarms ||
        !uiState.canUseFullScreenIntent

    val listState = rememberLazyListState()
    var activeDialog by remember { mutableStateOf(ActiveSettingsDialog.NONE) }
    var lastOpenedDialog by remember { mutableStateOf(ActiveSettingsDialog.SNOOZE_MINUTES) }
    if (activeDialog != ActiveSettingsDialog.NONE) {
        lastOpenedDialog = activeDialog
    }

    LaunchedEffect(isTutorialHighlightActive) {
        if (isTutorialHighlightActive) {
            listState.scrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = tokens.spacing.md,
            bottom = tokens.spacing.floatingNavBottomClearance
        )
    ) {
        item(key = "settings_title", contentType = "title") {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm)
            )
        }

        // ── 1. Account ───────────────────────────────────────────────────────────
        item(key = "header_account", contentType = "header") {
            SettingsSectionHeader(stringResource(R.string.settings_section_account))
        }

        item(key = "compact_account_card", contentType = "action") {
            val accountInteraction = remember { MutableInteractionSource() }
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.settings_account_card_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.settings_account_card_status),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = transparentListItemColors,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                    .snowPressFeedback(accountInteraction, pressedScale = 0.98f)
                    .glassReadySurface(
                        shape = RoundedCornerShape(tokens.radius.compactCard),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        alpha = if (showCompactAccountCard) {
                            tokens.surface.cardAlpha
                        } else {
                            tokens.surface.interactiveAlpha
                        },
                        highlighted = showCompactAccountCard
                    )
                    .clickable(
                        interactionSource = accountInteraction,
                        indication = null,
                        onClick = onOpenAccount
                    )
            )
        }

        // ── 2. Aufgaben & Erinnerungen ───────────────────────────────────────────
        item(key = "header_tasks_reminders", contentType = "header") {
            Spacer(modifier = Modifier.height(tokens.spacing.md))
            SettingsSectionHeader(stringResource(R.string.settings_section_tasks_reminders))
        }

        item(key = "reminders_highlight_group", contentType = "grouped_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .tutorialTarget(TutorialTargetId.SETTINGS_HIGHLIGHTS)
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_notifications_title)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (settings.notificationsEnabled) {
                                    R.string.settings_notifications_on
                                } else {
                                    R.string.settings_notifications_off
                                }
                            )
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Outlined.Notifications, contentDescription = null)
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.notificationsEnabled,
                            onCheckedChange = {
                                viewModel.onAction(SettingsAction.SetNotificationsEnabled(it))
                            }
                        )
                    },
                    colors = transparentListItemColors,
                    modifier = Modifier
                        .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                        .glassReadySurface(
                            shape = RoundedCornerShape(tokens.radius.compactCard),
                            alpha = tokens.surface.interactiveAlpha
                        )
                )

                val leadTimeInteraction = remember { MutableInteractionSource() }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_lead_time_title)) },
                    supportingContent = {
                        val mins = settings.reminderLeadTimeMinutes
                        Text(
                            if (mins == 0) {
                                stringResource(R.string.settings_lead_time_at_due_summary)
                            } else {
                                stringResource(R.string.settings_lead_time_before_summary, mins)
                            }
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Outlined.Alarm, contentDescription = null)
                    },
                    colors = transparentListItemColors,
                    modifier = Modifier
                        .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                        .snowPressFeedback(leadTimeInteraction, pressedScale = 0.98f)
                        .glassReadySurface(
                            shape = RoundedCornerShape(tokens.radius.compactCard),
                            alpha = tokens.surface.interactiveAlpha
                        )
                        .clickable(interactionSource = leadTimeInteraction, indication = null) {
                            activeDialog = ActiveSettingsDialog.LEAD_TIME
                        }
                )
            }
        }

        item(key = "default_snooze", contentType = "action") {
            val snoozeInteraction = remember { MutableInteractionSource() }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_default_snooze_title)) },
                supportingContent = {
                    Text(stringResource(R.string.label_minutes_full, settings.defaultSnoozeMinutes))
                },
                leadingContent = {
                    Icon(Icons.Outlined.Snooze, contentDescription = null)
                },
                colors = transparentListItemColors,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                    .snowPressFeedback(snoozeInteraction, pressedScale = 0.98f)
                    .glassReadySurface(
                        shape = RoundedCornerShape(tokens.radius.compactCard),
                        alpha = tokens.surface.interactiveAlpha
                    )
                    .clickable(interactionSource = snoozeInteraction, indication = null) {
                        activeDialog = ActiveSettingsDialog.SNOOZE_MINUTES
                    }
            )
        }

        if (settings.notificationsEnabled && !uiState.canScheduleExactAlarms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item(key = "exact_alarm_warning", contentType = "action") {
                val alarmInteraction = remember { MutableInteractionSource() }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_exact_alarm_title)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_exact_alarm_desc))
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = tokens.semantic.warning
                        )
                    },
                    colors = transparentListItemColors,
                    modifier = Modifier
                        .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                        .snowPressFeedback(alarmInteraction, pressedScale = 0.98f)
                        .glassReadySurface(
                            shape = RoundedCornerShape(tokens.radius.compactCard),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            alpha = tokens.surface.cardAlpha,
                            highlighted = true
                        )
                        .clickable(interactionSource = alarmInteraction, indication = null) {
                            val alarmIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            runCatching { context.startActivity(alarmIntent) }
                        }
                )
            }
        }

        item(key = "dismiss_behavior", contentType = "action") {
            val dismissInteraction = remember { MutableInteractionSource() }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dismiss_title)) },
                supportingContent = {
                    Text(
                        when (settings.dismissBehavior) {
                            DismissBehavior.MARK_IGNORED ->
                                stringResource(
                                    R.string.settings_dismiss_ignored_summary,
                                    settings.scoreRules.ignoredPenaltyFor(TaskPriority.LOW),
                                    settings.scoreRules.ignoredPenaltyFor(TaskPriority.HIGH)
                                )
                            DismissBehavior.DELETE_PERMANENTLY ->
                                stringResource(R.string.settings_dismiss_delete_summary)
                        }
                    )
                },
                leadingContent = {
                    Icon(Icons.Outlined.Close, contentDescription = null)
                },
                colors = transparentListItemColors,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                    .snowPressFeedback(dismissInteraction, pressedScale = 0.98f)
                    .glassReadySurface(
                        shape = RoundedCornerShape(tokens.radius.compactCard),
                        alpha = tokens.surface.interactiveAlpha
                    )
                    .clickable(interactionSource = dismissInteraction, indication = null) {
                        activeDialog = ActiveSettingsDialog.DISMISS_BEHAVIOR
                    }
            )
        }

        item(key = "grace_period", contentType = "action") {
            val graceInteraction = remember { MutableInteractionSource() }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_grace_title)) },
                supportingContent = {
                    val mins = settings.scoreRules.missedGracePeriodMinutes
                    Text(
                        if (mins == 0) {
                            stringResource(R.string.settings_grace_immediate_summary)
                        } else {
                            stringResource(R.string.settings_grace_buffer_summary, mins)
                        }
                    )
                },
                leadingContent = {
                    Icon(Icons.Outlined.Timer, contentDescription = null)
                },
                colors = transparentListItemColors,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                    .snowPressFeedback(graceInteraction, pressedScale = 0.98f)
                    .glassReadySurface(
                        shape = RoundedCornerShape(tokens.radius.compactCard),
                        alpha = tokens.surface.interactiveAlpha
                    )
                    .clickable(interactionSource = graceInteraction, indication = null) {
                        activeDialog = ActiveSettingsDialog.GRACE_PERIOD
                    }
            )
        }

        item(key = "score_rules", contentType = "action") {
            val rulesInteraction = remember { MutableInteractionSource() }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_score_rules_title)) },
                supportingContent = {
                    val rules = settings.scoreRules
                    Text(formatScoreRulesSummary(rules))
                },
                leadingContent = {
                    Icon(Icons.Outlined.Scoreboard, contentDescription = null)
                },
                colors = transparentListItemColors,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                    .snowPressFeedback(rulesInteraction, pressedScale = 0.98f)
                    .glassReadySurface(
                        shape = RoundedCornerShape(tokens.radius.compactCard),
                        alpha = tokens.surface.interactiveAlpha
                    )
                    .clickable(interactionSource = rulesInteraction, indication = null) {
                        activeDialog = ActiveSettingsDialog.SCORE_RULES
                    }
            )
        }

        // ── 3. Darstellung ───────────────────────────────────────────────────────
        item(key = "header_appearance", contentType = "header") {
            Spacer(modifier = Modifier.height(tokens.spacing.md))
            SettingsSectionHeader(stringResource(R.string.settings_section_appearance))
        }

        item(key = "appearance_entry", contentType = "action") {
            val bgHint = if (settings.appearance.backgroundImageUri != null) {
                stringResource(R.string.settings_appearance_bg_hint)
            } else {
                ""
            }
            val appearanceLabel = if (settings.appearance.mode == ThemeMode.CUSTOM) {
                stringResource(R.string.appearance_custom_name, settings.appearance.customAccentHex)
            } else {
                themeConfig.displayName
            }
            val appearanceInteraction = remember { MutableInteractionSource() }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_appearance_title)) },
                supportingContent = {
                    Text("$appearanceLabel$bgHint")
                },
                leadingContent = {
                    Icon(Icons.Outlined.Palette, contentDescription = null)
                },
                colors = transparentListItemColors,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                    .snowPressFeedback(appearanceInteraction, pressedScale = 0.98f)
                    .glassReadySurface(
                        shape = RoundedCornerShape(tokens.radius.compactCard),
                        alpha = tokens.surface.interactiveAlpha
                    )
                    .clickable(
                        interactionSource = appearanceInteraction,
                        indication = null,
                        onClick = onOpenAppearance
                    )
            )
        }

        item(key = "tab_transition_picker", contentType = "action") {
            val transitionInteraction = remember { MutableInteractionSource() }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_tab_transition_title)) },
                supportingContent = {
                    Text(
                        stringResource(
                            when (settings.tabTransitionMode) {
                                TabTransitionMode.ANIMATED -> R.string.settings_tab_transition_animated
                                TabTransitionMode.INSTANT -> R.string.settings_tab_transition_instant
                            }
                        )
                    )
                },
                leadingContent = {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                },
                colors = transparentListItemColors,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                    .snowPressFeedback(transitionInteraction, pressedScale = 0.98f)
                    .glassReadySurface(
                        shape = RoundedCornerShape(tokens.radius.compactCard),
                        alpha = tokens.surface.interactiveAlpha
                    )
                    .clickable(interactionSource = transitionInteraction, indication = null) {
                        activeDialog = ActiveSettingsDialog.TAB_TRANSITION
                    }
            )
        }

        item(key = "reduce_live_blur", contentType = "switch") {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_reduce_blur_title)) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (settings.reduceLiveBlur) {
                                R.string.settings_reduce_blur_on
                            } else {
                                R.string.settings_reduce_blur_off
                            }
                        )
                    )
                },
                leadingContent = {
                    Icon(Icons.Outlined.BlurOff, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = settings.reduceLiveBlur,
                        onCheckedChange = {
                            viewModel.onAction(SettingsAction.SetReduceLiveBlur(it))
                        }
                    )
                },
                colors = transparentListItemColors,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                    .glassReadySurface(
                        shape = RoundedCornerShape(tokens.radius.compactCard),
                        alpha = tokens.surface.interactiveAlpha
                    )
            )
        }

        item(key = "widget_settings", contentType = "action") {
            val widgetInteraction = remember { MutableInteractionSource() }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_widget_title)) },
                supportingContent = {
                    Text(
                        widgetSummaryText(
                            colorMode = settings.widgetColorMode,
                            surfaceStyle = settings.widgetSurfaceStyle
                        )
                    )
                },
                leadingContent = {
                    Icon(Icons.Outlined.Widgets, contentDescription = null)
                },
                colors = transparentListItemColors,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                    .snowPressFeedback(widgetInteraction, pressedScale = 0.98f)
                    .glassReadySurface(
                        shape = RoundedCornerShape(tokens.radius.compactCard),
                        alpha = tokens.surface.interactiveAlpha
                    )
                    .clickable(interactionSource = widgetInteraction, indication = null) {
                        activeDialog = ActiveSettingsDialog.WIDGET
                    }
            )
        }

        item(key = "language_picker", contentType = "action") {
            val languageInteraction = remember { MutableInteractionSource() }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_language_title)) },
                supportingContent = {
                    Text("${settings.appLanguage.flagEmoji} ${settings.appLanguage.nativeName}")
                },
                leadingContent = {
                    Icon(Icons.Outlined.Language, contentDescription = null)
                },
                colors = transparentListItemColors,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                    .snowPressFeedback(languageInteraction, pressedScale = 0.98f)
                    .glassReadySurface(
                        shape = RoundedCornerShape(tokens.radius.compactCard),
                        alpha = tokens.surface.interactiveAlpha
                    )
                    .clickable(interactionSource = languageInteraction, indication = null) {
                        activeDialog = ActiveSettingsDialog.LANGUAGE
                    }
            )
        }

        // ── 4. Experimentell ─────────────────────────────────────────────────────
        item(key = "header_experimental", contentType = "header") {
            Spacer(modifier = Modifier.height(tokens.spacing.md))
            SettingsSectionHeader(
                title = stringResource(R.string.settings_section_experimental),
                badge = stringResource(R.string.settings_experimental_badge),
                subtitle = stringResource(R.string.settings_experimental_subtitle)
            )
        }

        item(key = "cross_app_sync", contentType = "switch") {
            val isConnected = settings.partnerAppConnected
            val isSyncActive = isConnected && settings.crossAppSyncEnabled
            val subtitleRes = when {
                !isConnected -> R.string.settings_cross_app_sync_unavailable
                isSyncActive -> R.string.settings_cross_app_sync_on
                else -> R.string.settings_cross_app_sync_off
            }
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.settings_cross_app_sync_title),
                        color = if (isConnected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                supportingContent = {
                    Text(stringResource(subtitleRes))
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Sync,
                        contentDescription = null,
                        tint = if (isConnected) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        }
                    )
                },
                trailingContent = {
                    Switch(
                        checked = isSyncActive,
                        enabled = isConnected,
                        onCheckedChange = { enabled ->
                            viewModel.onAction(SettingsAction.SetCrossAppSyncEnabled(enabled))
                        }
                    )
                },
                colors = transparentListItemColors,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                    .glassReadySurface(
                        shape = RoundedCornerShape(tokens.radius.compactCard),
                        alpha = tokens.surface.interactiveAlpha,
                        highlighted = isSyncActive
                    )
            )
        }

        item(key = "dynamic_app_icon", contentType = "switch") {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dynamic_icon_title)) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (settings.dynamicAppIconEnabled) {
                                R.string.settings_dynamic_icon_on
                            } else {
                                R.string.settings_dynamic_icon_off
                            }
                        )
                    )
                },
                leadingContent = {
                    Icon(Icons.Outlined.Apps, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = settings.dynamicAppIconEnabled,
                        onCheckedChange = {
                            viewModel.onAction(SettingsAction.SetDynamicAppIconEnabled(it))
                        }
                    )
                },
                colors = transparentListItemColors,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                    .glassReadySurface(
                        shape = RoundedCornerShape(tokens.radius.compactCard),
                        alpha = tokens.surface.interactiveAlpha
                    )
            )
        }

        item(key = "anti_ignore_toggle", contentType = "switch") {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_anti_ignore_title)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_anti_ignore_coming_soon))
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Switch(
                        checked = false,
                        enabled = false,
                        onCheckedChange = {}
                    )
                },
                colors = transparentListItemColors,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                    .glassReadySurface(
                        shape = RoundedCornerShape(tokens.radius.compactCard),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        alpha = tokens.surface.interactiveAlpha,
                        highlighted = false
                    )
            )
        }

        if (settings.antiIgnorePowerEnabled && missingCorePowerPermissions) {
            item(key = "missing_power_permissions", contentType = "action") {
                val missingInteraction = remember { MutableInteractionSource() }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_permissions_title)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_permissions_desc))
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = tokens.semantic.warning
                        )
                    },
                    colors = transparentListItemColors,
                    modifier = Modifier
                        .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                        .snowPressFeedback(missingInteraction, pressedScale = 0.98f)
                        .glassReadySurface(
                            shape = RoundedCornerShape(tokens.radius.compactCard),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            alpha = tokens.surface.cardAlpha,
                            highlighted = true
                        )
                        .clickable(interactionSource = missingInteraction, indication = null) {
                            showAntiIgnorePermissionsDialog = true
                        }
                )
            }
        }

        // ── 5. Hilfe & Info ──────────────────────────────────────────────────────
        item(key = "header_help_info", contentType = "header") {
            Spacer(modifier = Modifier.height(tokens.spacing.md))
            SettingsSectionHeader(stringResource(R.string.settings_section_help_info))
        }

        item(key = "replay_onboarding", contentType = "action") {
            val onboardingInteraction = remember { MutableInteractionSource() }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_replay_onboarding_title)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_replay_onboarding_desc))
                },
                leadingContent = {
                    Icon(Icons.Outlined.AutoStories, contentDescription = null)
                },
                colors = transparentListItemColors,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                    .snowPressFeedback(onboardingInteraction, pressedScale = 0.98f)
                    .glassReadySurface(
                        shape = RoundedCornerShape(tokens.radius.compactCard),
                        alpha = tokens.surface.interactiveAlpha
                    )
                    .clickable(
                        interactionSource = onboardingInteraction,
                        indication = null,
                        onClick = onOpenOnboarding
                    )
            )
        }

        item(key = "replay_tutorial", contentType = "action") {
            val tutorialInteraction = remember { MutableInteractionSource() }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_replay_tutorial_title)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_replay_tutorial_desc))
                },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null)
                },
                colors = transparentListItemColors,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                    .snowPressFeedback(tutorialInteraction, pressedScale = 0.98f)
                    .glassReadySurface(
                        shape = RoundedCornerShape(tokens.radius.compactCard),
                        alpha = tokens.surface.interactiveAlpha
                    )
                    .clickable(
                        interactionSource = tutorialInteraction,
                        indication = null,
                        onClick = onStartTutorial
                    )
            )
        }

        item(key = "app_version_info", contentType = "info") {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_app_version_title)) },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.settings_app_version_value,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE
                        )
                    )
                },
                leadingContent = {
                    Icon(Icons.Outlined.Info, contentDescription = null)
                },
                colors = transparentListItemColors,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.md, vertical = 4.dp)
                    .glassReadySurface(
                        shape = RoundedCornerShape(tokens.radius.compactCard),
                        alpha = tokens.surface.interactiveAlpha
                    )
            )
        }
    }

    val isDialogVisible = activeDialog != ActiveSettingsDialog.NONE
    when (lastOpenedDialog) {
        ActiveSettingsDialog.NONE -> Unit

        ActiveSettingsDialog.LEAD_TIME -> {
            SingleChoiceDialog(
                visible = isDialogVisible,
                title = stringResource(R.string.settings_lead_time_title),
                options = listOf(0, 5, 10, 15, 30, 60),
                selected = settings.reminderLeadTimeMinutes,
                labelFor = { mins ->
                    if (mins == 0) {
                        stringResource(R.string.settings_lead_time_at_due_option)
                    } else {
                        stringResource(R.string.settings_lead_time_before_option, mins)
                    }
                },
                onSelect = { minutes ->
                    viewModel.onAction(SettingsAction.SetReminderLeadTimeMinutes(minutes))
                    activeDialog = ActiveSettingsDialog.NONE
                },
                onDismiss = { activeDialog = ActiveSettingsDialog.NONE }
            )
        }

        ActiveSettingsDialog.SNOOZE_MINUTES -> {
            SingleChoiceDialog(
                visible = isDialogVisible,
                title = stringResource(R.string.settings_default_snooze_title),
                options = listOf(5, 10, 15, 30, 60),
                selected = settings.defaultSnoozeMinutes,
                labelFor = { stringResource(R.string.label_minutes_full, it) },
                onSelect = { minutes ->
                    viewModel.onAction(SettingsAction.SetDefaultSnoozeMinutes(minutes))
                    activeDialog = ActiveSettingsDialog.NONE
                },
                onDismiss = { activeDialog = ActiveSettingsDialog.NONE }
            )
        }

        ActiveSettingsDialog.DISMISS_BEHAVIOR -> {
            SingleChoiceDialog(
                visible = isDialogVisible,
                title = stringResource(R.string.settings_dismiss_title),
                options = DismissBehavior.entries,
                selected = settings.dismissBehavior,
                labelFor = { behavior ->
                    when (behavior) {
                        DismissBehavior.MARK_IGNORED ->
                            stringResource(
                                R.string.settings_dismiss_ignored_option,
                                settings.scoreRules.ignoredPenaltyFor(TaskPriority.LOW),
                                settings.scoreRules.ignoredPenaltyFor(TaskPriority.HIGH)
                            )
                        DismissBehavior.DELETE_PERMANENTLY ->
                            stringResource(R.string.settings_dismiss_delete_option)
                    }
                },
                onSelect = { behavior ->
                    viewModel.onAction(SettingsAction.SetDismissBehavior(behavior))
                    activeDialog = ActiveSettingsDialog.NONE
                },
                onDismiss = { activeDialog = ActiveSettingsDialog.NONE }
            )
        }

        ActiveSettingsDialog.GRACE_PERIOD -> {
            SingleChoiceDialog(
                visible = isDialogVisible,
                title = stringResource(R.string.settings_grace_title),
                options = listOf(0, 5, 15, 30),
                selected = settings.scoreRules.missedGracePeriodMinutes,
                labelFor = { mins ->
                    if (mins == 0) {
                        stringResource(R.string.settings_grace_immediate_option)
                    } else {
                        stringResource(R.string.label_minutes_full, mins)
                    }
                },
                onSelect = { mins ->
                    viewModel.onAction(
                        SettingsAction.SetScoreRules(
                            settings.scoreRules.copy(missedGracePeriodMinutes = mins)
                        )
                    )
                    activeDialog = ActiveSettingsDialog.NONE
                },
                onDismiss = { activeDialog = ActiveSettingsDialog.NONE }
            )
        }

        ActiveSettingsDialog.SCORE_RULES -> {
            val presets = listOf(
                ScoreRulesConfig(
                    completedPoints = 1,
                    ignoredPenalty = -1,
                    missedPenalty = -1,
                    missedGracePeriodMinutes = settings.scoreRules.missedGracePeriodMinutes
                ),
                ScoreRulesConfig(
                    completedPoints = 1,
                    ignoredPenalty = -2,
                    missedPenalty = -2,
                    missedGracePeriodMinutes = settings.scoreRules.missedGracePeriodMinutes
                )
            )
            SingleChoiceDialog(
                visible = isDialogVisible,
                title = stringResource(R.string.settings_score_rules_title),
                options = presets,
                selected = settings.scoreRules,
                labelFor = { r ->
                    val summary = formatScoreRulesSummary(r)
                    if (r.ignoredPenalty <= -2 || r.missedPenalty <= -2) {
                        stringResource(R.string.settings_score_strict_option, summary)
                    } else {
                        stringResource(R.string.settings_score_standard_option, summary)
                    }
                },
                onSelect = { rules ->
                    viewModel.onAction(SettingsAction.SetScoreRules(rules))
                    activeDialog = ActiveSettingsDialog.NONE
                },
                onDismiss = { activeDialog = ActiveSettingsDialog.NONE }
            )
        }

        ActiveSettingsDialog.LANGUAGE -> {
            SingleChoiceDialog(
                visible = isDialogVisible,
                title = stringResource(R.string.settings_language_dialog_title),
                options = AppLanguage.entries,
                selected = settings.appLanguage,
                labelFor = { lang -> "${lang.flagEmoji} ${lang.nativeName}" },
                onSelect = { language ->
                    viewModel.onAction(SettingsAction.SetAppLanguage(language))
                    activeDialog = ActiveSettingsDialog.NONE
                },
                onDismiss = { activeDialog = ActiveSettingsDialog.NONE }
            )
        }

        ActiveSettingsDialog.TAB_TRANSITION -> {
            SingleChoiceDialog(
                visible = isDialogVisible,
                title = stringResource(R.string.settings_tab_transition_dialog_title),
                options = TabTransitionMode.entries,
                selected = settings.tabTransitionMode,
                labelFor = { mode ->
                    stringResource(
                        when (mode) {
                            TabTransitionMode.ANIMATED -> R.string.settings_tab_transition_animated_option
                            TabTransitionMode.INSTANT -> R.string.settings_tab_transition_instant_option
                        }
                    )
                },
                onSelect = { mode ->
                    viewModel.onAction(SettingsAction.SetTabTransitionMode(mode))
                    activeDialog = ActiveSettingsDialog.NONE
                },
                onDismiss = { activeDialog = ActiveSettingsDialog.NONE }
            )
        }

        ActiveSettingsDialog.WIDGET -> {
            WidgetSettingsDialog(
                visible = isDialogVisible,
                colorMode = settings.widgetColorMode,
                surfaceStyle = settings.widgetSurfaceStyle,
                appearance = settings.appearance,
                systemDarkTheme = isSystemInDarkTheme(),
                onSelectColorMode = { mode ->
                    viewModel.onAction(SettingsAction.SetWidgetColorMode(mode))
                },
                onSelectSurfaceStyle = { style ->
                    viewModel.onAction(SettingsAction.SetWidgetSurfaceStyle(style))
                },
                onDismiss = { activeDialog = ActiveSettingsDialog.NONE }
            )
        }
    }

    AntiIgnorePermissionsDialog(
        visible = showAntiIgnorePermissionsDialog,
        hasNotificationPermission = hasNotificationPermission,
        canScheduleExactAlarms = uiState.canScheduleExactAlarms,
        canUseFullScreenIntent = uiState.canUseFullScreenIntent,
        isIgnoringBatteryOptimization = isIgnoringBatteryOptimization,
        hasDndPolicyAccess = hasDndPolicyAccess,
        onRequestNotificationPermission = {
            val runtimeMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            if (runtimeMissing) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                runCatching { context.startActivity(intent) }
            }
        },
        onOpenExactAlarmSettings = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                runCatching { context.startActivity(intent) }
            }
        },
        onOpenFullScreenIntentSettings = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                runCatching { context.startActivity(intent) }
            }
        },
        onOpenBatteryOptimizationSettings = {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            if (runCatching { context.startActivity(intent) }.isFailure) {
                runCatching { context.startActivity(fallback) }
            }
        },
        onOpenDndPolicySettings = {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            runCatching { context.startActivity(intent) }
        },
        onDismiss = { showAntiIgnorePermissionsDialog = false }
    )
}

@Composable
private fun AntiIgnorePermissionsDialog(
    visible: Boolean,
    hasNotificationPermission: Boolean,
    canScheduleExactAlarms: Boolean,
    canUseFullScreenIntent: Boolean,
    isIgnoringBatteryOptimization: Boolean,
    hasDndPolicyAccess: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenIntentSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onOpenDndPolicySettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = SnowDosThemeTokens.current
    SnowFrostedDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        maxWidth = 440.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(tokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
        ) {
            Text(
                text = stringResource(R.string.permissions_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.permissions_dialog_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PermissionCheckCard(
                title = stringResource(R.string.permissions_notif_title),
                subtitle = stringResource(R.string.permissions_notif_desc),
                granted = hasNotificationPermission,
                actionLabel = stringResource(R.string.action_allow),
                onAction = onRequestNotificationPermission
            )

            PermissionCheckCard(
                title = stringResource(R.string.permissions_exact_title),
                subtitle = stringResource(R.string.permissions_exact_desc),
                granted = canScheduleExactAlarms,
                actionLabel = stringResource(R.string.action_open),
                onAction = onOpenExactAlarmSettings
            )

            PermissionCheckCard(
                title = stringResource(R.string.permissions_fullscreen_title),
                subtitle = stringResource(R.string.permissions_fullscreen_desc),
                granted = canUseFullScreenIntent,
                actionLabel = stringResource(R.string.action_open),
                onAction = onOpenFullScreenIntentSettings
            )

            PermissionCheckCard(
                title = stringResource(R.string.permissions_battery_title),
                subtitle = stringResource(R.string.permissions_battery_desc),
                granted = isIgnoringBatteryOptimization,
                actionLabel = stringResource(R.string.action_check),
                onAction = onOpenBatteryOptimizationSettings
            )

            PermissionCheckCard(
                title = stringResource(R.string.permissions_dnd_title),
                subtitle = stringResource(R.string.permissions_dnd_desc),
                granted = hasDndPolicyAccess,
                actionLabel = stringResource(R.string.action_open),
                onAction = onOpenDndPolicySettings
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SnowGlassButton(
                    highlighted = true,
                    onClick = onDismiss
                ) {
                    Text(
                        text = stringResource(R.string.action_done),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCheckCard(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    val tokens = SnowDosThemeTokens.current
    SnowGlassCard(
        shape = RoundedCornerShape(tokens.radius.compactCard),
        alpha = tokens.surface.interactiveAlpha,
        highlighted = granted,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = if (granted) tokens.semantic.positive else tokens.semantic.warning,
                modifier = Modifier.size(22.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!granted) {
                SnowGlassButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = actionLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.label_active),
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.semantic.positive
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    badge: String? = null,
    subtitle: String? = null
) {
    val tokens = SnowDosThemeTokens.current
    Column(
        modifier = Modifier.padding(
            horizontal = tokens.spacing.md,
            vertical = tokens.spacing.xs
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(tokens.radius.pill),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun <T> SingleChoiceDialog(
    visible: Boolean,
    title: String,
    options: List<T>,
    selected: T,
    labelFor: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = SnowDosThemeTokens.current
    SnowFrostedDialog(
        visible = visible,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(tokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    val optionInteraction = remember { MutableInteractionSource() }
                    val rowShape = RoundedCornerShape(14.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .snowPressFeedback(optionInteraction, pressedScale = 0.98f)
                            .clip(rowShape)
                            .clickable(
                                interactionSource = optionInteraction,
                                indication = null
                            ) { onSelect(option) }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option) }
                        )
                        Text(
                            text = labelFor(option),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SnowGlassButton(
                    onClick = onDismiss
                ) {
                    Text(
                        text = stringResource(R.string.action_close),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetSettingsDialog(
    visible: Boolean,
    colorMode: WidgetColorMode,
    surfaceStyle: WidgetSurfaceStyle,
    appearance: ThemeAppearanceConfig,
    systemDarkTheme: Boolean,
    onSelectColorMode: (WidgetColorMode) -> Unit,
    onSelectSurfaceStyle: (WidgetSurfaceStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = SnowDosThemeTokens.current
    SnowFrostedDialog(
        visible = visible,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(tokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
        ) {
            Text(
                text = stringResource(R.string.widget_config_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            WidgetAppearanceControls(
                colorMode = colorMode,
                surfaceStyle = surfaceStyle,
                appearance = appearance,
                systemDarkTheme = systemDarkTheme,
                onSelectColorMode = onSelectColorMode,
                onSelectSurfaceStyle = onSelectSurfaceStyle,
                blurScope = SnowBlurScope.OVERLAY
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SnowGlassButton(
                    onClick = onDismiss
                ) {
                    Text(
                        text = stringResource(R.string.action_done),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun formatScoreRulesSummary(rules: ScoreRulesConfig): String {
    val minPos = rules.completedPointsFor(TaskPriority.LOW)
    val maxPos = rules.completedPointsFor(TaskPriority.HIGH)
    val minPen = rules.missedPenaltyFor(TaskPriority.LOW)
    val maxPen = rules.missedPenaltyFor(TaskPriority.HIGH)
    return if (minPos == -minPen && maxPos == -maxPen) {
        stringResource(R.string.settings_score_summary_symmetrical, minPos, maxPos)
    } else {
        stringResource(R.string.settings_score_summary_asymmetric, minPos, maxPos, minPen, maxPen)
    }
}
