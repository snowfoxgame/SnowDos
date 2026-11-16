package com.snowdos.app.feature.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.snowdos.app.R
import com.snowdos.app.core.designsystem.SnowDosThemeTokens
import com.snowdos.app.core.designsystem.components.FloatingNavItem
import com.snowdos.app.core.designsystem.components.FloatingPillNavBar
import com.snowdos.app.core.designsystem.components.LocalAppHazeState
import com.snowdos.app.core.designsystem.components.SnowBlurScope
import com.snowdos.app.core.designsystem.components.glassReadySurface
import com.snowdos.app.core.designsystem.components.snowPressFeedback
import com.snowdos.app.domain.model.TabTransitionMode
import com.snowdos.app.feature.account.AccountScreen
import com.snowdos.app.feature.onboarding.OnboardingScreen
import com.snowdos.app.feature.settings.AppearanceSettingsScreen
import com.snowdos.app.feature.settings.SettingsScreen
import com.snowdos.app.feature.stats.StatsScreen
import com.snowdos.app.feature.tasks.TasksAction
import com.snowdos.app.feature.tasks.TasksScreen
import com.snowdos.app.feature.tasks.TasksViewModel
import com.snowdos.app.feature.tutorial.LocalTutorialTargetRegistry
import com.snowdos.app.feature.tutorial.SpotlightTutorialOverlay
import com.snowdos.app.feature.tutorial.TutorialStep
import com.snowdos.app.feature.tutorial.TutorialTargetId
import com.snowdos.app.feature.tutorial.TutorialTargetRegistry
import com.snowdos.app.feature.tutorial.tutorialTarget
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlin.math.tanh
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

private const val ROUTE_MAIN_TABS = "main_tabs"
private const val ROUTE_APPEARANCE_SETTINGS = "settings/appearance"
private const val ROUTE_ACCOUNT_SETTINGS = "settings/account"
private const val ROUTE_ONBOARDING = "onboarding"

sealed interface AppLaunchTarget {
    data class TaskNotification(
        val taskId: String,
        val triggerProof: Boolean = false
    ) : AppLaunchTarget

    data class WidgetNavigation(
        val openCreateTask: Boolean = false
    ) : AppLaunchTarget
}

