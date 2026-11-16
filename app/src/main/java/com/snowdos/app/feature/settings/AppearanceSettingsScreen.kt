package com.snowdos.app.feature.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snowdos.app.R
import com.snowdos.app.core.common.labelRes
import com.snowdos.app.core.common.localizedTitle
import com.snowdos.app.core.common.resolve
import com.snowdos.app.core.common.subtitleRes
import com.snowdos.app.core.designsystem.SnowDosThemeTokens
import com.snowdos.app.core.designsystem.ThemeCategory
import com.snowdos.app.core.designsystem.ThemePreset
import com.snowdos.app.core.designsystem.ThemeRegistry
import com.snowdos.app.core.designsystem.components.ColorWheelPicker
import com.snowdos.app.core.designsystem.components.SnowGlassButton
import com.snowdos.app.core.designsystem.components.SnowGlassCard
import com.snowdos.app.core.designsystem.components.SnowGlassChip
import com.snowdos.app.core.designsystem.components.SnowGlassSnackbarHost
import com.snowdos.app.core.designsystem.components.SnowSegmentOption
import com.snowdos.app.core.designsystem.components.SnowSegmentedPill
import com.snowdos.app.core.designsystem.components.glassReadySurface
import com.snowdos.app.core.designsystem.components.rememberDecodedBackgroundBitmap
import com.snowdos.app.core.designsystem.components.snowPressFeedback
import com.snowdos.app.core.designsystem.parseHexColorOrNull
import com.snowdos.app.domain.model.EightBitVariant
import com.snowdos.app.domain.model.WeatherThemeStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appearance = uiState.appearance
    val tokens = SnowDosThemeTokens.current
    val snackbarHostState = remember { SnackbarHostState() }
    val currentContext by rememberUpdatedState(LocalContext.current)
    var askedLocationOnce by rememberSaveable { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf<ThemeCategory?>(null) }
    val categoryCounts = remember {
        ThemeCategory.entries.associateWith { category ->
            ThemeRegistry.presets.count { it.category == category }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshWeather(forceRefresh = true)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackgroundFromUri(uri)
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg.resolve(currentContext))
        viewModel.consumeMessage()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnowGlassSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance_title)) },
                navigationIcon = {
                    val backInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = onBack,
                        interactionSource = backInteraction,
                        modifier = Modifier.snowPressFeedback(backInteraction, pressedScale = 0.92f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = tokens.spacing.md),
            contentPadding = PaddingValues(
                top = tokens.spacing.sm,
                bottom = tokens.spacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
        ) {
            val effectivePresetId = appearance.effectivePresetId()
            val activeSeasonalPreset = ThemeRegistry.findPreset(effectivePresetId)

            item(key = "auto_seasonal_toggle") {
                    SnowGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        highlighted = appearance.autoSeasonalThemeEnabled
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(tokens.spacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = tokens.spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.appearance_auto_seasonal_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(R.string.appearance_auto_seasonal_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (appearance.autoSeasonalThemeEnabled) {
                                    Text(
                                        text = stringResource(
                                            R.string.appearance_auto_seasonal_active_fmt,
                                            activeSeasonalPreset.localizedTitle(currentContext)
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Switch(
                                checked = appearance.autoSeasonalThemeEnabled,
                                onCheckedChange = viewModel::setAutoSeasonalTheme
                            )
                        }
                    }
                }

                item(key = "preset_category_filter", contentType = "category_filter") {
                    val primaryTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    val primaryLabel = MaterialTheme.colorScheme.primary
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
                    ) {
                        item(key = "category_all") {
                            SnowGlassChip(
                                label = "${stringResource(R.string.appearance_category_all)} (${ThemeRegistry.presets.size})",
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                selectedContainerColor = primaryTint,
                                selectedLabelColor = primaryLabel
                            )
                        }
                        items(ThemeCategory.entries, key = { it.name }) { category ->
                            val count = categoryCounts[category] ?: 0
                            SnowGlassChip(
                                label = "${stringResource(category.labelRes)} ($count)",
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                selectedContainerColor = primaryTint,
                                selectedLabelColor = primaryLabel
                            )
                        }
                    }
                }

                val filteredPresets = if (selectedCategory == null) {
                    ThemeRegistry.presets
                } else {
                    ThemeRegistry.presets.filter { it.category == selectedCategory }
                }
                val presetRows = filteredPresets.chunked(2)
                items(
                    items = presetRows,
                    key = { row -> row.joinToString("_") { it.id } },
                    contentType = { "preset_row" }
                ) { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
                    ) {
                        rowPresets.forEach { preset ->
                            PresetOptionCard(
                                preset = preset,
                                thumbnailUri = uiState.presetThumbnails[preset.id],
                                selected = effectivePresetId == preset.id,
                                gamingAccentHex = appearance.gamingAccentHex,
                                eightBitVariant = appearance.eightBitVariant,
                                onSelectEightBitVariant = viewModel::selectEightBitVariant,
                                onClick = {
                                    viewModel.selectPreset(preset.id)
                                    if (preset.id == "weather_time" &&
                                        !uiState.hasLocationPermission &&
                                        !askedLocationOnce
                                    ) {
                                        askedLocationOnce = true
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowPresets.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (effectivePresetId == "interactive_bubbles") {
                    item(key = "bubbles_battery_hint") {
                        SnowGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.appearance_bubbles_battery_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(tokens.spacing.md)
                            )
                        }
                    }
                }

                if (effectivePresetId == "weather_time") {
                    item(key = "weather_style_picker") {
                        SnowGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(tokens.spacing.md),
                                verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
                            ) {
                                Text(
                                    text = stringResource(R.string.appearance_weather_style_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                SnowSegmentedPill(
                                    options = listOf(
                                        SnowSegmentOption(
                                            WeatherThemeStyle.CUTE_2D,
                                            stringResource(R.string.appearance_weather_cute_2d)
                                        ),
                                        SnowSegmentOption(
                                            WeatherThemeStyle.REALISTIC,
                                            stringResource(R.string.appearance_weather_realistic)
                                        )
                                    ),
                                    selected = appearance.weatherStyle,
                                    onSelect = viewModel::selectWeatherStyle
                                )
                                Text(
                                    text = stringResource(
                                        if (uiState.hasLocationPermission) {
                                            R.string.appearance_weather_location_granted
                                        } else {
                                            R.string.appearance_weather_location_fallback
                                        }
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                SnowGlassButton(
                                    onClick = {
                                        if (uiState.hasLocationPermission) {
                                            viewModel.refreshWeather(forceRefresh = true)
                                        } else {
                                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = if (uiState.hasLocationPermission) {
                                            Icons.Outlined.Refresh
                                        } else {
                                            Icons.Outlined.LocationOn
                                        },
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.size(tokens.spacing.xs))
                                    Text(
                                        text = stringResource(
                                            if (uiState.hasLocationPermission) {
                                                R.string.appearance_weather_refresh
                                            } else {
                                                R.string.appearance_weather_grant_location
                                            }
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                if (effectivePresetId == "gaming") {
                    item(key = "gaming_led_picker") {
                        SnowGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(tokens.spacing.md),
                                verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
                            ) {
                                Text(
                                    text = stringResource(R.string.appearance_gaming_led_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(R.string.appearance_gaming_led_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                ColorWheelPicker(
                                    selectedHex = appearance.gamingAccentHex,
                                    onColorHexChanged = viewModel::updateGamingAccentHex
                                )
                            }
                        }
                    }
                }

                if (effectivePresetId == "8bit_cute") {
                    item(key = "eight_bit_variant_picker") {
                        SnowGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(tokens.spacing.md),
                                verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
                            ) {
                                Text(
                                    text = stringResource(R.string.appearance_8bit_variant_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                SnowSegmentedPill(
                                    options = listOf(
                                        SnowSegmentOption(
                                            EightBitVariant.PASTEL_PINK,
                                            stringResource(R.string.appearance_8bit_pink)
                                        ),
                                        SnowSegmentOption(
                                            EightBitVariant.PASTEL_BLUE,
                                            stringResource(R.string.appearance_8bit_blue)
                                        )
                                    ),
                                    selected = appearance.eightBitVariant,
                                    onSelect = viewModel::selectEightBitVariant
                                )
                            }
                        }
                    }
                }

            item(key = "glass_opacity_slider") {
                val glassCardShape = RoundedCornerShape(tokens.radius.card)
                Surface(
                    shape = glassCardShape,
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassReadySurface(
                            shape = glassCardShape,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            alpha = tokens.surface.cardAlpha
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(tokens.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.appearance_glass_opacity),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = tokens.spacing.sm)
                            )
                            Text(
                                text = "${(appearance.glassOpacity * 100f).roundToInt()} %",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = stringResource(R.string.appearance_glass_opacity_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = appearance.glassOpacity,
                            onValueChange = viewModel::updateGlassOpacity,
                            valueRange = 0.12f..0.48f
                        )
                    }
                }
            }

            item(key = "background_picker") {
                BackgroundPickerCard(
                    backgroundImageUri = appearance.backgroundImageUri,
                    isImporting = uiState.isImportingBackground,
                    onPickImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onResetBackground = viewModel::resetBackground
                )
            }
        }
    }
}

@Composable
private fun PresetOptionCard(
    preset: ThemePreset,
    thumbnailUri: String?,
    selected: Boolean,
    gamingAccentHex: String,
    eightBitVariant: EightBitVariant,
    onSelectEightBitVariant: (EightBitVariant) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = SnowDosThemeTokens.current
    val thumbnailBitmap = rememberDecodedBackgroundBitmap(
        uriString = thumbnailUri,
        maxWidthPx = 400,
        maxHeightPx = 260
    )
    val cardShape = RoundedCornerShape(tokens.radius.card)
    val cardInteraction = remember { MutableInteractionSource() }
    val previewSurfaceColor = when {
        preset.id == "8bit_cute" && eightBitVariant == EightBitVariant.PASTEL_BLUE -> Color(0xFFECF5FF)
        else -> preset.previewSurface
    }
    val previewPrimaryColor = when {
        preset.id == "gaming" -> parseHexColorOrNull(gamingAccentHex) ?: preset.previewPrimary
        preset.id == "8bit_cute" && eightBitVariant == EightBitVariant.PASTEL_BLUE -> Color(0xFF2B6FD6)
        else -> preset.previewPrimary
    }

    Surface(
        shape = cardShape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .snowPressFeedback(cardInteraction, pressedScale = 0.97f)
            .clip(cardShape)
            .glassReadySurface(
                shape = cardShape,
                containerColor = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                alpha = if (selected) {
                    tokens.surface.selectedAlpha
                } else {
                    tokens.surface.cardAlpha
                },
                highlighted = selected,
                borderWidth = if (tokens.surface.usePixelRetroStyle || !selected) {
                    tokens.surface.borderWidth
                } else {
                    1.5.dp
                }
            )
            .clickable(interactionSource = cardInteraction, indication = null, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(tokens.radius.compactCard))
                    .background(previewSurfaceColor)
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = if (preset.id.startsWith("8bit_")) {
                            FilterQuality.None
                        } else {
                            FilterQuality.Low
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(previewPrimaryColor)
                            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(preset.previewSecondary)
                            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    )
                }

                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.label_active),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(20.dp)
                    )
                }
            }

            val context = LocalContext.current
            Text(
                text = preset.localizedTitle(context),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Text(
                text = stringResource(preset.subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            if (preset.id == "8bit_cute") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EightBitMiniVariantChip(
                        label = stringResource(R.string.appearance_8bit_pink_short),
                        dotColor = Color(0xFFFF8CB8),
                        selected = eightBitVariant == EightBitVariant.PASTEL_PINK,
                        onClick = { onSelectEightBitVariant(EightBitVariant.PASTEL_PINK) },
                        modifier = Modifier.weight(1f)
                    )
                    EightBitMiniVariantChip(
                        label = stringResource(R.string.appearance_8bit_blue_short),
                        dotColor = Color(0xFF7BB8FF),
                        selected = eightBitVariant == EightBitVariant.PASTEL_BLUE,
                        onClick = { onSelectEightBitVariant(EightBitVariant.PASTEL_BLUE) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EightBitMiniVariantChip(
    label: String,
    dotColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = SnowDosThemeTokens.current
    val shape = RoundedCornerShape(tokens.radius.chip)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BackgroundPickerCard(
    backgroundImageUri: String?,
    isImporting: Boolean,
    onPickImage: () -> Unit,
    onResetBackground: () -> Unit
) {
    val tokens = SnowDosThemeTokens.current
    val previewBitmap = rememberDecodedBackgroundBitmap(
        uriString = backgroundImageUri,
        maxWidthPx = 900,
        maxHeightPx = 420
    )

    val buttonShape = RoundedCornerShape(tokens.radius.pill)

    SnowGlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
        ) {
            Text(
                text = stringResource(R.string.appearance_bg_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    if (backgroundImageUri != null) R.string.appearance_bg_active_desc
                    else R.string.appearance_bg_default_desc
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            previewBitmap?.let { bmp ->
                Spacer(modifier = Modifier.height(2.dp))
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = stringResource(R.string.appearance_bg_preview_cd),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(tokens.radius.compactCard))
                )
            }

            SnowGlassButton(
                onClick = onPickImage,
                enabled = !isImporting,
                highlighted = true,
                shape = buttonShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(
                        if (isImporting) R.string.appearance_bg_importing
                        else R.string.appearance_bg_pick_cta
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (backgroundImageUri != null) {
                SnowGlassButton(
                    onClick = onResetBackground,
                    shape = buttonShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.appearance_bg_reset_cta),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
