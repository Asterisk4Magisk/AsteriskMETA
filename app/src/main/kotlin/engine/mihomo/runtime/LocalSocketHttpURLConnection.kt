// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.runtime

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** One HTTP exchange with the app-private Mihomo controller. */
internal class LocalSocketHttpURLConnection(url: URL, private val socketPath: String) : HttpURLConnection(url) {
    @Volatile
    private var socket: LocalSocket? = null
    @Volatile
    private var disconnected = false
    private val requestBody = ByteArrayOutputStream()
    private var requestHeaders: Map<String, List<String>> = emptyMap()
    private val responseHeaders = linkedMapOf<String?, List<String>>()
    private var responseBody: InputStream? = null

    override fun setRequestMethod(method: String) {
        if (connected) throw ProtocolException("Already connected")
        if (method !in setOf("GET", "POST", "HEAD", "OPTIONS", "PUT", "DELETE", "TRACE", "PATCH")) {
            throw ProtocolException("Invalid HTTP method: $method")
        }
        this.method = method
    }

    override fun connect() {
        if (disconnected) throw IOException("Controller socket is closed")
        if (connected) return
        requestHeaders = super.getRequestProperties().mapValues { (_, values) -> values.toList() }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(connectTimeout.toLong())
        while (true) {
            val candidate = LocalSocket()
            try {
                // Allocate the fd before the worker starts, so timeout cleanup cannot race its creation.
                candidate.inputStream
                candidate.soTimeout = readTimeout
                synchronized(this) {
                    if (disconnected) throw IOException("Controller socket is closed")
                    socket = candidate
                }
                val address = LocalSocketAddress(socketPath, LocalSocketAddress.Namespace.FILESYSTEM)
                if (connectTimeout == 0) {
                    candidate.connect(address)
                } else {
                    // LocalSocket's public timed-connect overload is unsupported on Android.
                    val pending = ConnectExecutor.submit { candidate.connect(address) }
                    try {
                        pending.get((deadline - System.nanoTime()).coerceAtLeast(1L), TimeUnit.NANOSECONDS)
                    } catch (error: TimeoutException) {
                        pending.cancel(true)
                        throw SocketTimeoutException("Mihomo controller connection timed out").apply { initCause(error) }
                    } catch (error: ExecutionException) {
                        throw error.cause as? IOException ?: IOException("Mihomo controller connection failed", error.cause)
                    } catch (error: InterruptedException) {
                        pending.cancel(true)
                        Thread.currentThread().interrupt()
                        throw InterruptedIOException("Mihomo controller connection interrupted").apply { initCause(error) }
                    }
                }
                connected = true
                return
            } catch (error: IOException) {
                runCatching { candidate.close() }
                if (disconnected || error is InterruptedIOException) throw error
                // The controller is opened asynchronously after START. Retry only before any request bytes
                // are written; this also makes mutations safe from accidental duplicate application.
                val remaining = deadline - System.nanoTime()
                if (connectTimeout == 0 || remaining <= 0L) {
                    throw ConnectException("Unable to connect to Mihomo controller").apply { initCause(error) }
                }
                try {
                    TimeUnit.NANOSECONDS.sleep(minOf(remaining, TimeUnit.MILLISECONDS.toNanos(50)))
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException("Mihomo controller connection interrupted").apply { initCause(interrupted) }
                }
                if (System.nanoTime() >= deadline) {
                    throw ConnectException("Unable to connect to Mihomo controller").apply { initCause(error) }
                }
            }
        }
    }

    override fun getOutputStream(): OutputStream {
        if (!doOutput) throw ProtocolException("Output is disabled")
        if (responseBody != null) throw ProtocolException("Request has already been sent")
        connect()
        return requestBody
    }

    override fun setReadTimeout(timeout: Int) {
        super.setReadTimeout(timeout)
        socket?.soTimeout = timeout
    }

    override fun getResponseCode(): Int {
        readResponse()
        return responseCode
    }

    override fun getResponseMessage(): String {
        readResponse()
        return responseMessage
    }

    override fun getInputStream(): InputStream {
        readResponse()
        if (responseCode !in 200..299) throw IOException("HTTP $responseCode $responseMessage")
        return responseBody!!
    }

    override fun getErrorStream(): InputStream? {
        readResponse()
        return responseBody.takeIf { responseCode !in 200..299 }
    }

    override fun getHeaderFields(): Map<String?, List<String>> {
        readResponse()
        return responseHeaders.toMap()
    }

    override fun getHeaderField(name: String?): String? {
        readResponse()
        return responseHeaders.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.lastOrNull()
    }

    override fun getHeaderField(index: Int): String? {
        readResponse()
        return responseHeaders.values.elementAtOrNull(index)?.lastOrNull()
    }

    override fun getHeaderFieldKey(index: Int): String? {
        readResponse()
        return responseHeaders.keys.elementAtOrNull(index)
    }

    override fun usingProxy(): Boolean = false

    override fun disconnect() {
        val activeSocket = synchronized(this) {
            disconnected = true
            connected = false
            socket.also { socket = null }
        }
        runCatching { activeSocket?.shutdownInput() }
        runCatching { activeSocket?.close() }
    }

