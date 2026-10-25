// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.effects.MihomoAutoSwitchSynchronizer
import app.effects.MihomoRuntimeSynchronizer
import app.effects.ProxyStatusSynchronizer
import app.effects.ResourceFileSynchronizer
import app.effects.EnhancementModuleUpgradeChecker
import data.backup.AndroidAppBackupDocumentGateway
import data.backup.AppBackupUseCase
import features.enhancement.AndroidEnhancementModuleBackend
import features.enhancement.EnhancementModuleCoordinator
import features.enhancement.EnhancementModulePrompt
import app.effects.RootBootScriptSynchronizer
import app.effects.TrafficStatsNotificationSynchronizer
import engine.proxy.VpnPermissionRequester
import engine.proxy.ProxyServiceUseCase
import features.logs.AndroidAsteriskdLogRepository
import features.logs.AndroidCoreLogRepository
import features.logs.AndroidLogcatRepository
import features.monitoring.MonitoringRepository
import features.resources.ResourceFileUseCase
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
    backupFilePicker: suspend () -> Uri?,
    backupFileCreator: suspend (String) -> Uri?,
    logFileCreator: suspend (String) -> Uri?,
    requestVpnPermission: VpnPermissionRequester,
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
    val resourceFileUpdateCoordinator = application.resourceFileUpdateCoordinator
    val mihomoProfilePreparer = application.mihomoProfilePreparer
    val mihomoProfileContentStore = application.mihomoProfileContentStore
    val appBackupUseCase = remember(appContext, backupFilePicker, backupFileCreator, mihomoProfileContentStore) {
        AppBackupUseCase(
            gateway = AndroidAppBackupDocumentGateway(
                context = appContext,
                filePicker = backupFilePicker,
                fileCreator = backupFileCreator,
            ),
            profileContentStore = mihomoProfileContentStore,
        )
    }
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
    val proxyEngine = remember(application) { application.proxyEngine }
    val enhancementModuleCoordinator = remember(appContext) {
        EnhancementModuleCoordinator { AndroidEnhancementModuleBackend(appContext) }
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
    val proxyServiceUseCase = remember(proxyEngine, requestVpnPermission) {
        ProxyServiceUseCase(proxyEngine, requestVpnPermission)
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
        appBackupUseCase,
        mihomoProfileContentStore,
        mihomoProfilePreparer,
        mihomoProviderFetcher,
        qrCodeScanner,
        mihomoProfileFilePicker,
        mihomoRuntime,
        mihomoProviderUsage,
        monitoring,
        proxyServiceUseCase,
        requestVpnPermission,
        switchRunModeUseCase,
        applyServiceControlUseCase,
        rootBootScriptUseCase,
        rootEbpfProbeUseCase,
        enhancementModuleCoordinator,
        tipNotifier,
        logFileCreator,
    ) {
        AppServices(
            appScope = appScope,
            homeServiceOperationState = application.homeServiceOperationState,
            proxyEngine = proxyEngine,
            rootAccess = rootAccess,
            userSpaces = userSpaces,
            packageCatalog = packageCatalog,
            networkInterfaces = networkInterfaces,
            resourceFileUseCase = resourceFileUseCase,
            resourceFileUpdateCoordinator = resourceFileUpdateCoordinator,
            appBackupUseCase = appBackupUseCase,
            mihomoProfileContentStore = mihomoProfileContentStore,
            mihomoProfilePreparer = mihomoProfilePreparer,
            mihomoProviderFetcher = mihomoProviderFetcher,
            qrCodeScanner = qrCodeScanner,
            mihomoProfileFilePicker = mihomoProfileFilePicker,
            mihomoRuntime = mihomoRuntime,
            mihomoProviderUsage = mihomoProviderUsage,
            monitoring = monitoring,
            proxyServiceUseCase = proxyServiceUseCase,
            requestVpnPermission = requestVpnPermission,
            switchRunModeUseCase = switchRunModeUseCase,
            applyServiceControlUseCase = applyServiceControlUseCase,
            rootBootScriptUseCase = rootBootScriptUseCase,
            rootEbpfProbeUseCase = rootEbpfProbeUseCase,
            enhancementModuleCoordinator = enhancementModuleCoordinator,
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
    MihomoAutoSwitchSynchronizer(
        stateStore = stateStore,
        runtime = mihomoRuntime,
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

    EnhancementModuleUpgradeChecker(stateStore, enhancementModuleCoordinator)

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
                EnhancementModulePrompt()
                features.resources.SharedCoreMigrationPrompt()
            }
        }
    }
}
