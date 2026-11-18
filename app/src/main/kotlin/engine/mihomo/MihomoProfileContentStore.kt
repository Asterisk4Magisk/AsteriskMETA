// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import android.content.Context
import app.MihomoProfileState
import utils.writeAtomically
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.Reader
import java.security.MessageDigest
import java.util.UUID

internal const val MaxMihomoProfileBytes = 256 * 1024 * 1024

internal data class MihomoProfileContentRef(
    val path: String,
    val sha256: String,
    val sizeBytes: Long,
)

internal class MihomoProfileContentStore internal constructor(
    private val profilesDir: File,
) {
    constructor(context: Context) : this(
        File(context.applicationContext.filesDir, ProfilesDirName),
    )

    fun read(profile: MihomoProfileState): String {
        return readBytes(profile).toString(Charsets.UTF_8)
    }

    fun readBytes(profile: MihomoProfileState): ByteArray {
        if (!profile.hasContent) {
            error(MihomoProfileEmptyErrorMessage)
        }
        val source = File(profile.contentPath)
        requireMihomoProfileSize(source.length())
        val bytes = source.readBytes()
        if (bytes.isEmpty() || bytes.toString(Charsets.UTF_8).isBlank()) {
            error(MihomoProfileEmptyErrorMessage)
        }
        return bytes
    }

    fun <T> useReader(
        profile: MihomoProfileState,
        block: (Reader) -> T,
    ): T {
        if (!profile.hasContent) {
            error(MihomoProfileEmptyErrorMessage)
        }
        val source = File(profile.contentPath)
        if (!source.isFile || source.length() <= 0L) {
            error(MihomoProfileEmptyErrorMessage)
        }
        requireMihomoProfileSize(source.length())
        return source.bufferedReader(Charsets.UTF_8).use(block)
    }

    fun readOrEmpty(profile: MihomoProfileState): String {
        return runCatching { read(profile) }.getOrDefault("")
    }

    fun writeNew(content: String): MihomoProfileContentRef {
        return writeTo(newProfileFile(), content)
    }

    fun writePendingSubscription(profileId: Int, content: String): MihomoProfileContentRef {
        return writeTo(newSubscriptionFile(profileId), content)
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
        deletePath(path)
    }

    fun delete(contentRef: MihomoProfileContentRef) {
        deletePath(contentRef.path)
    }

    fun pruneUnreferenced(referencedPaths: Set<String>) {
        val retainedPaths = referencedPaths.mapTo(mutableSetOf()) { path -> File(path).absolutePath }
        profilesDir.listFiles()
            ?.asSequence()
            ?.filter { file ->
                file.isFile &&
                    (
                        file.name.startsWith(ProfileFilePrefix) ||
                            file.name.startsWith(SubscriptionFilePrefix)
                    ) &&
                    file.extension == ProfileFileExtension &&
                    file.absolutePath !in retainedPaths
            }
            ?.forEach { file -> runCatching { file.delete() } }
    }

    fun pruneSubscriptionHistory(
        profileId: Int,
        referencedPaths: Set<String>,
    ) {
        val retainedPaths = referencedPaths.mapTo(mutableSetOf()) { path -> File(path).absolutePath }
        val prefix = subscriptionFilePrefix(profileId)
        profilesDir.listFiles()
            ?.asSequence()
            ?.filter { file ->
                file.isFile &&
                    file.name.startsWith(prefix) &&
                    file.extension == ProfileFileExtension &&
                    file.absolutePath !in retainedPaths
            }
            ?.sortedWith(compareByDescending<File> { file -> file.lastModified() }.thenByDescending(File::getName))
            ?.drop(RetainedSubscriptionHistoryCount)
            ?.forEach { file -> runCatching { file.delete() } }
    }

    private fun deletePath(path: String) {
        runCatching { File(path).delete() }
    }

    private fun writeTo(target: File, content: String): MihomoProfileContentRef {
        requireMihomoProfileSize(content.length.toLong())
        if (content.isBlank()) {
            error(MihomoProfileEmptyErrorMessage)
        }
        val bytes = content.toByteArray(Charsets.UTF_8)
        requireMihomoProfileSize(bytes.size.toLong())
        writeAtomically(target) { output -> output.write(bytes) }
        return MihomoProfileContentRef(
            path = target.absolutePath,
            sha256 = bytes.sha256Hex(),
            sizeBytes = bytes.size.toLong(),
        )
    }

    private fun newProfileFile(): File {
        profilesDir.mkdirs()
        return File(
            profilesDir,
            "$ProfileFilePrefix${System.currentTimeMillis()}-${UUID.randomUUID()}.$ProfileFileExtension",
        )
    }

    private fun newSubscriptionFile(profileId: Int): File {
        profilesDir.mkdirs()
        return File(
            profilesDir,
            "${subscriptionFilePrefix(profileId)}${System.currentTimeMillis()}-${UUID.randomUUID()}.$ProfileFileExtension",
        )
    }
}

internal fun Context.mihomoProfileContentStore(): MihomoProfileContentStore {
    return MihomoProfileContentStore(this)
}

internal fun requireMihomoProfileSize(sizeBytes: Long) {
    require(sizeBytes <= MaxMihomoProfileBytes) { "Mihomo configuration exceeds 256 MiB" }
}

internal fun InputStream.readMihomoProfileText(): String {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = ByteArrayOutputStream(buffer.size)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        requireMihomoProfileSize(output.size().toLong() + count)
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}

internal fun String.sha256Hex(): String {
    return toByteArray(Charsets.UTF_8).sha256Hex()
}

internal fun ByteArray.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private const val ProfilesDirName = "mihomo-profiles"
private const val ProfileFilePrefix = "profile-"
private const val SubscriptionFilePrefix = "subscription-profile-"
private const val ProfileFileExtension = "yaml"
private const val RetainedSubscriptionHistoryCount = 2

private fun subscriptionFilePrefix(profileId: Int): String = "$SubscriptionFilePrefix$profileId-"
