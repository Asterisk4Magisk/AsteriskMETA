// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.effects.ProxyStatusSynchronizer
import app.effects.LauncherIconSynchronizer
import app.effects.MihomoRuntimeSynchronizer
import app.effects.ResourceFileSynchronizer
import app.effects.SubscriptionAutoUpdater
import app.effects.RootBootScriptSynchronizer
import app.effects.Tun2SocksRuntimeFileSynchronizer
import features.logs.AndroidCoreLogRepository
import features.logs.AndroidLogcatRepository
import data.AndroidAppStateStore
import engine.mihomo.MihomoProfileContentStore
import engine.mihomo.runtime.MihomoRuntimeRepository
import engine.proxy.AndroidProxyEngine
import engine.proxy.ProxyServiceUseCase
import features.resources.ResourceFileUseCase
import features.settings.locale.ProvideAppLanguage
import features.settings.locale.RecreateActivityOnAppLanguageChange
import features.settings.usecase.SwitchRunModeUseCase
import features.settings.usecase.RootBootScriptUseCase
import features.settings.usecase.RootEbpfProbeUseCase
import features.subscription.runtime.AndroidMihomoProviderFetcher
import features.subscription.runtime.AndroidSubscriptionFetcher
import system.AndroidNetworkInterfaceProvider
import system.AndroidPackageProvider
import system.AndroidRootShellGateway
import system.AndroidUserSpaceProvider
import ui.AppTheme
import ui.feedback.AndroidToastTipNotifier
import ui.keyColorFor

