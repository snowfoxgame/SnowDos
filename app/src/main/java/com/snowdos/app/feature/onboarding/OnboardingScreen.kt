package com.snowdos.app.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snowdos.app.R
import com.snowdos.app.core.common.labelRes
import com.snowdos.app.core.common.subtitleRes
import com.snowdos.app.core.designsystem.SnowDosThemeTokens
import com.snowdos.app.core.designsystem.ThemePreset
import com.snowdos.app.core.designsystem.ThemeRegistry
import com.snowdos.app.core.designsystem.components.SnowGlassButton
import com.snowdos.app.core.designsystem.components.SnowGlassCard
import com.snowdos.app.core.designsystem.components.glassReadySurface
import com.snowdos.app.core.designsystem.components.snowPressFeedback
import com.snowdos.app.domain.model.AppLanguage
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val ONBOARDING_PAGE_COUNT = 5

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onFinishWithTutorial: () -> Unit = onFinished,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tokens = SnowDosThemeTokens.current
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == ONBOARDING_PAGE_COUNT - 1

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = tokens.spacing.md)
    ) {
        // Top Language Pill (left) + Skip bar (right, pages 1..4)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SnowGlassButton(
                onClick = { viewModel.toggleLanguage() },
                enabled = !uiState.isFinishing
            ) {
                Text(
                    text = "${uiState.appLanguage.flagEmoji} ${stringResource(R.string.onboarding_language_pill)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = !isLastPage,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(180))
            ) {
                SnowGlassButton(
                    onClick = { viewModel.skipOnboarding(onFinished) },
                    enabled = !uiState.isFinishing
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 2,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                .absoluteValue
                .coerceIn(0f, 1f)
            val isVisible = pagerState.currentPage == page

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = 1f - 0.04f * pageOffset
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - 0.35f * pageOffset
                    }
            ) {
                when (page) {
                    0 -> WelcomeLanguagePage(
                        visible = isVisible,
                        selectedLanguage = uiState.appLanguage,
                        onSelectLanguage = viewModel::setLanguage
                    )
                    1 -> ThemeSelectionPage(
                        visible = isVisible,
                        selectedThemeId = uiState.selectedThemeId,
                        onSelectTheme = viewModel::selectThemePreset
                    )
                    2 -> TemplatesPage(
                        visible = isVisible,
                        selectedTemplateIds = uiState.selectedTemplateIds,
                        onToggleTemplate = viewModel::toggleTemplate
                    )
                    3 -> AboutPage(visible = isVisible)
                    4 -> ReadyPage(
                        visible = isVisible,
                        selectedCount = uiState.selectedTemplateIds.size,
                        isFinishing = uiState.isFinishing,
                        onStartTutorial = { viewModel.completeOnboarding(onFinishWithTutorial) },
                        onStartWithoutTutorial = { viewModel.completeOnboarding(onFinished) }
                    )
                }
            }
        }

        // Bottom step dots & navigation row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = tokens.spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = pagerState.currentPage > 0,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(150))
                ) {
                    SnowGlassButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = pagerState.currentPage - 1,
                                    animationSpec = spring(
                                        dampingRatio = 0.85f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        },
                        enabled = !uiState.isFinishing
                    ) {
                        Text(
                            text = stringResource(R.string.action_back),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            StepIndicatorDots(
                pageCount = ONBOARDING_PAGE_COUNT,
                currentPage = pagerState.currentPage
            )

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isLastPage,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(150))
                ) {
                    SnowGlassButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = pagerState.currentPage + 1,
                                    animationSpec = spring(
                                        dampingRatio = 0.85f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        },
                        enabled = !uiState.isFinishing,
                        highlighted = true
                    ) {
                        Text(
                            text = stringResource(R.string.action_next),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicatorDots(
    pageCount: Int,
    currentPage: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (selected) 24.dp else 8.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "dotWidth"
            )
            val color by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                },
                animationSpec = tween(240),
                label = "dotColor"
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun WelcomeLanguagePage(
    visible: Boolean,
    selectedLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit
) {
    val tokens = SnowDosThemeTokens.current
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) showContent = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(340)) + slideInVertically(tween(340)) { it / 5 }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_welcome_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.onboarding_welcome_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = tokens.spacing.sm)
                )

                Spacer(modifier = Modifier.height(tokens.spacing.sm))

                Text(
                    text = stringResource(R.string.onboarding_language_prompt),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
                ) {
                    AppLanguage.entries.forEach { language ->
                        LanguageChoiceCard(
                            language = language,
                            selected = language == selectedLanguage,
                            onClick = { onSelectLanguage(language) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageChoiceCard(
    language: AppLanguage,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = SnowDosThemeTokens.current
    val shape = RoundedCornerShape(tokens.radius.card)
    val interaction = remember { MutableInteractionSource() }
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(180),
        label = "langCardBg"
    )

    Surface(
        shape = shape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .snowPressFeedback(interaction, pressedScale = 0.97f)
            .clip(shape)
            .glassReadySurface(
                shape = shape,
                containerColor = containerColor,
                alpha = if (selected) tokens.surface.selectedAlpha else tokens.surface.cardAlpha,
                highlighted = selected,
                borderWidth = if (tokens.surface.usePixelRetroStyle || !selected) {
                    tokens.surface.borderWidth
                } else {
                    1.5.dp
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = language.flagEmoji,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = language.nativeName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.onboarding_selected_cd),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ThemeSelectionPage(
    visible: Boolean,
    selectedThemeId: String,
    onSelectTheme: (String) -> Unit
) {
    val tokens = SnowDosThemeTokens.current
    var showHeader by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) showHeader = true
    }

    val presets = ThemeRegistry.presets
    val rowCount = (presets.size + 1) / 2

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = tokens.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
    ) {
        item {
            AnimatedVisibility(
                visible = showHeader,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 5 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.onboarding_theme_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.onboarding_theme_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(count = rowCount, key = { rowIdx -> "theme_row_$rowIdx" }) { rowIdx ->
            val left = presets[rowIdx * 2]
            val right = presets.getOrNull(rowIdx * 2 + 1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
            ) {
                OnboardingPresetTile(
                    preset = left,
                    selected = left.id == selectedThemeId,
                    onClick = { onSelectTheme(left.id) },
                    modifier = Modifier.weight(1f)
                )
                if (right != null) {
                    OnboardingPresetTile(
                        preset = right,
                        selected = right.id == selectedThemeId,
                        onClick = { onSelectTheme(right.id) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun OnboardingPresetTile(
    preset: ThemePreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = SnowDosThemeTokens.current
    val shape = RoundedCornerShape(tokens.radius.compactCard)
    val interaction = remember { MutableInteractionSource() }
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(180),
        label = "presetTileBg"
    )

    Surface(
        shape = shape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .snowPressFeedback(interaction, pressedScale = 0.97f)
            .clip(shape)
            .glassReadySurface(
                shape = shape,
                containerColor = containerColor,
                alpha = if (selected) tokens.surface.selectedAlpha else tokens.surface.cardAlpha,
                highlighted = selected,
                borderWidth = if (tokens.surface.usePixelRetroStyle || !selected) {
                    tokens.surface.borderWidth
                } else {
                    1.5.dp
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(tokens.radius.compactCard))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                preset.previewSurface,
                                preset.previewSurface.copy(alpha = 0.85f),
                                preset.previewPrimary.copy(alpha = 0.25f)
                            )
                        )
                    )
            ) {
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
                            .background(preset.previewPrimary)
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
                        contentDescription = stringResource(R.string.onboarding_selected_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(18.dp)
                    )
                }
            }

            Text(
                text = preset.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(preset.subtitleRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TemplatesPage(
    visible: Boolean,
    selectedTemplateIds: Set<String>,
    onToggleTemplate: (String) -> Unit
) {
    val tokens = SnowDosThemeTokens.current
    var showHeader by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) showHeader = true
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = tokens.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
    ) {
        item {
            AnimatedVisibility(
                visible = showHeader,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 5 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.onboarding_templates_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.onboarding_templates_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        OnboardingCategory.entries.forEach { category ->
            val categoryTemplates = OnboardingTemplateCatalog.templates.filter { it.category == category }
            item(key = "header_${category.name}") {
                Text(
                    text = stringResource(category.titleRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = tokens.spacing.xs)
                )
            }
            items(
                count = (categoryTemplates.size + 1) / 2,
                key = { rowIdx -> "${category.name}_row_$rowIdx" }
            ) { rowIdx ->
                val left = categoryTemplates[rowIdx * 2]
                val right = categoryTemplates.getOrNull(rowIdx * 2 + 1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
                ) {
                    TemplateOptionCard(
                        template = left,
                        selected = left.id in selectedTemplateIds,
                        onClick = { onToggleTemplate(left.id) },
                        modifier = Modifier.weight(1f)
                    )
                    if (right != null) {
                        TemplateOptionCard(
                            template = right,
                            selected = right.id in selectedTemplateIds,
                            onClick = { onToggleTemplate(right.id) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateOptionCard(
    template: OnboardingTaskTemplate,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = SnowDosThemeTokens.current
    val shape = RoundedCornerShape(tokens.radius.compactCard)
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(180),
        label = "templateCardBg"
    )

    val cardInteraction = remember { MutableInteractionSource() }
    Surface(
        shape = shape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .snowPressFeedback(cardInteraction, pressedScale = 0.97f)
            .clip(shape)
            .glassReadySurface(
                shape = shape,
                containerColor = containerColor,
                alpha = if (selected) tokens.surface.selectedAlpha else tokens.surface.cardAlpha,
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
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = template.icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.onboarding_selected_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = stringResource(template.titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "↻ ${stringResource(template.recurrence.labelRes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AboutPage(visible: Boolean) {
    val tokens = SnowDosThemeTokens.current
    var showCard by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) showCard = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = showCard,
            enter = fadeIn(tween(360)) + slideInVertically(tween(360)) { it / 5 }
        ) {
            val aboutShape = RoundedCornerShape(tokens.radius.card)
            Surface(
                shape = aboutShape,
                color = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .glassReadySurface(
                        shape = aboutShape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        alpha = tokens.surface.cardAlpha
                    )
            ) {
                Column(
                    modifier = Modifier.padding(tokens.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.onboarding_about_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = stringResource(R.string.onboarding_about_p1),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = stringResource(R.string.onboarding_about_p2),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = stringResource(R.string.onboarding_about_p3),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val supportShape = RoundedCornerShape(tokens.radius.compactCard)
                    Surface(
                        shape = supportShape,
                        color = Color.Transparent,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassReadySurface(
                                shape = supportShape,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                alpha = tokens.surface.cardAlpha
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(tokens.spacing.md),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_feedback_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.onboarding_feedback_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyPage(
    visible: Boolean,
    selectedCount: Int,
    isFinishing: Boolean,
    onStartTutorial: () -> Unit,
    onStartWithoutTutorial: () -> Unit
) {
    val tokens = SnowDosThemeTokens.current
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) showContent = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(340)) + slideInVertically(tween(340)) { it / 5 }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_ready_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.onboarding_ready_subtitle),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (selectedCount > 0) {
                        pluralStringResource(
                            R.plurals.onboarding_ready_selected_summary,
                            selectedCount,
                            selectedCount
                        )
                    } else {
                        stringResource(R.string.onboarding_ready_empty_summary)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(tokens.spacing.xs))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
                ) {
                    SnowGlassButton(
                        onClick = onStartTutorial,
                        enabled = !isFinishing,
                        highlighted = true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_start_tutorial_cta),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    SnowGlassButton(
                        onClick = onStartWithoutTutorial,
                        enabled = !isFinishing,
                        highlighted = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_start_no_tutorial_cta),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(tokens.spacing.xs))

                SnowGlassCard(
                    shape = RoundedCornerShape(tokens.radius.compactCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(tokens.spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.onboarding_contact_placeholder),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
