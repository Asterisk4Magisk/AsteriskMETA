// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package utils

import java.io.File
import java.io.OutputStream

internal fun writeAtomically(
    target: File,
    write: (OutputStream) -> Unit,
) {
    val parent = target.parentFile ?: error("Parent directory is unavailable for ${target.absolutePath}")
    parent.mkdirs()
    synchronized(writeLockFor(target)) {
        val tempPrefix = "${target.name}.".let { prefix ->
            if (prefix.length >= 3) prefix else prefix.padEnd(3, '_')
        }
        val tempFile = File.createTempFile(tempPrefix, ".tmp", parent)
        try {
            tempFile.outputStream().use(write)
            if (tempFile.length() <= 0) {
                tempFile.delete()
                error("${target.name} is empty")
            }
            if (target.exists() && !target.delete()) {
                tempFile.delete()
                error("Failed to replace ${target.name}")
            }
            if (!tempFile.renameTo(target)) {
                tempFile.delete()
                error("Failed to replace ${target.name}")
            }
        } catch (error: Throwable) {
            tempFile.delete()
            throw error
        }
    }
}

private val WriteLocks = mutableMapOf<String, Any>()

private fun writeLockFor(target: File): Any {
    return synchronized(WriteLocks) {
        WriteLocks.getOrPut(target.absolutePath) { Any() }
    }
}
