// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.LogMessage
import features.logs.AndroidAppLogger
import features.logs.AndroidCoreLogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

internal class MihomoCoreLogSubscriber {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var channel: ReceiveChannel<LogMessage>? = null

    fun start() {
        val nextChannel = Clash.subscribeLogcat()
        channel = nextChannel
        scope.launch {
            runCatching {
                for (message in nextChannel) {
                    AndroidCoreLogRepository.appendPersisted(
                        level = message.level.toCoreLogLevel(),
                        message = message.message,
                        timestampMillis = message.time.time,
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                AndroidAppLogger.warn(LogTag, "Failed to collect Mihomo core logs", error)
            }
        }
    }

    fun stop() {
        channel?.cancel()
        channel = null
        scope.cancel()
    }
}

private fun LogMessage.Level.toCoreLogLevel(): String {
    return when (this) {
        LogMessage.Level.Debug -> "debug"
        LogMessage.Level.Warning -> "warning"
        LogMessage.Level.Error -> "error"
        LogMessage.Level.Silent -> "debug"
        LogMessage.Level.Info,
        LogMessage.Level.Unknown -> "info"
    }
}

private const val LogTag = "MihomoCoreLogSubscriber"
