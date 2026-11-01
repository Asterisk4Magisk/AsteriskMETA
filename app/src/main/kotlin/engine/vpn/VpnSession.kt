// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.content.Context
import engine.proxy.LocalProxyOptions
import features.logs.AndroidAppLogger
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/**
 * The cross-process record of a running VPN tunnel.
 *
 * The service holds an exclusive file lock on [FileName] for exactly as long as the tunnel is
 * up, and the file carries the local proxy the tunnel serves. The kernel drops the lock when the
 * service process dies, so a crash can never leave a running session behind; a file that is
 * present but unlocked marks a session that ended without an orderly stop.
 */
internal object VpnSession {
    private const val FileName = "vpn-session"
    private const val LogTag = "VpnSession"
    private const val KeyListenAddress = "listen_address"
    private const val KeyPort = "port"
    private const val KeyUsername = "username"
    private const val KeyPassword = "password"

    private val probeLock = Any()

    /**
     * Whether this process holds the session. The lock cannot be probed from its owner: an
     * overlapping lock in the same JVM throws, and closing a second descriptor of the file would
     * drop every POSIX lock this process holds. The owner therefore answers from here.
     */
    @Volatile
    private var ownedHere = false

    /**
     * Publishes a session from the service process. Closing the result removes the file before
     * the lock is released, so an orderly stop never reads back as an interrupted session.
     */
    fun open(context: Context, localProxyOptions: LocalProxyOptions): Closeable {
        val file = file(context)
        file.parentFile?.mkdirs()
        val access = RandomAccessFile(file, "rw")
        try {
            val lock = access.channel.lock()
            access.setLength(0L)
            access.write(localProxyOptions.toSessionJson().toString().toByteArray(Charsets.UTF_8))
            ownedHere = true
            return Closeable {
                runCatching { file.delete() }
                runCatching { lock.release() }
                runCatching { access.close() }
                ownedHere = false
            }
        } catch (error: Throwable) {
            runCatching { access.close() }
            throw error
        }
    }

    /**
     * Whether the service process currently holds the session. Probes from other processes are
     * serialized because two overlapping probes in one process would throw.
     */
    fun isRunning(context: Context): Boolean {
        if (ownedHere) return true
        val file = file(context)
        return file.isFile && synchronized(probeLock) {
            runCatching {
                RandomAccessFile(file, "r").use { access ->
                    val probe = access.channel.tryLock(0L, Long.MAX_VALUE, true) ?: return@use true
                    probe.release()
                    false
                }
            }.getOrElse { error ->
                AndroidAppLogger.warn(LogTag, "Failed to probe the VPN session", error)
                false
            }
        }
    }

    /** A session that ended without an orderly stop, for example when an app update killed it. */
    fun wasInterrupted(context: Context): Boolean {
        return file(context).isFile && !isRunning(context)
    }

    /** The local proxy of the running session, or null when the tunnel serves none. */
    fun localProxyOptions(context: Context): LocalProxyOptions? {
        val file = file(context)
        if (!file.isFile) return null
        return runCatching {
            val record = JSONObject(file.readText(Charsets.UTF_8))
            LocalProxyOptions(
                listenAddress = record.optString(KeyListenAddress),
                port = record.optInt(KeyPort, 0),
                username = record.optString(KeyUsername),
                password = record.optString(KeyPassword),
            )
        }.getOrNull()?.takeIf { options -> options.port > 0 }
    }

    private fun LocalProxyOptions.toSessionJson(): JSONObject {
        if (port <= 0) return JSONObject()
        return JSONObject()
            .put(KeyListenAddress, listenAddress)
            .put(KeyPort, port)
            .put(KeyUsername, username)
            .put(KeyPassword, password)
    }

    private fun file(context: Context): File = File(context.filesDir, FileName)
}