@Composable
fun App(
    padding: PaddingValues = PaddingValues(0.dp),
    qrCodeScanner: suspend () -> String?,
    resourceFilePicker: suspend () -> Uri?,
    mihomoProfileFilePicker: suspend () -> Uri?,
    logFileCreator: suspend (String) -> Uri?,
    requestVpnPermission: suspend (Intent) -> Boolean,
) {
    val appContext = LocalContext.current.applicationContext
    val appScope = (appContext as AsteriskApplication).appScope
    val rootAccess = remember { AndroidRootShellGateway() }
    val userSpaces = remember(appContext, rootAccess) {
        AndroidUserSpaceProvider(
            context = appContext,
            rootAccess = rootAccess,
        )
    }
    val packageCatalog = remember(appContext, rootAccess, userSpaces) {
        AndroidPackageProvider(
            context = appContext,
            rootAccess = rootAccess,
            userSpaces = userSpaces,
        )
    }
    val networkInterfaces = remember(rootAccess) {
        AndroidNetworkInterfaceProvider(rootAccess)
    }
    val resourceFileUseCase = remember(appContext, resourceFilePicker) {
        ResourceFileUseCase(
            context = appContext,
            resourceFilePicker = resourceFilePicker,
        )
    }
    val subscriptionFetcher = remember { AndroidSubscriptionFetcher() }
    val mihomoProfileContentStore = remember(appContext) {
        MihomoProfileContentStore(appContext)
    }
    val mihomoProviderFetcher = remember(appContext) {
        AndroidMihomoProviderFetcher(
            context = appContext,
        )
    }
    val mihomoRuntime = remember(appScope, appContext) {
        MihomoRuntimeRepository(appScope, appContext)
    }
    val proxyEngine = remember(appContext, rootAccess) {
        AndroidProxyEngine(
            context = appContext,
            rootAccess = rootAccess,
            requestVpnPermission = requestVpnPermission,
        )
    }
    val rootBootScriptUseCase = remember(appContext, rootAccess) {
        RootBootScriptUseCase(
            context = appContext,
            rootAccess = rootAccess,
        )
    }
    val rootEbpfProbeUseCase = remember(appContext, rootAccess) {
        RootEbpfProbeUseCase(
            context = appContext,
            rootAccess = rootAccess,
        )
    }
    val switchRunModeUseCase = remember(proxyEngine, rootAccess, rootBootScriptUseCase) {
        SwitchRunModeUseCase(
            context = appContext,
            proxyEngine = proxyEngine,
            rootAccess = rootAccess,
            rootBootScriptUseCase = rootBootScriptUseCase,
        )
    }
    val proxyServiceUseCase = remember(proxyEngine) {
        ProxyServiceUseCase(proxyEngine)
    }
    val stateStore = remember(appContext) { AndroidAppStateStore.get(appContext) }
    val tipNotifier = remember(appContext) { AndroidToastTipNotifier(appContext) }
    val services = remember(
        appScope,
        proxyEngine,
        rootAccess,
        userSpaces,
        packageCatalog,
        networkInterfaces,
        resourceFileUseCase,
        mihomoProfileContentStore,
        subscriptionFetcher,
        mihomoProviderFetcher,
        qrCodeScanner,
        mihomoProfileFilePicker,
        mihomoRuntime,
        proxyServiceUseCase,
        switchRunModeUseCase,
        rootBootScriptUseCase,
        rootEbpfProbeUseCase,
        tipNotifier,
        logFileCreator,
    ) {
        AppServices(
            appScope = appScope,
            proxyEngine = proxyEngine,
            rootAccess = rootAccess,
            userSpaces = userSpaces,
            packageCatalog = packageCatalog,
            networkInterfaces = networkInterfaces,
            resourceFileUseCase = resourceFileUseCase,
            mihomoProfileContentStore = mihomoProfileContentStore,
            subscriptionFetcher = subscriptionFetcher,
            mihomoProviderFetcher = mihomoProviderFetcher,
            qrCodeScanner = qrCodeScanner,
            mihomoProfileFilePicker = mihomoProfileFilePicker,
            mihomoRuntime = mihomoRuntime,
            proxyServiceUseCase = proxyServiceUseCase,
            switchRunModeUseCase = switchRunModeUseCase,
            rootBootScriptUseCase = rootBootScriptUseCase,
            rootEbpfProbeUseCase = rootEbpfProbeUseCase,
            tipNotifier = tipNotifier,
            logFileCreator = logFileCreator,
            coreLogRepository = AndroidCoreLogRepository,
            logcatRepository = AndroidLogcatRepository,
        )
    }
    val chromeState by stateStore.collectAppChromeState()
    val updateAppState: ((AppState) -> AppState) -> Unit = remember(stateStore) {
        { transform -> stateStore.update(transform) }
    }
    val keyColor = keyColorFor(chromeState.seedIndex)
    RecreateActivityOnAppLanguageChange(languageMode = chromeState.languageMode)
    ProxyStatusSynchronizer(
        stateStore = stateStore,
        proxyEngine = proxyEngine,
        updateAppState = updateAppState,
    )
    MihomoRuntimeSynchronizer(
        stateStore = stateStore,
        proxyEngine = proxyEngine,
        mihomoRuntime = mihomoRuntime,
        updateAppState = updateAppState,
    )
    ResourceFileSynchronizer(
        resourceFileUseCase = resourceFileUseCase,
    )
    LauncherIconSynchronizer(
        context = appContext,
        stateStore = stateStore,
    )
    SubscriptionAutoUpdater(
        stateStore = stateStore,
        subscriptionFetcher = subscriptionFetcher,
        contentStore = mihomoProfileContentStore,
        providerFetcher = mihomoProviderFetcher,
        updateAppState = updateAppState,
    )
    RootBootScriptSynchronizer(
        stateStore = stateStore,
        rootBootScriptUseCase = rootBootScriptUseCase,
    )
    Tun2SocksRuntimeFileSynchronizer(
        context = appContext,
        stateStore = stateStore,
    )

    ProvideAppLanguage(languageMode = chromeState.languageMode) {
        AppTheme(
            colorMode = chromeState.colorMode,
            keyColor = keyColor,
        ) {
            CompositionLocalProvider(
                LocalAppStateStore provides stateStore,
                LocalAppChromeState provides chromeState,
                LocalUpdateAppState provides updateAppState,
                LocalAppServices provides services,
            ) {
                AppContent(padding = padding)
            }
        }
    }
}
