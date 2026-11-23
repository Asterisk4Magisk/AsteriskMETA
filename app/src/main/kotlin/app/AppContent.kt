// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.effects.NodeDelayAutoProbe
import app.modes.NavigationBarStyleCompact
import app.navigation.MainDestination
import app.navigation.MainDestinationState
import app.navigation.Navigator
import app.navigation.Route
import app.navigation.rememberMainDestinationState
import engine.mihomo.MihomoProfileEmptyErrorMessage
import engine.mihomo.MihomoProfileMissingErrorMessage
import engine.proxy.ProxyServiceResult
import features.about.AboutPage
import features.about.LicensePage
import features.home.HomeServiceOperation
import features.home.HomeServiceOperationState
import features.logs.CoreLogsPage
import features.logs.LogcatLogsPage
import features.mihomo.MihomoDashboardPage
import features.mihomo.MihomoOverrideScriptEditPage
import features.mihomo.MihomoOverrideScriptListPage
import features.mihomo.MihomoProfileEditPage
import features.mihomo.MihomoProfileListPage
import features.mihomo.MihomoProfileSourcePreview
import features.mihomo.MihomoProxyPage
import features.mihomo.provider.MihomoProviderManagementPage
import features.mihomo.provider.MihomoProxyProviderDetailPage
import features.monitoring.connections.ConnectionsMonitorPage
import features.monitoring.network.NetworkMonitorPage
import features.monitoring.resource.ResourceMonitorPage
import features.monitoring.traffic.TrafficMonitorPage
import features.proxy.app.ProxyAppListPage
import features.resources.ResourceCidrEditorPage
import features.resources.ResourceManagementPage
import features.settings.SettingsPage
import kotlinx.coroutines.launch
import ui.components.AsteriskCompactNavigationBar
import ui.components.AsteriskCompactNavigationTab
import ui.components.AsteriskFloatingNavigationAction
import ui.components.AsteriskFloatingNavigationBar
import ui.components.AsteriskFloatingNavigationItem
import ui.components.AsteriskScaffold
import ui.components.asteriskNavigationSelection
import ui.components.floatingActionChrome
import ui.components.floatingActionContentColor
import ui.icons.AsteriskIcons as Icons
import ui.layout.FoldAdaptiveLayout
import ui.layout.LocalAdaptiveWindow
import ui.layout.avoidFold
import ui.layout.pageWindowPadding
import ui.layout.rememberAdaptiveWindow
import ui.navigation.AsteriskNavDisplay
import ui.theme.AsteriskMotion
import ui.theme.AsteriskShapeTokens

