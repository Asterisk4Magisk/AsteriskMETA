// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package data.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.R
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal const val MaxAppBackupFileBytes = 32 * 1024 * 1024

internal class AndroidAppBackupDocumentGateway(
    context: Context,
    private val filePicker: suspend () -> Uri?,
    private val fileCreator: suspend (String) -> Uri?,
) : AppBackupDocumentGateway {
    private val appContext = context.applicationContext

    override suspend fun readText(): String? {
        val uri = filePicker() ?: return null
        return withContext(Dispatchers.IO) {
            val tooLargeMessage = appContext.getString(R.string.error_backup_file_too_large)
            val declaredSize = appContext.contentResolver.querySize(uri)
            require(declaredSize == null || declaredSize <= MaxAppBackupFileBytes) {
                tooLargeMessage
            }
            val input = appContext.contentResolver.openInputStream(uri)
                ?: error(appContext.getString(R.string.error_backup_file_open_failed))
            input.use { stream ->
                stream.readUtf8TextWithLimit(
                    maxBytes = MaxAppBackupFileBytes,
                    tooLargeMessage = tooLargeMessage,
                )
            }
        }
    }

    override suspend fun writeText(
        defaultFileName: String,
        content: String,
    ): Boolean {
        require(content.hasUtf8SizeAtMost(MaxAppBackupFileBytes)) {
            appContext.getString(R.string.error_backup_file_too_large)
        }
        val uri = fileCreator(defaultFileName) ?: return false
        withContext(Dispatchers.IO) {
            val output = appContext.contentResolver.openOutputStream(uri)
                ?: error(appContext.getString(R.string.error_backup_file_open_failed))
            output.writer(Charsets.UTF_8).use { writer -> writer.write(content) }
        }
        return true
    }
}

internal fun InputStream.readUtf8TextWithLimit(
    maxBytes: Int,
    tooLargeMessage: String,
): String {
    require(maxBytes > 0)
    val output = ByteArrayOutputStream(minOf(maxBytes, DefaultBackupReadBufferSize))
    val buffer = ByteArray(DefaultBackupReadBufferSize)
    var totalBytes = 0
    while (true) {
        val readCount = read(buffer)
        if (readCount < 0) break
        require(totalBytes <= maxBytes - readCount) { tooLargeMessage }
        output.write(buffer, 0, readCount)
        totalBytes += readCount
    }
    return output.toString(Charsets.UTF_8.name())
}

private fun android.content.ContentResolver.querySize(uri: Uri): Long? =
    query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (sizeColumn < 0 || !cursor.moveToFirst() || cursor.isNull(sizeColumn)) {
            null
        } else {
            cursor.getLong(sizeColumn).takeIf { size -> size >= 0L }
        }
    }

private const val DefaultBackupReadBufferSize = 8 * 1024
