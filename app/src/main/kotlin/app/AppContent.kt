// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
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
import features.mihomo.provider.MihomoProviderDetailPage
import features.mihomo.provider.MihomoProviderListPage
import features.proxy.app.ProxyAppListPage
import features.resources.ResourceManagementPage
import features.settings.SettingsPage
import ui.layout.pageWindowPadding
import ui.layout.shouldShowNavigationRail
import ui.layout.shouldShowSplitPane
import ui.theme.AsteriskMotion
import androidx.compose.runtime.getValue

private data class MainNavigationItem(
    val destination: MainDestination,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun mainNavigationItems(): List<MainNavigationItem> {
    val home = stringResource(R.string.nav_dashboard)
    val proxies = stringResource(R.string.nav_proxies)
    val configurations = stringResource(R.string.nav_configurations)
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
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
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
                entry<Route.MihomoProviders> {
                    MihomoProviderListPage(padding = padding)
                }
                entry<Route.MihomoProviderDetail> { route ->
                    key(route.providerName) {
                        MihomoProviderDetailPage(
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
            val detailSpatialMotion = AsteriskMotion.spatial<IntOffset>()

            NavDisplay(
                entries = entries,
                onBack = { navigator.pop() },
                transitionSpec = {
                    slideInHorizontally(
                        animationSpec = detailSpatialMotion,
                        initialOffsetX = { width -> width },
                    ).togetherWith(
                        slideOutHorizontally(
                            animationSpec = detailSpatialMotion,
                            targetOffsetX = { width -> -width / 3 },
                        ),
                    )
                },
                popTransitionSpec = {
                    slideInHorizontally(
                        animationSpec = detailSpatialMotion,
                        initialOffsetX = { width -> -width / 3 },
                    ).togetherWith(
                        slideOutHorizontally(
                            animationSpec = detailSpatialMotion,
                            targetOffsetX = { width -> width },
                        ),
                    )
                },
            )
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
            navigationItems.forEach { item ->
                NavigationRailItem(
                    selected = selectedDestination == item.destination,
                    onClick = { mainDestinationState.select(item.destination) },
                    icon = { Icon(imageVector = item.icon, contentDescription = null) },
                    label = { Text(item.label) },
                )
            }
        }
        Scaffold(
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
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            MainNavigationBar(
                navigationItems = navigationItems,
                mainDestinationState = mainDestinationState,
            )
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
    NavigationBar(modifier = modifier) {
        navigationItems.forEach { item ->
            NavigationBarItem(
                selected = selectedDestination == item.destination,
                onClick = { mainDestinationState.select(item.destination) },
                icon = { Icon(imageVector = item.icon, contentDescription = null) },
                label = { Text(item.label) },
                alwaysShowLabel = true,
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
    val spatialMotion = AsteriskMotion.spatial<IntOffset>()
    val effectsMotion = AsteriskMotion.fastEffects<Float>()
    AnimatedContent(
        targetState = mainDestinationState.current,
        modifier = modifier,
        transitionSpec = {
            val direction = when {
                targetState.index > initialState.index -> 1
                targetState.index < initialState.index -> -1
                else -> 0
            }
            (
                slideInHorizontally(
                    animationSpec = spatialMotion,
                    initialOffsetX = { width -> direction * width / 8 },
                ) + fadeIn(animationSpec = effectsMotion)
                ).togetherWith(
                slideOutHorizontally(
                    animationSpec = spatialMotion,
                    targetOffsetX = { width -> -direction * width / 8 },
                ) + fadeOut(animationSpec = effectsMotion),
            )
        },
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