    private fun readResponse() {
        if (responseBody != null) return
        connect()
        val activeSocket = socket ?: throw IOException("Controller socket is closed")
        val body = requestBody.toByteArray()
        val headers = buildString {
            append("$method ${url.file.ifEmpty { "/" }} HTTP/1.1\r\n")
            append("Host: localhost\r\nConnection: close\r\n")
            requestHeaders.forEach { (name, values) ->
                if (name.lowercase() !in setOf("host", "connection", "content-length", "transfer-encoding")) {
                    values.forEach { value -> append("$name: $value\r\n") }
                }
            }
            if (doOutput || body.isNotEmpty()) append("Content-Length: ${body.size}\r\n")
            append("\r\n")
        }
        activeSocket.outputStream.apply {
            write(headers.toByteArray(Charsets.ISO_8859_1))
            if (body.isNotEmpty()) write(body)
            flush()
        }
        val input = BufferedInputStream(ControllerInput(activeSocket))
        do {
            responseHeaders.clear()
            val status = input.readHttpLine()
            val parts = status.split(' ', limit = 3)
            if (parts.size < 2 || !parts[0].startsWith("HTTP/1.")) throw ProtocolException("Invalid HTTP status line")
            responseCode = parts[1].toIntOrNull() ?: throw ProtocolException("Invalid HTTP status code")
            responseMessage = parts.getOrElse(2) { "" }
            responseHeaders[null] = listOf(status)
            while (true) {
                val line = input.readHttpLine()
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator <= 0) throw ProtocolException("Invalid HTTP response header")
                val name = line.substring(0, separator)
                val key = responseHeaders.keys.firstOrNull { it.equals(name, ignoreCase = true) } ?: name
                responseHeaders[key] = responseHeaders[key].orEmpty() + line.substring(separator + 1).trim()
            }
        } while (responseCode in 100..199 && responseCode != 101)
        val transferEncoding = responseHeaders.entries.firstOrNull { it.key.equals("Transfer-Encoding", true) }
            ?.value?.joinToString(",")
        val lengthValue = responseHeaders.entries.firstOrNull { it.key.equals("Content-Length", true) }?.value?.lastOrNull()
        responseBody = when {
            method == "HEAD" || responseCode in 100..199 || responseCode == 204 || responseCode == 304 ->
                ByteArray(0).inputStream()
            transferEncoding?.split(',')?.lastOrNull()?.trim().equals("chunked", true) -> ChunkedBody(input)
            lengthValue != null -> {
                val length = lengthValue.toLongOrNull()?.takeIf { it >= 0 }
                    ?: throw ProtocolException("Invalid HTTP content length")
                LimitedBody(input, length)
            }
            else -> input
        }
    }

    private companion object {
        val ConnectExecutor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "MihomoControllerConnect").apply { isDaemon = true }
        }
    }
}

/** LocalSocket's JNI reader loses errno and reports a receive timeout as plain IOException. */
private class ControllerInput(private val socket: LocalSocket) : InputStream() {
    override fun read(): Int {
        val byte = ByteArray(1)
        return if (read(byte, 0, 1) == -1) -1 else byte[0].toInt() and 0xff
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > bytes.size - length) throw IndexOutOfBoundsException()
        if (length == 0) return 0
        return try {
            val count = Os.read(socket.fileDescriptor, bytes, offset, length)
            if (count == 0) -1 else count
        } catch (error: ErrnoException) {
            // This socket is blocking: EAGAIN means SO_RCVTIMEO expired, not a short read.
            if (error.errno == OsConstants.EAGAIN || error.errno == OsConstants.ETIMEDOUT) {
                throw SocketTimeoutException("Mihomo controller read timed out").apply { initCause(error) }
            }
            throw IOException(error.message, error)
        }
    }

    override fun close() = socket.close()
}

private fun InputStream.readHttpLine(): String {
    val line = ByteArrayOutputStream()
    while (true) {
        val next = read()
        if (next == -1) throw EOFException("Unexpected end of HTTP headers")
        if (next == '\n'.code) {
            val bytes = line.toByteArray()
            if (bytes.isEmpty() || bytes.last() != '\r'.code.toByte()) throw ProtocolException("Invalid HTTP line ending")
            return String(bytes, 0, bytes.size - 1, Charsets.ISO_8859_1)
        }
        line.write(next)
    }
}

private class LimitedBody(private val input: InputStream, private var remaining: Long) : InputStream() {
    override fun read(): Int {
        if (remaining == 0L) return -1
        val next = input.read()
        if (next == -1) throw EOFException("Truncated HTTP body")
        remaining--
        return next
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > bytes.size - length) throw IndexOutOfBoundsException()
        if (length == 0) return 0
        if (remaining == 0L) return -1
        val count = input.read(bytes, offset, minOf(length.toLong(), remaining).toInt())
        if (count == -1) throw EOFException("Truncated HTTP body")
        remaining -= count
        return count
    }

    override fun close() = input.close()
}

private class ChunkedBody(private val input: InputStream) : InputStream() {
    private var remaining = 0L
    private var firstChunk = true
    private var finished = false

    override fun read(): Int {
        val byte = ByteArray(1)
        return if (read(byte, 0, 1) == -1) -1 else byte[0].toInt() and 0xff
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > bytes.size - length) throw IndexOutOfBoundsException()
        if (length == 0) return 0
        if (finished) return -1
        if (remaining == 0L) {
            if (!firstChunk && input.readHttpLine().isNotEmpty()) throw ProtocolException("Invalid HTTP chunk ending")
            firstChunk = false
            remaining = input.readHttpLine().substringBefore(';').trim().toLongOrNull(16)?.takeIf { it >= 0 }
                ?: throw ProtocolException("Invalid HTTP chunk size")
            if (remaining == 0L) {
                while (input.readHttpLine().isNotEmpty()) { /* Consume trailers. */ }
                finished = true
                return -1
            }
        }
        val count = input.read(bytes, offset, minOf(length.toLong(), remaining).toInt())
        if (count == -1) throw EOFException("Truncated HTTP chunk")
        remaining -= count
        return count
    }

    override fun close() = input.close()
}
