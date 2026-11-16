package com.snowdos.app.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snowdos.app.AppThemeViewModel
import com.snowdos.app.R
import com.snowdos.app.core.common.ProvideAppLanguage
import com.snowdos.app.core.designsystem.SnowDosTheme
import com.snowdos.app.core.designsystem.SnowDosThemeTokens
import com.snowdos.app.core.designsystem.components.AppBackgroundContainer
import com.snowdos.app.core.designsystem.components.SnowGlassButton
import com.snowdos.app.core.designsystem.components.SnowGlassCard
import com.snowdos.app.domain.model.WidgetColorMode
import com.snowdos.app.domain.model.WidgetSurfaceStyle
import com.snowdos.app.domain.repository.SettingsRepository
import com.snowdos.app.domain.strategy.WidgetUpdateTrigger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var widgetUpdateTrigger: WidgetUpdateTrigger

    private val themeViewModel: AppThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(
            RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )

        enableEdgeToEdge()

        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            LaunchedEffect(systemDarkTheme) {
                themeViewModel.updateSystemDarkTheme(systemDarkTheme)
            }
            val themeState by themeViewModel.themeState.collectAsStateWithLifecycle()

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

            var selectedColorMode by remember { mutableStateOf(WidgetColorMode.FOLLOW_APP) }
            var selectedSurfaceStyle by remember { mutableStateOf(WidgetSurfaceStyle.GLASS) }
            var isLoaded by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                val initial = settingsRepository.observeSettings().first()
                selectedColorMode = initial.widgetColorMode
                selectedSurfaceStyle = initial.widgetSurfaceStyle
                isLoaded = true
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
                        val tokens = SnowDosThemeTokens.current
                        val scope = rememberCoroutineScope()

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .safeDrawingPadding()
                                .verticalScroll(rememberScrollState())
                                .padding(tokens.spacing.md),
                            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
                        ) {
                            Text(
                                text = stringResource(R.string.widget_config_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.widget_config_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (isLoaded) {
                                SnowGlassCard(
                                    shape = RoundedCornerShape(tokens.radius.card),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(tokens.spacing.md),
                                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
                                    ) {
                                        WidgetAppearanceControls(
                                            colorMode = selectedColorMode,
                                            surfaceStyle = selectedSurfaceStyle,
                                            appearance = themeState.appearance,
                                            systemDarkTheme = systemDarkTheme,
                                            onSelectColorMode = { selectedColorMode = it },
                                            onSelectSurfaceStyle = { selectedSurfaceStyle = it },
                                            appBackgroundUri = themeState.activeBackgroundUri
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f, fill = false))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { finish() }) {
                                    Text(
                                        text = stringResource(R.string.action_cancel),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                SnowGlassButton(
                                    onClick = {
                                        scope.launch {
                                            settingsRepository.setWidgetColorMode(selectedColorMode)
                                            settingsRepository.setWidgetSurfaceStyle(selectedSurfaceStyle)
                                            widgetUpdateTrigger.updateAll()
                                            val resultIntent = Intent().putExtra(
                                                AppWidgetManager.EXTRA_APPWIDGET_ID,
                                                appWidgetId
                                            )
                                            setResult(RESULT_OK, resultIntent)
                                            finish()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.widget_config_save),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