@Composable
fun SnowDosNavHost(
    modifier: Modifier = Modifier,
    launchTarget: AppLaunchTarget? = null,
    onLaunchTargetConsumed: () -> Unit = {},
    entryViewModel: AppEntryViewModel = hiltViewModel()
) {
    val hasCompletedOnboarding by entryViewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()
    val tabTransitionMode by entryViewModel.tabTransitionMode.collectAsStateWithLifecycle()
    val accountTabVisibility by entryViewModel.accountTabVisibility.collectAsStateWithLifecycle()
    val onboardingDone = hasCompletedOnboarding
    if (onboardingDone == null) {
        Box(modifier = modifier.fillMaxSize())
        return
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val showAccountInNav = TopLevelNavPolicy.isAccountTabVisibleInNav(
        visibility = accountTabVisibility,
        screenWidthDp = configuration.screenWidthDp,
        fontScale = density.fontScale
    )
    val activeDestinations = remember(showAccountInNav) {
        TopLevelNavPolicy.activeDestinations(showAccountInNav)
    }

    val navController = rememberNavController()
    val initialRoute = if (onboardingDone) ROUTE_MAIN_TABS else ROUTE_ONBOARDING
    var pendingStartTutorial by remember { mutableStateOf(false) }

    val navLabels = activeDestinations.map { stringResource(it.labelRes) }
    val navItems = remember(activeDestinations, navLabels) {
        activeDestinations.mapIndexed { index, destination ->
            FloatingNavItem(
                destination = destination,
                label = navLabels[index],
                selectedIcon = destination.selectedIcon,
                unselectedIcon = destination.unselectedIcon,
                iconAnimation = destination.iconAnimation
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = initialRoute,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(
                route = ROUTE_ONBOARDING,
                enterTransition = {
                    fadeIn(animationSpec = tween(260))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(220))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(260))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(220))
                }
            ) {
                val navigateToMainTabs = {
                    if (!navController.popBackStack()) {
                        navController.navigate(ROUTE_MAIN_TABS) {
                            popUpTo(navController.graph.id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                }
                OnboardingScreen(
                    onFinished = navigateToMainTabs,
                    onFinishWithTutorial = {
                        pendingStartTutorial = true
                        navigateToMainTabs()
                    }
                )
            }

            composable(
                route = ROUTE_MAIN_TABS,
                enterTransition = {
                    fadeIn(animationSpec = tween(260))
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        targetOffset = { it / 4 }
                    ) + fadeOut(animationSpec = tween(240))
                },
                popEnterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        initialOffset = { it / 4 }
                    ) + fadeIn(animationSpec = tween(240))
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(220))
                }
            ) {
                MainTabsDestination(
                    destinations = activeDestinations,
                    navItems = navItems,
                    showCompactAccountCard = !showAccountInNav,
                    topBarPadding = innerPadding.calculateTopPadding(),
                    tabTransitionMode = tabTransitionMode,
                    launchTarget = launchTarget,
                    onLaunchTargetConsumed = {
                        val currentRoute = navController.currentDestination?.route
                        if (currentRoute == ROUTE_APPEARANCE_SETTINGS || currentRoute == ROUTE_ACCOUNT_SETTINGS) {
                            navController.popBackStack(ROUTE_MAIN_TABS, inclusive = false)
                        }
                        onLaunchTargetConsumed()
                    },
                    startTutorialTrigger = pendingStartTutorial,
                    onTutorialTriggerConsumed = { pendingStartTutorial = false },
                    onOpenAppearance = {
                        navController.navigate(ROUTE_APPEARANCE_SETTINGS) {
                            launchSingleTop = true
                        }
                    },
                    onOpenAccount = {
                        navController.navigate(ROUTE_ACCOUNT_SETTINGS) {
                            launchSingleTop = true
                        }
                    },
                    onOpenOnboarding = {
                        navController.navigate(ROUTE_ONBOARDING) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = ROUTE_APPEARANCE_SETTINGS,
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(320, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(220))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(180))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(220))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(220))
                }
            ) {
                AppearanceSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = ROUTE_ACCOUNT_SETTINGS,
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(320, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(220))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(180))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(220))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(220))
                }
            ) {
                AccountScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun MainTabsDestination(
    destinations: List<TopLevelDestination>,
    navItems: List<FloatingNavItem<TopLevelDestination>>,
    showCompactAccountCard: Boolean,
    topBarPadding: androidx.compose.ui.unit.Dp,
    tabTransitionMode: TabTransitionMode,
    launchTarget: AppLaunchTarget?,
    onLaunchTargetConsumed: () -> Unit,
    startTutorialTrigger: Boolean,
    onTutorialTriggerConsumed: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenOnboarding: () -> Unit,
    tasksViewModel: TasksViewModel = hiltViewModel()
) {
    val tokens = SnowDosThemeTokens.current
    val homeIndex = destinations.indexOf(TopLevelDestination.TASKS).coerceAtLeast(0)
    val statsIndex = destinations.indexOf(TopLevelDestination.STATS).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = homeIndex, pageCount = { destinations.size })
    val scope = rememberCoroutineScope()
    val appHazeState = LocalAppHazeState.current
    val animateTransitions = tabTransitionMode == TabTransitionMode.ANIMATED

    val tutorialRegistry = remember { TutorialTargetRegistry() }
    var activeTutorialStep by remember { mutableStateOf<Int?>(null) }
    val currentTutorialStep = activeTutorialStep?.let { TutorialStep.ordered.getOrNull(it) }
    val onStartTutorial = remember { { activeTutorialStep = 0 } }

    var activeDestination by remember { mutableStateOf(TopLevelDestination.TASKS) }
    var lastDestinations by remember { mutableStateOf(destinations) }

    LaunchedEffect(pagerState, destinations) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (destinations == lastDestinations) {
                destinations.getOrNull(page)?.let { activeDestination = it }
            }
        }
    }

    LaunchedEffect(destinations) {
        if (destinations != lastDestinations) {
            val targetIndex = TopLevelNavPolicy.resolveTargetIndex(
                previousDestination = activeDestination,
                newDestinations = destinations
            )
            lastDestinations = destinations
            activeDestination = destinations[targetIndex]
            if (pagerState.currentPage != targetIndex) {
                pagerState.scrollToPage(targetIndex)
            }
        }
    }

    val overscrollAnimatable = remember { Animatable(0f) }
    val rawOverscroll = remember { floatArrayOf(0f) }
    val maxPullPx = with(LocalDensity.current) { 48.dp.toPx() }
    val fabVisibility = remember { Animatable(1f) }

    suspend fun navigateToTab(targetIndex: Int) {
        if (targetIndex < 0 || targetIndex > destinations.lastIndex) return
        if (animateTransitions) {
            pagerState.animateScrollToPage(
                page = targetIndex,
                animationSpec = spring(
                    dampingRatio = 0.88f,
                    stiffness = Spring.StiffnessMedium
                )
            )
        } else {
            pagerState.scrollToPage(targetIndex)
        }
    }

    LaunchedEffect(launchTarget) {
        val target = launchTarget ?: return@LaunchedEffect
        activeTutorialStep = null
        if (pagerState.currentPage != homeIndex) {
            navigateToTab(homeIndex)
        }
        when (target) {
            is AppLaunchTarget.TaskNotification -> {
                tasksViewModel.onAction(
                    TasksAction.OnOpenTaskFromNotification(
                        taskId = target.taskId,
                        triggerProof = target.triggerProof
                    )
                )
            }
            is AppLaunchTarget.WidgetNavigation -> {
                if (target.openCreateTask) {
                    tasksViewModel.onAction(TasksAction.OnOpenCreateEditor)
                }
            }
        }
        onLaunchTargetConsumed()
    }

    LaunchedEffect(startTutorialTrigger) {
        if (startTutorialTrigger) {
            activeTutorialStep = 0
            onTutorialTriggerConsumed()
        }
    }

    LaunchedEffect(activeTutorialStep, destinations) {
        val stepIndex = activeTutorialStep ?: return@LaunchedEffect
        val step = TutorialStep.ordered.getOrNull(stepIndex) ?: return@LaunchedEffect
        val targetIndex = destinations.indexOf(step.destination).coerceAtLeast(0)
        if (pagerState.currentPage != targetIndex) {
            navigateToTab(targetIndex)
        }
    }

    SyncFabVisibilityEffect(
        pagerState = pagerState,
        homeIndex = homeIndex,
        animateTransitions = animateTransitions,
        fabVisibility = fabVisibility
    )

    val overscrollConnection = remember(maxPullPx, pagerState, destinations.lastIndex) {
        fun damp(raw: Float): Float {
            if (raw == 0f) return 0f
            return (maxPullPx * tanh((raw / (maxPullPx * 2.2f)).toDouble())).toFloat()
        }

        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && rawOverscroll[0] != 0f) {
                    val pullingBack =
                        (rawOverscroll[0] > 0f && available.x < 0f) ||
                            (rawOverscroll[0] < 0f && available.x > 0f)
                    if (pullingBack) {
                        val previous = rawOverscroll[0]
                        val updated = previous + available.x
                        val crossedZero = (previous > 0f && updated <= 0f) || (previous < 0f && updated >= 0f)
                        val consumedX = if (crossedZero) -previous else available.x
                        rawOverscroll[0] = if (crossedZero) 0f else updated
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            overscrollAnimatable.snapTo(damp(rawOverscroll[0]))
                        }
                        return Offset(x = consumedX, y = 0f)
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.x != 0f) {
                    val atLeftEdge = pagerState.currentPage == 0 && available.x > 0f
                    val atRightEdge = pagerState.currentPage == destinations.lastIndex && available.x < 0f
                    if (atLeftEdge || atRightEdge || rawOverscroll[0] != 0f) {
                        rawOverscroll[0] += available.x
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            overscrollAnimatable.snapTo(damp(rawOverscroll[0]))
                        }
                        return Offset(x = available.x, y = 0f)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (rawOverscroll[0] != 0f || overscrollAnimatable.value != 0f) {
                    rawOverscroll[0] = 0f
                    overscrollAnimatable.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = 0.62f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(activeTutorialStep) {
        tutorialRegistry.isEnabled = activeTutorialStep != null
    }

    val statsActiveState = remember(statsIndex, pagerState) {
        derivedStateOf {
            !pagerState.isScrollInProgress && pagerState.currentPage == statsIndex
        }
    }
    val onOpenCreateTask = remember(tasksViewModel) {
        { tasksViewModel.onAction(TasksAction.OnOpenCreateEditor) }
    }
    val onSelectTab = remember(destinations, scope, animateTransitions, pagerState) {
        { destination: TopLevelDestination ->
            val targetIndex = destinations.indexOf(destination)
            if (targetIndex >= 0 && targetIndex != pagerState.targetPage) {
                scope.launch {
                    navigateToTab(targetIndex)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalTutorialTargetRegistry provides tutorialRegistry) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topBarPadding)
            ) {
                MainTabsPager(
                    pagerState = pagerState,
                    destinations = destinations,
                    statsActiveState = statsActiveState,
                    overscrollConnection = overscrollConnection,
                    overscrollAnimatable = overscrollAnimatable,
                    appHazeState = appHazeState,
                    showCompactAccountCard = showCompactAccountCard,
                    onOpenAccount = onOpenAccount,
                    onOpenAppearance = onOpenAppearance,
                    onOpenOnboarding = onOpenOnboarding,
                    onStartTutorial = onStartTutorial,
                    currentTutorialStep = currentTutorialStep,
                    tasksViewModel = tasksViewModel
                )

                MainTabsFab(
                    pagerState = pagerState,
                    homeIndex = homeIndex,
                    fabVisibility = fabVisibility,
                    onOpenCreateTask = onOpenCreateTask,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(
                            end = tokens.spacing.lg,
                            bottom = tokens.spacing.floatingNavBottomClearance
                        )
                )

                MainTabsFloatingNavBar(
                    pagerState = pagerState,
                    destinations = destinations,
                    navItems = navItems,
                    animateTransitions = animateTransitions,
                    onSelect = onSelectTab,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        val closeTutorial: () -> Unit = {
            activeTutorialStep = null
            scope.launch {
                if (pagerState.currentPage != homeIndex) {
                    navigateToTab(homeIndex)
                }
            }
        }

        SpotlightTutorialOverlay(
            visible = activeTutorialStep != null,
            stepIndex = activeTutorialStep ?: 0,
            registry = tutorialRegistry,
            onNext = {
                val current = activeTutorialStep ?: return@SpotlightTutorialOverlay
                if (current >= TutorialStep.ordered.lastIndex) {
                    closeTutorial()
                } else {
                    activeTutorialStep = current + 1
                }
            },
            onSkip = closeTutorial
        )
    }
}

@Composable
private fun SyncFabVisibilityEffect(
    pagerState: PagerState,
    homeIndex: Int,
    animateTransitions: Boolean,
    fabVisibility: Animatable<Float, AnimationVector1D>
) {
    val isDragInProgress by pagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragInProgress, pagerState.targetPage, homeIndex, animateTransitions) {
        if (isDragInProgress) {
            snapshotFlow {
                (1f - abs((pagerState.currentPage + pagerState.currentPageOffsetFraction) - homeIndex))
                    .coerceIn(0f, 1f)
            }.collect { p ->
                fabVisibility.snapTo(p)
            }
        } else {
            val targetFab = if (pagerState.targetPage == homeIndex) 1f else 0f
            if (animateTransitions) {
                fabVisibility.animateTo(
                    targetValue = targetFab,
                    animationSpec = spring(
                        dampingRatio = 0.88f,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            } else {
                fabVisibility.snapTo(targetFab)
            }
        }
    }
}

@Composable
private fun MainTabsPager(
    pagerState: PagerState,
    destinations: List<TopLevelDestination>,
    statsActiveState: State<Boolean>,
    overscrollConnection: NestedScrollConnection,
    overscrollAnimatable: Animatable<Float, AnimationVector1D>,
    appHazeState: dev.chrisbanes.haze.HazeState?,
    showCompactAccountCard: Boolean,
    onOpenAccount: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onStartTutorial: () -> Unit,
    currentTutorialStep: TutorialStep?,
    tasksViewModel: TasksViewModel
) {
    val outerOverscrollFactory = LocalOverscrollFactory.current
    val pagerContainerModifier = Modifier
        .fillMaxSize()
        .nestedScroll(overscrollConnection)
        .graphicsLayer {
            val pull = overscrollAnimatable.value
            translationX = pull * 0.72f
            scaleX = 1f + (abs(pull) / 1800f).coerceAtMost(0.03f)
            transformOrigin = TransformOrigin(if (pull >= 0f) 0f else 1f, 0.5f)
        }
        .let { base ->
            if (appHazeState != null) {
                base.hazeSource(state = appHazeState, zIndex = 1f)
            } else {
                base
            }
        }

    Box(modifier = pagerContainerModifier) {
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = (destinations.size - 1).coerceAtLeast(1),
                key = { page -> destinations.getOrNull(page)?.route ?: page },
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val destination = destinations.getOrNull(page) ?: return@HorizontalPager
                CompositionLocalProvider(LocalOverscrollFactory provides outerOverscrollFactory) {
                    when (destination) {
                        TopLevelDestination.STATS -> StatsTabContent(
                            statsActiveState = statsActiveState,
                            isTutorialHighlightActive = currentTutorialStep == TutorialStep.STATS_OVERVIEW
                        )
                        TopLevelDestination.TASKS -> TasksScreen(
                            isTutorialTaskHighlightActive = currentTutorialStep == TutorialStep.TASK_ACTIONS,
                            viewModel = tasksViewModel
                        )
                        TopLevelDestination.ACCOUNT -> AccountScreen()
                        TopLevelDestination.SETTINGS -> SettingsScreen(
                            showCompactAccountCard = showCompactAccountCard,
                            onOpenAccount = onOpenAccount,
                            onOpenAppearance = onOpenAppearance,
                            onOpenOnboarding = onOpenOnboarding,
                            onStartTutorial = onStartTutorial,
                            isTutorialHighlightActive = currentTutorialStep == TutorialStep.SETTINGS_HIGHLIGHTS
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsTabContent(
    statsActiveState: State<Boolean>,
    isTutorialHighlightActive: Boolean
) {
    StatsScreen(
        isActive = statsActiveState.value,
        isTutorialHighlightActive = isTutorialHighlightActive
    )
}

@Composable
private fun MainTabsFab(
    pagerState: PagerState,
    homeIndex: Int,
    fabVisibility: Animatable<Float, AnimationVector1D>,
    onOpenCreateTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = SnowDosThemeTokens.current
    val isFabVisible by remember {
        derivedStateOf {
            fabVisibility.value > 0.01f
        }
    }
    val isFabEnabled by remember(homeIndex, pagerState) {
        derivedStateOf {
            pagerState.targetPage == homeIndex && fabVisibility.value > 0.5f
        }
    }

    if (!isFabVisible) return

    val fabInteraction = remember { MutableInteractionSource() }
    val fabShape = if (tokens.surface.usePixelRetroStyle) {
        androidx.compose.foundation.shape.RoundedCornerShape(tokens.radius.pill)
    } else {
        CircleShape
    }
    FloatingActionButton(
        onClick = {
            if (isFabEnabled) {
                onOpenCreateTask()
            }
        },
        interactionSource = fabInteraction,
        shape = fabShape,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp
        ),
        modifier = modifier
            .tutorialTarget(TutorialTargetId.ADD_TASK)
            .graphicsLayer {
                val p = fabVisibility.value.coerceIn(0f, 1f)
                val scale = 0.65f + 0.35f * p
                scaleX = scale
                scaleY = scale
                alpha = p
            }
            .snowPressFeedback(
                interactionSource = fabInteraction,
                enabled = isFabEnabled,
                pressedScale = 0.94f
            )
            .glassReadySurface(
                shape = fabShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                alpha = tokens.surface.selectedAlpha,
                highlighted = true,
                blurScope = SnowBlurScope.CHROME
            )
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.tasks_new_cd)
        )
    }
}

@Composable
private fun MainTabsFloatingNavBar(
    pagerState: PagerState,
    destinations: List<TopLevelDestination>,
    navItems: List<FloatingNavItem<TopLevelDestination>>,
    animateTransitions: Boolean,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDragInProgress by pagerState.interactionSource.collectIsDraggedAsState()
    val maxPageIndex = destinations.lastIndex.coerceAtLeast(0)
    val selectedDestination by remember(destinations, maxPageIndex, pagerState) {
        derivedStateOf {
            destinations.getOrElse(pagerState.targetPage.coerceIn(0, maxPageIndex)) {
                TopLevelDestination.TASKS
            }
        }
    }
    val scrollPositionProvider = remember(pagerState, maxPageIndex) {
        {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(-0.15f, maxPageIndex + 0.15f)
        }
    }

    FloatingPillNavBar(
        items = navItems,
        selectedDestination = selectedDestination,
        scrollPositionProvider = scrollPositionProvider,
        isDragInProgress = isDragInProgress || pagerState.isScrollInProgress,
        animateTransitions = animateTransitions,
        onSelect = onSelect,
        modifier = modifier
    )
}
