// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import engine.network.withProxyAuthenticator
import engine.network.ProxyCredentials
import engine.network.toSocksProxy
import app.ProjectInfo
import utils.writeAtomically
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

internal class AndroidResourceFileDownloader {
    fun download(
        url: String,
        target: File,
        proxy: AndroidResourceFileDownloadProxy? = null,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) {
        proxy.withProxyAuthenticator {
            val connection = URI.create(url).toUrlConnection(proxy)
            try {
                AndroidResourceFileDownloadCancellation.track(connection)
                AndroidResourceFileDownloadCancellation.throwIfCancelled()
                val code = connection.responseCode
                AndroidResourceFileDownloadCancellation.throwIfCancelled()
                if (code !in 200..299) {
                    error("HTTP $code")
                }
                val totalBytes = connection.contentLengthLong
                connection.inputStream.use { input ->
                    writeAtomically(target) { output ->
                        input.copyToWithProgress(output, totalBytes, onProgress)
                    }
                }
            } catch (error: Throwable) {
                if (AndroidResourceFileDownloadCancellation.isCancelled()) {
                    throw AndroidResourceFileDownloadCancelledException()
                }
                throw error
            } finally {
                AndroidResourceFileDownloadCancellation.untrack(connection)
                connection.disconnect()
            }
        }
    }
}

internal data class AndroidResourceFileDownloadProxy(
    override val host: String,
    override val port: Int,
    override val username: String,
    override val password: String,
) : ProxyCredentials

private fun URI.toUrlConnection(proxy: AndroidResourceFileDownloadProxy?): HttpURLConnection {
    val url = toURL()
    val connection = if (proxy == null) {
        url.openConnection()
    } else {
        url.openConnection(proxy.toSocksProxy())
    }
    return (connection as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 60_000
        instanceFollowRedirects = true
        requestMethod = "GET"
        setRequestProperty("User-Agent", ResourceFileDefaultUserAgent)
    }
}

private const val ResourceFileDefaultUserAgent = "${ProjectInfo.PROJECT_NAME}/v${ProjectInfo.VERSION_NAME}"

internal fun overallProgress(
    fileIndex: Int,
    fileCount: Int,
    downloadedBytes: Long,
    totalBytes: Long,
): Int? {
    if (totalBytes <= 0L || fileCount <= 0) return null
    val completedFiles = fileIndex.coerceAtLeast(0).toDouble()
    val currentFileProgress = (downloadedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0)
    return (((completedFiles + currentFileProgress) / fileCount.toDouble()) * 100).toInt().coerceIn(0, 100)
}

private fun java.io.InputStream.copyToWithProgress(
    output: java.io.OutputStream,
    totalBytes: Long,
    onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var downloadedBytes = 0L
    onProgress(downloadedBytes, totalBytes)
    while (true) {
        AndroidResourceFileDownloadCancellation.throwIfCancelled()
        val bytesRead = read(buffer)
        if (bytesRead < 0) break
        AndroidResourceFileDownloadCancellation.throwIfCancelled()
        output.write(buffer, 0, bytesRead)
        downloadedBytes += bytesRead
        onProgress(downloadedBytes, totalBytes)
    }
}
