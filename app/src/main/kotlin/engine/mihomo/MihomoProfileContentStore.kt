// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import android.content.Context
import app.MihomoProfileState
import utils.writeAtomically
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal data class MihomoProfileContentRef(
    val path: String,
    val sha256: String,
    val sizeBytes: Long,
)

internal class MihomoProfileContentStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val profilesDir = File(appContext.filesDir, ProfilesDirName)

    fun read(profile: MihomoProfileState): String {
        return readBytes(profile).toString(Charsets.UTF_8)
    }

    fun readBytes(profile: MihomoProfileState): ByteArray {
        if (!profile.hasContent) {
            error(MihomoProfileEmptyErrorMessage)
        }
        val bytes = File(profile.contentPath).readBytes()
        if (bytes.isEmpty() || bytes.toString(Charsets.UTF_8).isBlank()) {
            error(MihomoProfileEmptyErrorMessage)
        }
        return bytes
    }

    fun readOrEmpty(profile: MihomoProfileState): String {
        return runCatching { read(profile) }.getOrDefault("")
    }

    fun writeNew(content: String): MihomoProfileContentRef {
        return writeTo(newProfileFile(), content)
    }

    fun write(profile: MihomoProfileState, content: String): MihomoProfileContentRef {
        val target = profile.contentPath
            .takeIf(String::isNotBlank)
            ?.let(::File)
            ?: newProfileFile()
        return writeTo(target, content)
    }

    fun delete(profile: MihomoProfileState) {
        val path = profile.contentPath.takeIf(String::isNotBlank) ?: return
        runCatching { File(path).delete() }
    }

    private fun writeTo(target: File, content: String): MihomoProfileContentRef {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || content.isBlank()) {
            error(MihomoProfileEmptyErrorMessage)
        }
        return writeBytesTo(target, bytes)
    }

    private fun writeBytesTo(target: File, bytes: ByteArray): MihomoProfileContentRef {
        if (bytes.isEmpty() || bytes.toString(Charsets.UTF_8).isBlank()) {
            error(MihomoProfileEmptyErrorMessage)
        }
        writeAtomically(target) { output -> output.write(bytes) }
        return MihomoProfileContentRef(
            path = target.absolutePath,
            sha256 = bytes.sha256Hex(),
            sizeBytes = bytes.size.toLong(),
        )
    }

    private fun newProfileFile(): File {
        profilesDir.mkdirs()
        return File(profilesDir, "profile-${System.currentTimeMillis()}-${UUID.randomUUID()}.yaml")
    }
}

internal fun Context.mihomoProfileContentStore(): MihomoProfileContentStore {
    return MihomoProfileContentStore(this)
}

internal fun String.sha256Hex(): String {
    return toByteArray(Charsets.UTF_8).sha256Hex()
}

internal fun ByteArray.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private const val ProfilesDirName = "mihomo-profiles"
