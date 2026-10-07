// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import ui.icons.AsteriskIcons as Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import ui.components.AsteriskFloatingNavigationBar
import ui.components.AsteriskFloatingNavigationItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import ui.components.AsteriskScaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import ui.navigation.AsteriskNavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.navigation.MainDestination
import app.navigation.MainDestinationState
import app.navigation.Navigator
import app.navigation.Route
import app.navigation.rememberMainDestinationState
import features.about.AboutPage
import features.about.LicensePage
import features.logs.CoreLogsPage
import features.logs.LogcatLogsPage
import features.monitoring.connections.ConnectionsMonitorPage
import features.monitoring.network.NetworkMonitorPage
import features.monitoring.resource.ResourceMonitorPage
import features.monitoring.traffic.TrafficMonitorPage
import features.mihomo.MihomoDashboardPage
import features.mihomo.MihomoOverrideScriptEditPage
import features.mihomo.MihomoOverrideScriptListPage
import features.mihomo.MihomoProfileEditPage
import features.mihomo.MihomoProfileListPage
import features.mihomo.MihomoProxyPage
import features.mihomo.provider.MihomoProviderManagementPage
import features.mihomo.provider.MihomoProxyProviderDetailPage
import features.proxy.app.ProxyAppListPage
import features.resources.ResourceManagementPage
import features.settings.SettingsPage
import ui.layout.pageWindowPadding
import ui.layout.shouldShowNavigationRail
import ui.layout.shouldShowSplitPane
import ui.theme.AsteriskMotion
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import engine.mihomo.MihomoProfileEmptyErrorMessage
import engine.mihomo.MihomoProfileMissingErrorMessage
import engine.proxy.ProxyServiceResult
import features.home.HomeServiceOperation
import features.home.HomeServiceOperationState
import kotlinx.coroutines.launch
import ui.components.AsteriskFloatingNavigationAction
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

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
    val settings = stringResource(R.string.nav_settings)

    return remember(home, proxies, configurations, settings) {
        listOf(
            MainNavigationItem(MainDestination.Home, home, Icons.Rounded.Home),
            MainNavigationItem(MainDestination.Proxies, proxies, Icons.AutoMirrored.Rounded.AltRoute),
            MainNavigationItem(MainDestination.Configurations, configurations, Icons.Rounded.Description),
            MainNavigationItem(MainDestination.Settings, settings, Icons.Rounded.Settings),
        )
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator found!") }
val LocalIsWideScreen = staticCompositionLocalOf { false }

val LocalSupportsSplitPane = staticCompositionLocalOf { false }
internal val LocalMainDestinationState = staticCompositionLocalOf<MainDestinationState?> { null }

@Composable
fun AppContent(
    padding: PaddingValues,
) {
    val mainDestinationState = rememberMainDestinationState()
    val serviceControl = rememberHomeServiceControl()

    val backStack = remember { mutableStateListOf<NavKey>().apply { add(Route.Main) } }
    val navigator = remember { Navigator(backStack) }

    MainScreenBackHandler(mainDestinationState, navigator)

    val isWideScreen = shouldShowNavigationRail()
    val supportsSplitPane = shouldShowSplitPane()

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalIsWideScreen provides isWideScreen,
        LocalSupportsSplitPane provides supportsSplitPane,
        LocalMainDestinationState provides mainDestinationState,
        LocalHomeServiceControl provides serviceControl,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .imePadding(),
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
                    key(route.profileId, route.type, route.draftId) {
                        MihomoProfileEditPage(
                            padding = padding,
                            profileId = route.profileId,
                            type = route.type,
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
                transitionSpec = AsteriskMotion.navigationForward(),
                popTransitionSpec = AsteriskMotion.navigationBack(),
                predictivePopTransitionSpec = AsteriskMotion.predictiveNavigationBack(),
            )
            features.proxy.ProxyErrorHost()
        }
    }
}

