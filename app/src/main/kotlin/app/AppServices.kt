// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app

import androidx.compose.runtime.staticCompositionLocalOf
import android.net.Uri
import engine.mihomo.MihomoProfileContentStore
import engine.proxy.AndroidProxyEngine
import engine.proxy.ProxyServiceUseCase
import engine.mihomo.runtime.MihomoRuntimeRepository
import features.mihomo.provider.MihomoProviderUsageStateHolder
import features.logs.CoreLogRepository
import features.monitoring.MonitoringRepository
import features.resources.ResourceFileUpdateCoordinator
import features.resources.ResourceFileUseCase
import features.settings.usecase.SwitchRunModeUseCase
import features.settings.usecase.RootBootScriptUseCase
import features.settings.usecase.RootEbpfProbeUseCase
import features.subscription.runtime.AndroidMihomoProviderFetcher
import features.subscription.runtime.AndroidMihomoProfilePreparer
import kotlinx.coroutines.CoroutineScope
import system.AndroidNetworkInterfaceProvider
import system.AndroidPackageProvider
import system.AndroidRootShellGateway
import system.AndroidUserSpaceProvider
import ui.feedback.AndroidToastTipNotifier

internal data class AppServices(
    val appScope: CoroutineScope,
    val proxyEngine: AndroidProxyEngine,
    val rootAccess: AndroidRootShellGateway,
    val userSpaces: AndroidUserSpaceProvider,
    val packageCatalog: AndroidPackageProvider,
    val networkInterfaces: AndroidNetworkInterfaceProvider,
    val resourceFileUseCase: ResourceFileUseCase,
    val resourceFileUpdateCoordinator: ResourceFileUpdateCoordinator,
    val mihomoProfileContentStore: MihomoProfileContentStore,
    val mihomoProfilePreparer: AndroidMihomoProfilePreparer,
    val mihomoProviderFetcher: AndroidMihomoProviderFetcher,
    val qrCodeScanner: suspend () -> String?,
    val mihomoProfileFilePicker: suspend () -> Uri?,
    val mihomoRuntime: MihomoRuntimeRepository,
    val mihomoProviderUsage: MihomoProviderUsageStateHolder,
    val monitoring: MonitoringRepository,
    val proxyServiceUseCase: ProxyServiceUseCase,
    val switchRunModeUseCase: SwitchRunModeUseCase,
    val rootBootScriptUseCase: RootBootScriptUseCase,
    val rootEbpfProbeUseCase: RootEbpfProbeUseCase,
    val tipNotifier: AndroidToastTipNotifier,
    val logFileCreator: suspend (String) -> Uri?,
    val coreLogRepository: CoreLogRepository,
    val logcatRepository: CoreLogRepository,
)

internal val LocalAppServices = staticCompositionLocalOf<AppServices> {
    error("LocalAppServices is not provided")
}
