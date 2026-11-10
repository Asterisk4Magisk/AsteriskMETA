// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app

import features.resources.runtime.ResourceAutoUpdateScheduler
import features.resources.resourceAutoUpdateIntervalMillis
import features.resources.ResourceFileUseCase
import features.resources.ResourceFileUpdateCoordinator
import features.resources.ResourceFileUpdateRequest
import features.resources.runtime.AndroidResourceFileDownloadCancellation
import android.app.Application
import app.modes.RunModeVpnService
import features.boot.BootAutoConnect
import features.quicksettings.quickSettingsTileState
import features.quicksettings.refreshQuickSettingsTileUnlessShowing
import features.home.HomeServiceOperationState
import engine.mihomo.binding.MihomoBridge
import engine.proxy.LocalProxyRuntime
import engine.vpn.AsteriskVpnService
import engine.vpn.VpnSession
import app.effects.synchronizeProxyStatus
import app.effects.RunningConfigurationApplier
import androidx.compose.runtime.snapshotFlow
import engine.mihomo.selectedMihomoProfileOrNull
import engine.proxy.ProxyServiceResult
import engine.proxy.ProxyServiceUseCase
import features.home.HomeServiceOperation
import features.logs.AndroidAppLogger
import features.resources.ResourceFileUpdateResult
import features.resources.isBusy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ui.feedback.AndroidToastTipNotifier
import features.logs.AndroidCoreLogRepository
import features.logs.AndroidAsteriskdLogRepository
import features.logs.AndroidLogcatRepository
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import system.AndroidAppIconFetcher
import app.effects.AppActivityForegroundTracker
import app.effects.MihomoRuntimeLifecycleCoordinator
import engine.mihomo.MihomoProfileContentStore
import engine.mihomo.runtime.MihomoRuntimeRepository
import data.AndroidAppStateStore
import engine.proxy.AndroidProxyEngine
import engine.proxy.ProfileApplyException
import engine.proxy.ProfileApplyFailure
import engine.proxy.ProxyEngineStartRequest
import system.AndroidRootShellGateway
import features.mihomo.provider.MihomoProviderUsageStateHolder
import features.mihomo.provider.loadSelectedMihomoProviderUsageState
import features.subscription.runtime.AndroidMihomoProfilePreparer
import features.subscription.runtime.AndroidSubscriptionScheduleGateway
import features.subscription.runtime.SubscriptionScheduler
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AsteriskApplication : Application(), SingletonImageLoader.Factory {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    internal val homeServiceOperationState = HomeServiceOperationState()
    internal val stateStore: AndroidAppStateStore by lazy {
        AndroidAppStateStore.get(applicationContext)
    }

    /** Shared by every entry point so proxy and traffic stats operations are serialized. */
    internal val proxyEngine: AndroidProxyEngine by lazy {
        AndroidProxyEngine(
            context = applicationContext,
            rootAccess = AndroidRootShellGateway(),
            appScope = appScope,
        )
    }
    internal val mihomoProfilePreparer: AndroidMihomoProfilePreparer by lazy {
        AndroidMihomoProfilePreparer(applicationContext)
    }
    internal val mihomoRuntime: MihomoRuntimeRepository by lazy { MihomoRuntimeRepository(appScope, this) }
    internal val mihomoProfileContentStore: MihomoProfileContentStore by lazy {
        MihomoProfileContentStore(this)
    }
    internal val mihomoProviderUsage: MihomoProviderUsageStateHolder by lazy {
        MihomoProviderUsageStateHolder(appScope) { appState ->
            loadSelectedMihomoProviderUsageState(
                context = this,
                contentStore = mihomoProfileContentStore,
                runtime = mihomoRuntime,
                appState = appState,
            )
        }
    }
    internal val mihomoRuntimeLifecycle: MihomoRuntimeLifecycleCoordinator by lazy {
        MihomoRuntimeLifecycleCoordinator(appScope, mihomoRuntime)
    }
    private val subscriptionScheduler: SubscriptionScheduler by lazy {
        SubscriptionScheduler(AndroidSubscriptionScheduleGateway(applicationContext))
    }

    private lateinit var foregroundTracker: AppActivityForegroundTracker

    private val resourceFileUseCase by lazy {
        ResourceFileUseCase(
            context = this,
            resourceFilePicker = { null },
            currentRunMode = { stateStore.state.value.runMode },
        )
    }
    internal val resourceFileUpdateCoordinator by lazy {
        ResourceFileUpdateCoordinator(
            scope = appScope,
            execute = { request ->
                when (request) {
                    is ResourceFileUpdateRequest.BuiltIn -> resourceFileUseCase.update(
                        kind = request.kind,
                        source = request.source,
                        options = request.options,
                    )
                    is ResourceFileUpdateRequest.All -> resourceFileUseCase.update(
                        source = request.source,
                        options = request.options,
                    )
                }
            },
            cancelRunning = AndroidResourceFileDownloadCancellation::cancel,
        )
    }

    /** Applies successful resource updates and selected-profile changes to a running core. */
    private val runningConfigurationApplier by lazy {
        RunningConfigurationApplier(
            scope = appScope,
            updatesBusy = resourceFileUpdateCoordinator.state.map { state -> state.isBusy },
            apply = ::applyConfigurationToRunningCore,
            reportFailure = { error ->
                AndroidAppLogger.error(RunningConfigurationLogTag, "Automatic configuration apply failed", error)
            },
        )
    }

    /**
     * Requests an apply whenever the selected profile changes in a way the core would see:
     * a subscription update or a save from any entry point. Selecting another profile is not
     * an edit, and stops the service on its own.
     */
    private fun applySelectedProfileChanges() {
        appScope.launch {
            var previous: Pair<Int, MihomoProfileState?>? = null
            stateStore.state
                .map { state -> state.selectedMihomoProfileId to state.selectedMihomoProfileOrNull() }
                .distinctUntilChanged()
                .collect { current ->
                    val prior = previous
                    previous = current
                    if (prior == null || prior.first != current.first) return@collect
                    val before = prior.second ?: return@collect
                    val after = current.second ?: return@collect
                    // Every sync writes a new content file; identical content is not a change.
                    val comparable = if (before.contentSha256 == after.contentSha256) {
                        after.copy(contentPath = before.contentPath)
                    } else {
                        after
                    }
                    if (before.hasRuntimeRelevantChanges(comparable)) runningConfigurationApplier.request()
                }
        }
    }

    /**
     * An in-place reload through the core's control API in VPN and ROOT modes. It claims the
     * home screen's service operation state, so the power button and this apply exclude each
     * other, and a stopped core is left alone. What was saved stays saved when the apply fails.
     */
    private suspend fun applyConfigurationToRunningCore() {
        while (true) {
            snapshotFlow { homeServiceOperationState.isIdle() }.first { idle -> idle }
            val claimed = withContext(Dispatchers.Main.immediate) {
                homeServiceOperationState.isIdle().also { idle ->
                    if (idle) homeServiceOperationState.operation = HomeServiceOperation.Applying
                }
            }
            if (claimed) break
        }
        try {
            val useCase = ProxyServiceUseCase(proxyEngine)
            val current = stateStore.state.value
            if (useCase.isRunning(current) != true) return
            // `proxyRunning` may lag behind in a process the UI never opened; the probe is live.
            val snapshot = current.copy(proxyRunning = true)
            when (val result = useCase.applyConfiguration(snapshot)) {
                is ProxyServiceResult.Success -> {
                    AndroidAppLogger.info(RunningConfigurationLogTag, "Applied updated configuration")
                    // The proxy pages keep a snapshot per configuration; the core changed under it.
                    if (result.proxyRunning && mihomoRuntime.state.value.running) {
                        mihomoRuntime.refreshProxies(stateStore.state.value)
                        mihomoProviderUsage.refresh(stateStore.state.value)
                    }
                    stateStore.update { state ->
                        state.copy(
                            proxyRunning = result.proxyRunning,
                            localProxyPort = result.appState?.localProxyPort ?: state.localProxyPort,
                            mihomoControlPort = result.appState?.mihomoControlPort ?: state.mihomoControlPort,
                        )
                    }
                }
                is ProxyServiceResult.Failed -> {
                    // A failed restart may already have stopped the core, so read back what runs.
                    useCase.isRunning(snapshot)?.let { running ->
                        stateStore.update { state -> state.copy(proxyRunning = running) }
                    }
                    val notifier = AndroidToastTipNotifier(applicationContext)
                    val applyFailure = result.error as? ProfileApplyException
                    if (applyFailure == null) {
                        notifier.showError(result.error, getString(R.string.proxy_service_auto_apply_failed))
                    } else {
                        notifier.show(applyFailure.tipMessage())
                    }
                }
            }
        } finally {
            withContext(Dispatchers.Main.immediate) {
                homeServiceOperationState.operation = HomeServiceOperation.Idle
            }
        }
    }

    /**
     * Brings the app state in line when a tunnel ends in the service process, whether it was
     * stopped, revoked or its process died. The session watch reports the end, so this costs
     * nothing while the tunnel runs; a tunnel that is up when this process starts is watched
     * from here as well.
     */
    private fun synchronizeOnVpnSessionEnd() {
        appScope.launch {
            VpnSession.ended.collect {
                // Only the VPN mode is resynchronized: other modes are probed through ROOT.
                if (stateStore.state.value.runMode != RunModeVpnService) return@collect
                synchronizeProxyStatus(
                    currentState = { stateStore.state.value },
                    readStatus = { snapshot ->
                        runCatching {
                            proxyEngine.status(snapshot.runMode, snapshot)
                        }.getOrNull()
                    },
                    updateAppState = stateStore::update,
                )
            }
        }
        appScope.launch(Dispatchers.IO) { VpnSession.watch(applicationContext) }
    }

    /**
     * Asks SystemUI to rebind the quick settings tile whenever a state source in this process
     * changes it; the first collection is the refresh at process start. The language is included
     * because opening the panel no longer refreshes an active tile. A `proxyRunning` the tile
     * writes back itself is skipped, since the tile shows it without reading the status again.
     */
    private fun refreshQuickSettingsTileOnProxyStateChange() {
        appScope.launch {
            stateStore.state
                .map { state -> state.quickSettingsTileState() }
                .distinctUntilChanged()
                .collect { tileState -> refreshQuickSettingsTileUnlessShowing(applicationContext, tileState) }
        }
    }

    private fun ProfileApplyException.tipMessage(): String {
        val detail = cause?.message.orEmpty().ifBlank { cause?.javaClass?.simpleName.orEmpty() }
        return when (failure) {
            ProfileApplyFailure.NotPrepared -> getString(R.string.proxy_service_apply_not_prepared, detail)
            ProfileApplyFailure.Rejected -> getString(R.string.proxy_service_apply_rejected, detail)
            ProfileApplyFailure.Unconfirmed -> getString(R.string.proxy_service_apply_unconfirmed)
            ProfileApplyFailure.RuntimeFileNotSaved -> getString(R.string.proxy_service_apply_file_not_saved)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // The `:service` process only executes: the VPN Service with its core and the traffic
        // statistics service. State, schedulers, WorkManager, runtime monitoring and the core log
        // repositories belong to the app process; `:service` only appends its own app log.
        if (AsteriskVpnService.isServiceProcess(this)) {
            MihomoBridge.initialize(this)
            AndroidLogcatRepository.initializeServiceProcess(this)
            return
        }
        // HTTP fetches here go through the tunnel's local proxy, which the service publishes.
        LocalProxyRuntime.attachExternalSource { VpnSession.runningLocalProxyOptions(applicationContext) }
        synchronizeOnVpnSessionEnd()
        applySelectedProfileChanges()
        appScope.launch {
            resourceFileUpdateCoordinator.results.collect { result ->
                if (result is ResourceFileUpdateResult.Success) runningConfigurationApplier.request()
            }
        }
        refreshQuickSettingsTileOnProxyStateChange()
        appScope.launch {
            val scheduler = ResourceAutoUpdateScheduler(applicationContext)
            stateStore.state
                .map { state -> resourceAutoUpdateIntervalMillis(state.enableResourceAutoUpdate, state.resourceAutoUpdateInterval) }
                .distinctUntilChanged()
                .collect(scheduler::reconcile)
        }
        MihomoBridge.initialize(this)
        mihomoRuntime.bindSupervisedRestart { state ->
            val status = proxyEngine.restart(ProxyEngineStartRequest(state))
            check(status.running) { "ROOT restart did not start the core" }
        }
        AndroidLogcatRepository.initialize(applicationContext)
        AndroidCoreLogRepository.initialize(applicationContext)
        AndroidAsteriskdLogRepository.initialize(applicationContext)
        appScope.launch {
            stateStore.state
                .map { state ->
                    state.enableAutoConnect && state.runMode == RunModeVpnService
                }
                .distinctUntilChanged()
                .collect { enabled ->
                    BootAutoConnect.setComponentEnabled(applicationContext, enabled)
                }
        }
        mihomoProfileContentStore.pruneUnreferenced(
            stateStore.state.value.mihomoProfiles
                .mapNotNullTo(mutableSetOf()) { profile -> profile.contentPath.takeIf(String::isNotBlank) },
        )
        foregroundTracker = AppActivityForegroundTracker(mihomoRuntimeLifecycle)
        registerActivityLifecycleCallbacks(foregroundTracker)
        appScope.launch {
            stateStore.state
                .map { state ->
                    state.mihomoProfiles.map { profile ->
                        SubscriptionScheduleKey(
                            id = profile.id,
                            type = profile.type,
                            url = profile.url,
                            interval = profile.updateInterval,
                            enabled = profile.enabled,
                        )
                    }
                }
                .distinctUntilChanged()
                .collect {
                    subscriptionScheduler.reconcile(stateStore.state.value.mihomoProfiles)
                }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(AndroidAppIconFetcher.Factory(this@AsteriskApplication))
                add(AndroidAppIconFetcher.CacheKeyer())
            }
            .build()
    }

    private data class SubscriptionScheduleKey(
        val id: Int,
        val type: MihomoProfileType,
        val url: String,
        val interval: String,
        val enabled: Boolean,
    )
}

private fun HomeServiceOperationState.isIdle(): Boolean =
    operation == HomeServiceOperation.Idle && !modeOperationInProgress

private const val RunningConfigurationLogTag = "RunningConfiguration"
