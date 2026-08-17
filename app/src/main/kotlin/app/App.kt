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
import app.effects.MihomoRuntimeSynchronizer
import app.effects.ProxyStatusSynchronizer
import app.effects.ResourceFileSynchronizer
import app.effects.RootBootScriptSynchronizer
import app.effects.TrafficStatsNotificationSynchronizer
import engine.proxy.AndroidProxyEngine
import engine.proxy.ProxyServiceUseCase
import features.logs.AndroidAsteriskdLogRepository
import features.logs.AndroidCoreLogRepository
import features.logs.AndroidLogcatRepository
import features.monitoring.MonitoringRepository
import features.resources.ResourceFileUpdateCoordinator
import features.resources.ResourceFileUpdateRequest
import features.resources.ResourceFileUseCase
import features.resources.runtime.AndroidResourceFileDownloadCancellation
import features.settings.locale.ProvideAppLanguage
import features.settings.usecase.RootBootScriptUseCase
import features.settings.usecase.RootEbpfProbeUseCase
import features.settings.usecase.SwitchRunModeUseCase
import features.settings.usecase.ApplyServiceControlUseCase
import features.subscription.runtime.AndroidMihomoProviderFetcher
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
    val systemUiSnapshot = appContext.currentSystemUiSnapshot()
    val application = appContext as AsteriskApplication
    val appScope = application.appScope
    val rootAccess = remember { AndroidRootShellGateway() }
    val stateStore = application.stateStore
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
    val resourceFileUseCase = remember(appContext, resourceFilePicker, rootAccess, stateStore) {
        ResourceFileUseCase(
            context = appContext,
            resourceFilePicker = resourceFilePicker,
            currentRunMode = { stateStore.state.value.runMode },
            rootShell = rootAccess,
        )
    }
    val resourceFileUpdateCoordinator = remember(appScope, resourceFileUseCase) {
        ResourceFileUpdateCoordinator(
            scope = appScope,
            execute = { request ->
                when (request) {
                    is ResourceFileUpdateRequest.BuiltIn -> resourceFileUseCase.update(
                        kind = request.kind,
                        source = request.source,
                        options = request.options,
                        customResourceFiles = request.customResourceFiles,
                    )
                    is ResourceFileUpdateRequest.Custom -> resourceFileUseCase.updateCustom(
                        customFile = request.file,
                        options = request.options,
                        customResourceFiles = request.customResourceFiles,
                    )
                    is ResourceFileUpdateRequest.All -> resourceFileUseCase.update(
                        source = request.source,
                        options = request.options,
                        customResourceFiles = request.customResourceFiles,
                    )
                }
            },
            cancelRunning = AndroidResourceFileDownloadCancellation::cancel,
        )
    }
    val mihomoProfilePreparer = application.mihomoProfilePreparer
    val mihomoProfileContentStore = application.mihomoProfileContentStore
    val mihomoProviderFetcher = remember(appContext) {
        AndroidMihomoProviderFetcher(
            context = appContext,
        )
    }
    val mihomoRuntime = application.mihomoRuntime
    val mihomoProviderUsage = application.mihomoProviderUsage
    val monitoring = remember(appScope, appContext, rootAccess, stateStore, mihomoRuntime) {
        MonitoringRepository(appScope, appContext, rootAccess, stateStore, mihomoRuntime)
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
    val applyServiceControlUseCase = remember(proxyEngine) {
        ApplyServiceControlUseCase(proxyEngine)
    }
    val tipNotifier = remember(appContext) { AndroidToastTipNotifier(appContext) }
    val services = remember(
        appScope,
        proxyEngine,
        rootAccess,
        userSpaces,
        packageCatalog,
        networkInterfaces,
        resourceFileUseCase,
        resourceFileUpdateCoordinator,
        mihomoProfileContentStore,
        mihomoProfilePreparer,
        mihomoProviderFetcher,
        qrCodeScanner,
        mihomoProfileFilePicker,
        mihomoRuntime,
        mihomoProviderUsage,
        monitoring,
        proxyServiceUseCase,
        switchRunModeUseCase,
        applyServiceControlUseCase,
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
            resourceFileUpdateCoordinator = resourceFileUpdateCoordinator,
            mihomoProfileContentStore = mihomoProfileContentStore,
            mihomoProfilePreparer = mihomoProfilePreparer,
            mihomoProviderFetcher = mihomoProviderFetcher,
            qrCodeScanner = qrCodeScanner,
            mihomoProfileFilePicker = mihomoProfileFilePicker,
            mihomoRuntime = mihomoRuntime,
            mihomoProviderUsage = mihomoProviderUsage,
            monitoring = monitoring,
            proxyServiceUseCase = proxyServiceUseCase,
            switchRunModeUseCase = switchRunModeUseCase,
            applyServiceControlUseCase = applyServiceControlUseCase,
            rootBootScriptUseCase = rootBootScriptUseCase,
            rootEbpfProbeUseCase = rootEbpfProbeUseCase,
            tipNotifier = tipNotifier,
            logFileCreator = logFileCreator,
            coreLogRepository = AndroidCoreLogRepository,
            rootLogRepository = AndroidAsteriskdLogRepository,
            logcatRepository = AndroidLogcatRepository,
        )
    }
    val chromeState by stateStore.collectAppChromeState()
    val updateAppState: ((AppState) -> AppState) -> Unit = remember(stateStore) {
        { transform -> stateStore.update(transform) }
    }
    val keyColor = keyColorFor(chromeState.seedIndex)
    MihomoRuntimeSynchronizer(
        stateStore = stateStore,
        mihomoRuntimeLifecycle = application.mihomoRuntimeLifecycle,
        mihomoProviderUsage = mihomoProviderUsage,
    )
    ProxyStatusSynchronizer(
        stateStore = stateStore,
        proxyEngine = proxyEngine,
        updateAppState = updateAppState,
    )
    ResourceFileSynchronizer(
        resourceFileUseCase = resourceFileUseCase,
        stateStore = stateStore,
    )
    RootBootScriptSynchronizer(
        stateStore = stateStore,
        rootBootScriptUseCase = rootBootScriptUseCase,
    )
    TrafficStatsNotificationSynchronizer(
        stateStore = stateStore,
    )

    ProvideAppLanguage(
        languageMode = chromeState.languageMode,
        systemLocale = systemUiSnapshot.locale,
    ) {
        AppTheme(
            colorMode = chromeState.colorMode,
            keyColor = keyColor,
            systemDark = systemUiSnapshot.isDark,
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