@Composable
private fun Home(
    padding: PaddingValues,
    mainDestinationState: MainDestinationState,
) {
    val isWideScreen = LocalIsWideScreen.current
    val layoutDirection = LocalLayoutDirection.current
    val navigationItems = mainNavigationItems()
    if (isWideScreen) {
        WideScreenContent(
            navigationItems = navigationItems,
            layoutDirection = layoutDirection,
            mainDestinationState = mainDestinationState,
        )
    } else {
        CompactScreenLayout(
            navigationItems = navigationItems,
            padding = padding,
            mainDestinationState = mainDestinationState,
        )
    }
}

@Composable
private fun WideScreenContent(
    navigationItems: List<MainNavigationItem>,
    layoutDirection: LayoutDirection,
    mainDestinationState: MainDestinationState,
) {
    val selectedDestination = mainDestinationState.current
    Row {
        NavigationRail {
            HomeServicePowerButton(modifier = Modifier.padding(vertical = 12.dp))
            navigationItems.forEach { item ->
                NavigationRailItem(
                    selected = selectedDestination == item.destination,
                    onClick = { mainDestinationState.select(item.destination) },
                    icon = { Icon(imageVector = item.icon, contentDescription = null) },
                    label = { Text(item.label) },
                )
            }
        }
        AsteriskScaffold(
            modifier = Modifier
                .fillMaxSize(),
            contentWindowInsets =
                WindowInsets.systemBars.union(
                    WindowInsets.displayCutout.exclude(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Start),
                    ),
                ),
        ) { padding ->
            MainDestinationContent(
                padding = PaddingValues(top = padding.calculateTopPadding()),
                mainDestinationState = mainDestinationState,
                modifier = Modifier
                    .imePadding()
                    .padding(end = padding.calculateEndPadding(layoutDirection)),
            )
        }
    }
}

@Composable
private fun CompactScreenLayout(
    navigationItems: List<MainNavigationItem>,
    padding: PaddingValues,
    mainDestinationState: MainDestinationState,
) {
    var isBottomBarVisible by remember { mutableStateOf(true) }

    LaunchedEffect(mainDestinationState.current) {
        isBottomBarVisible = true
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < -10f && isBottomBarVisible) {
                    isBottomBarVisible = false
                } else if (delta > 10f && !isBottomBarVisible) {
                    isBottomBarVisible = true
                }
                return Offset.Zero
            }
        }
    }

    val bottomBarOffsetFraction by animateFloatAsState(
        targetValue = if (isBottomBarVisible) 0f else 1f,
        animationSpec = AsteriskMotion.spatial(),
        label = "bottom-bar-visibility-offset",
    )

    AsteriskScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        bottomBar = {
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
                )
            }
        },
    ) { innerPadding ->
        MainDestinationContent(
            padding = innerPadding,
            mainDestinationState = mainDestinationState,
            modifier = Modifier.pageWindowPadding(padding),
        )
    }
}

@Composable
private fun MainNavigationBar(
    navigationItems: List<MainNavigationItem>,
    mainDestinationState: MainDestinationState,
    modifier: Modifier = Modifier,
) {
    val selectedDestination = mainDestinationState.current
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
                    ).withMihomoRestartApplied()
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
private fun HomeServicePowerButton(modifier: Modifier = Modifier) {
    val appState by LocalAppStateStore.current.collectAppState()
    val control = LocalHomeServiceControl.current
    val label = stringResource(
        when (control.operation) {
            HomeServiceOperation.Starting -> R.string.home_service_starting
            HomeServiceOperation.Stopping -> R.string.home_service_stopping
            HomeServiceOperation.Idle -> if (appState.proxyRunning) R.string.home_service_stop else R.string.home_service_start
        },
    )
    val status = stringResource(if (appState.proxyRunning) R.string.home_service_enabled else R.string.home_service_disabled)
    AsteriskFloatingNavigationAction(
        selected = appState.proxyRunning,
        onClick = control.toggleService,
        enabled = !control.busy,
        modifier = modifier.semantics {
            contentDescription = label
            stateDescription = status
        },
    ) {
        if (control.serviceOperationInProgress) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
        } else {
            Icon(
                imageVector = if (appState.proxyRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