private data class MainNavigationItem(
    val destination: MainDestination,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun mainNavigationItems(): List<MainNavigationItem> {
    val home = stringResource(R.string.nav_dashboard)
    val proxies = stringResource(R.string.nav_proxies)
    val configurations = stringResource(R.string.nav_profiles)
    val settings = stringResource(R.string.settings_title)

    return remember(home, proxies, configurations, settings) {
        listOf(
            MainNavigationItem(MainDestination.Home, home, Icons.Rounded.Home),
            MainNavigationItem(MainDestination.Proxies, proxies, Icons.Rounded.Dns),
            MainNavigationItem(MainDestination.Configurations, configurations, Icons.Rounded.Description),
            MainNavigationItem(MainDestination.Settings, settings, Icons.Rounded.Settings),
        )
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator found!") }
val LocalIsWideScreen = staticCompositionLocalOf { false }

internal val LocalMainDestinationState = staticCompositionLocalOf<MainDestinationState?> { null }

@Composable
fun AppContent(
    padding: PaddingValues,
) {
    val mainDestinationState = rememberMainDestinationState()
    val serviceControl = rememberHomeServiceControl()

    val backStack = rememberNavBackStack(Route.Main)
    val navigator = remember { Navigator(backStack) }

    NodeDelayAutoProbe(
        stateStore = LocalAppStateStore.current,
        mihomoRuntime = LocalAppServices.current.mihomoRuntime,
        homeVisible = navigator.current() is Route.Main && mainDestinationState.current == MainDestination.Home,
    )

    MainScreenBackHandler(mainDestinationState, navigator)

    val window = rememberAdaptiveWindow()

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalAdaptiveWindow provides window,
        LocalIsWideScreen provides window.supportsWideContent,
        LocalMainDestinationState provides mainDestinationState,
        LocalHomeServiceControl provides serviceControl,
    ) {
        FoldAdaptiveLayout(
            window = window,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .avoidFold(window)
                .clipToBounds()
                .imePadding(),
            companion = {
                AppFoldOverview(
                    showProfilePreview = backStack.lastOrNull() == Route.Main &&
                        mainDestinationState.current == MainDestination.Configurations,
                )
            },
        ) {
            val entryProvider = remember(backStack) {
                entryProvider<NavKey> {
                entry<Route.Main> {
                    Home(
                        padding = padding,
                        mainDestinationState = mainDestinationState,
                    )
                }
                entry<Route.About> {
                    AboutPage(padding = padding)
                }
                entry<Route.License> {
                    LicensePage(padding = padding)
                }
                entry<Route.CoreLogs> {
                    CoreLogsPage(padding = padding)
                }
                entry<Route.LogcatLogs> {
                    LogcatLogsPage(padding = padding)
                }
                entry<Route.ResourceManagement> {
                    ResourceManagementPage(padding = padding)
                }
                entry<Route.ResourceCidrEdit> { route ->
                    ResourceCidrEditorPage(padding = padding, kind = route.kind)
                }
                entry<Route.ResourceMonitor> {
                    ResourceMonitorPage(padding = padding)
                }
                entry<Route.ConnectionsMonitor> {
                    ConnectionsMonitorPage(padding = padding)
                }
                entry<Route.TrafficMonitor> {
                    TrafficMonitorPage(padding = padding)
                }
                entry<Route.NetworkMonitor> {
                    NetworkMonitorPage(padding = padding)
                }
                entry<Route.ProxyAppList> {
                    ProxyAppListPage(
                        padding = padding,
                        onBack = { navigator.pop() },
                    )
                }
                entry<Route.MihomoProfileList> {
                    MihomoProfileListPage(
                        padding = padding,
                        onBack = { navigator.pop() },
                    )
                }
                entry<Route.MihomoProviderManagement> {
                    MihomoProviderManagementPage(padding = padding)
                }
                entry<Route.MihomoProxyProviderDetail> { route ->
                    key(route.providerName) {
                        MihomoProxyProviderDetailPage(
                            padding = padding,
                            providerName = route.providerName,
                        )
                    }
                }
                entry<Route.MihomoOverrideScripts> {
                    MihomoOverrideScriptListPage(
                        padding = padding,
                    )
                }
                entry<Route.MihomoOverrideScriptEdit> { route ->
                    key(route.scriptId, route.draftId) {
                        MihomoOverrideScriptEditPage(
                            padding = padding,
                            scriptId = route.scriptId,
                        )
                    }
                }
                entry<Route.MihomoProfileEdit> { route ->
                    key(route.profileId, route.type, route.draftId, route.editContent) {
                        MihomoProfileEditPage(
                            padding = padding,
                            profileId = route.profileId,
                            type = route.type,
                            editContent = route.editContent,
                        )
                    }
                }
            }
            }

            val entries = rememberDecoratedNavEntries(
                backStack = backStack,
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                entryProvider = entryProvider,
            )
            AsteriskNavDisplay(
                entries = entries,
                onBack = { navigator.pop() },
            )
            features.proxy.ProxyErrorHost()
        }
    }
}

@Composable
private fun AppFoldOverview(showProfilePreview: Boolean) {
    val state by LocalAppStateStore.current.collectAppState()
    val profile = state.mihomoProfiles.firstOrNull { it.id == state.selectedMihomoProfileId }
    if (showProfilePreview && profile != null) {
        Box(Modifier.fillMaxSize().padding(16.dp).clip(AsteriskShapeTokens.ListCard)) {
            MihomoProfileSourcePreview(profile)
        }
        return
    }

    val status = stringResource(
        if (state.proxyRunning) R.string.home_service_enabled else R.string.home_service_disabled,
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "status") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(status, style = MaterialTheme.typography.titleMedium)
                    if (profile != null) {
                        Text(
                            stringResource(R.string.home_current_profile),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(profile.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun Home(
    padding: PaddingValues,
    mainDestinationState: MainDestinationState,
) {
    val showNavigationRail = LocalAdaptiveWindow.current.showNavigationRail
    val layoutDirection = LocalLayoutDirection.current
    val navigationItems = mainNavigationItems()
    val appState by LocalAppStateStore.current.collectAppState()
    val autoHideNavigationBar = appState.autoHideNavigationBar && !showNavigationRail
    val shouldAutoHide by rememberUpdatedState(autoHideNavigationBar)
    var isBottomBarVisible by remember { mutableStateOf(true) }

    LaunchedEffect(mainDestinationState.current, autoHideNavigationBar) {
        isBottomBarVisible = true
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!shouldAutoHide) return Offset.Zero
                if (available.y < -10f) isBottomBarVisible = false
                if (available.y > 10f) isBottomBarVisible = true
                return Offset.Zero
            }
        }
    }
    val bottomBarOffsetFraction by animateFloatAsState(
        targetValue = if (isBottomBarVisible) 0f else 1f,
        animationSpec = AsteriskMotion.spatial(),
        label = "bottom-bar-visibility-offset",
    )
    val sizeMotion = AsteriskMotion.fastSpatial<IntSize>()
    val fadeMotion = AsteriskMotion.fastEffects<Float>()

    Row(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showNavigationRail,
            enter = expandHorizontally(sizeMotion, expandFrom = Alignment.Start) + fadeIn(fadeMotion),
            exit = shrinkHorizontally(sizeMotion, shrinkTowards = Alignment.Start) + fadeOut(fadeMotion),
        ) {
            NavigationRail {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    HomeServicePowerButton(modifier = Modifier.padding(vertical = 12.dp))
                    navigationItems.forEach { item ->
                        NavigationRailItem(
                            selected = mainDestinationState.current == item.destination,
                            onClick = { mainDestinationState.select(item.destination) },
                            icon = { Icon(imageVector = item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        }
        AsteriskScaffold(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            contentWindowInsets = if (showNavigationRail) {
                WindowInsets.systemBars.union(
                    WindowInsets.displayCutout.exclude(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Start),
                    ),
                )
            } else {
                ScaffoldDefaults.contentWindowInsets
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = !showNavigationRail,
                    enter = expandVertically(sizeMotion, expandFrom = Alignment.Bottom) + fadeIn(fadeMotion),
                    exit = shrinkVertically(sizeMotion, shrinkTowards = Alignment.Bottom) + fadeOut(fadeMotion),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = bottomBarOffsetFraction * size.height
                                alpha = (1f - bottomBarOffsetFraction * 0.85f).coerceIn(0f, 1f)
                            },
                    ) {
                        MainNavigationBar(
                            navigationItems = navigationItems,
                            mainDestinationState = mainDestinationState,
                            navigationBarStyle = appState.navigationBarStyle,
                        )
                    }
                }
            },
        ) { innerPadding ->
            MainDestinationContent(
                padding = if (showNavigationRail) PaddingValues(top = innerPadding.calculateTopPadding()) else innerPadding,
                mainDestinationState = mainDestinationState,
                modifier = if (showNavigationRail) {
                    Modifier.padding(end = innerPadding.calculateEndPadding(layoutDirection))
                } else {
                    Modifier.pageWindowPadding(padding)
                },
            )
        }
    }
}

@Composable
private fun MainNavigationBar(
    navigationItems: List<MainNavigationItem>,
    mainDestinationState: MainDestinationState,
    navigationBarStyle: Int,
    modifier: Modifier = Modifier,
) {
    val selectedDestination = mainDestinationState.current
    if (navigationBarStyle == NavigationBarStyleCompact) {
        AsteriskCompactNavigationBar(
            tabs = navigationItems.map { item ->
                AsteriskCompactNavigationTab(
                    icon = item.icon,
                    label = item.label,
                    selected = selectedDestination == item.destination,
                    onClick = { mainDestinationState.select(item.destination) },
                )
            },
            modifier = modifier,
            trailingAction = { HomeServicePowerButton(compact = true) },
        )
        return
    }
    AsteriskFloatingNavigationBar(
        modifier = modifier,
        trailingAction = { HomeServicePowerButton() },
    ) {
        navigationItems.forEach { item ->
            AsteriskFloatingNavigationItem(
                selected = selectedDestination == item.destination,
                onClick = { mainDestinationState.select(item.destination) },
                icon = item.icon,
                label = item.label,
            )
        }
    }
}

@Composable
private fun MainDestinationContent(
    padding: PaddingValues,
    mainDestinationState: MainDestinationState,
    modifier: Modifier = Modifier,
) {
    val stateHolder = rememberSaveableStateHolder()
    AnimatedContent(
        targetState = mainDestinationState.current,
        modifier = modifier,
        transitionSpec = AsteriskMotion.destinationChange { it.index },
        label = "main-destination",
    ) { destination ->
        stateHolder.SaveableStateProvider(destination.id) {
            key(destination) {
                when (destination) {
                    MainDestination.Home -> MihomoDashboardPage(padding = padding)
                    MainDestination.Proxies -> MihomoProxyPage(padding = padding)
                    MainDestination.Configurations -> MihomoProfileListPage(padding = padding)
                    MainDestination.Settings -> SettingsPage(padding = padding)
                }
            }
        }
    }
}

@Composable
private fun MainScreenBackHandler(
    mainState: MainDestinationState,
    navigator: Navigator,
) {
    val isMainDestinationBackHandlerEnabled by remember {
        derivedStateOf {
            navigator.current() is Route.Main &&
                navigator.backStackSize() == 1 &&
                mainState.current != MainDestination.Home
        }
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isMainDestinationBackHandlerEnabled,
        onBackCompleted = {
            mainState.select(MainDestination.Home)
        },
    )
}

internal class HomeServiceControl(private val operationState: HomeServiceOperationState) {
    var operation: HomeServiceOperation
        get() = operationState.operation
        set(value) { operationState.operation = value }
    var modeOperationInProgress: Boolean
        get() = operationState.modeOperationInProgress
        set(value) { operationState.modeOperationInProgress = value }
    val serviceOperationInProgress: Boolean get() = operation != HomeServiceOperation.Idle
    val busy: Boolean get() = serviceOperationInProgress || modeOperationInProgress
    var toggleService: () -> Unit = {}
}

internal val LocalHomeServiceControl = staticCompositionLocalOf<HomeServiceControl> {
    error("No home service control found")
}

@Composable
private fun rememberHomeServiceControl(): HomeServiceControl {
    val stateStore = LocalAppStateStore.current
    val updateAppState = LocalUpdateAppState.current
    val services = LocalAppServices.current
    val control = remember(services) { HomeServiceControl(services.homeServiceOperationState) }
    val startFailedMessage = stringResource(R.string.mihomo_dashboard_start_failed)
    val startNoConfigurationMessage = stringResource(R.string.mihomo_dashboard_start_no_profile)
    val startEmptyConfigurationMessage = stringResource(R.string.mihomo_dashboard_start_empty_profile)
    val stopFailedMessage = stringResource(R.string.mihomo_dashboard_stop_failed)
    val serviceStartedMessage = stringResource(R.string.proxy_service_started)
    val serviceStoppedMessage = stringResource(R.string.proxy_service_stopped)

    suspend fun handleProxyServiceResult(result: ProxyServiceResult, wasRunning: Boolean) {
        when (result) {
            is ProxyServiceResult.Success -> {
                updateAppState { state ->
                    state.copy(
                        proxyRunning = result.proxyRunning,
                        localProxyPort = result.appState?.localProxyPort ?: state.localProxyPort,
                        mihomoControlPort = result.appState?.mihomoControlPort ?: state.mihomoControlPort,
                    )
                }
                services.tipNotifier.show(if (result.proxyRunning) serviceStartedMessage else serviceStoppedMessage)
            }

            is ProxyServiceResult.Failed -> {
                updateAppState { state -> state.copy(proxyRunning = false) }
                val localizedStartMessage = if (wasRunning) {
                    null
                } else {
                    when (result.error.message) {
                        MihomoProfileMissingErrorMessage -> startNoConfigurationMessage
                        MihomoProfileEmptyErrorMessage -> startEmptyConfigurationMessage
                        else -> null
                    }
                }
                if (localizedStartMessage != null) {
                    services.tipNotifier.show(localizedStartMessage)
                } else {
                    services.tipNotifier.showError(
                        result.error,
                        if (wasRunning) stopFailedMessage else startFailedMessage,
                    )
                }
            }
        }
    }

    control.toggleService = toggle@{
        if (control.busy) return@toggle
        val stateSnapshot = stateStore.state.value
        val wasRunning = stateSnapshot.proxyRunning
        control.operation = if (wasRunning) HomeServiceOperation.Stopping else HomeServiceOperation.Starting
        services.appScope.launch {
            try {
                handleProxyServiceResult(services.proxyServiceUseCase.toggle(stateSnapshot), wasRunning)
            } finally {
                control.operation = HomeServiceOperation.Idle
            }
        }
    }
    return control
}

@Composable
private fun HomeServicePowerButton(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val control = LocalHomeServiceControl.current
    val contentColor = floatingActionContentColor(appState.proxyRunning)
    val label = stringResource(
        when (control.operation) {
            HomeServiceOperation.Starting -> R.string.home_service_starting
            HomeServiceOperation.Stopping -> R.string.home_service_stopping
            HomeServiceOperation.Applying -> R.string.home_service_applying
            HomeServiceOperation.Idle -> if (appState.proxyRunning) R.string.home_service_stop else R.string.home_service_start
        },
    )
    val status = stringResource(if (appState.proxyRunning) R.string.home_service_enabled else R.string.home_service_disabled)
    val semanticsModifier = modifier.semantics {
        contentDescription = label
        stateDescription = status
    }
    if (compact) {
        // Compact navigation pairs the tabs with a 56dp accent action and the
        // stock M3 large corner (16dp). Surface keeps disabled semantics while
        // a service operation is in progress.
        val shape = RoundedCornerShape(16.dp)
        Box(modifier = semanticsModifier.size(56.dp)) {
            Box(Modifier.matchParentSize().floatingActionChrome(appState.proxyRunning, shape))
            Surface(
                onClick = control.toggleService,
                enabled = !control.busy,
                modifier = Modifier.matchParentSize(),
                shape = shape,
                color = Color.Transparent,
                contentColor = contentColor,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .asteriskNavigationSelection(appState.proxyRunning, cornerRadius = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (control.serviceOperationInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                            color = contentColor,
                        )
                    } else {
                        Icon(
                            imageVector = if (appState.proxyRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }
        return
    }
    AsteriskFloatingNavigationAction(
        selected = appState.proxyRunning,
        onClick = control.toggleService,
        enabled = !control.busy,
        modifier = semanticsModifier,
    ) {
        if (control.serviceOperationInProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp,
                color = contentColor,
            )
        } else {
            Icon(
                imageVector = if (appState.proxyRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
