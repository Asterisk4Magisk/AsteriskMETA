// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app

import android.app.Application
import com.github.kr328.clash.common.Global
import features.logs.AndroidCoreLogRepository
import features.logs.AndroidLogcatRepository
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import system.AndroidAppIconFetcher

class AsteriskApplication : Application(), SingletonImageLoader.Factory {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Global.init(this)
        AndroidLogcatRepository.initialize(applicationContext)
        AndroidCoreLogRepository.initialize(applicationContext)
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
