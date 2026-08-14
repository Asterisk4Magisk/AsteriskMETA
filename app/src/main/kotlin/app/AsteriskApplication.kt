// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app

import android.app.Application
import com.github.kr328.clash.common.Global
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
import features.mihomo.provider.MihomoProviderUsageStateHolder
import features.mihomo.provider.loadSelectedMihomoProviderUsageState

class AsteriskApplication : Application(), SingletonImageLoader.Factory {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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

    private lateinit var foregroundTracker: AppActivityForegroundTracker

    override fun onCreate() {
        super.onCreate()
        Global.init(this)
        AndroidLogcatRepository.initialize(applicationContext)
        AndroidCoreLogRepository.initialize(applicationContext)
        AndroidAsteriskdLogRepository.initialize(applicationContext)
        foregroundTracker = AppActivityForegroundTracker(mihomoRuntimeLifecycle)
        registerActivityLifecycleCallbacks(foregroundTracker)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(AndroidAppIconFetcher.Factory(this@AsteriskApplication))
                add(AndroidAppIconFetcher.CacheKeyer())
            }
            .build()
    }
}
