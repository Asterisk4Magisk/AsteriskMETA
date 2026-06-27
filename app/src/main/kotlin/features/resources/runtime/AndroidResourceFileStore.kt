// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import app.CustomResourceFileState
import app.CustomResourceFileStatus
import app.ResourceFileKind
import app.ResourceFileStatus
import app.ResourceFilesStatus
import app.sanitizeCustomResourceFileName
import utils.writeAtomically
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

internal class AndroidResourceFileStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    val dataDir: File = appContext.mihomoResourceFilesDir()

    fun status(customResourceFiles: List<CustomResourceFileState> = emptyList()): ResourceFilesStatus {
        return currentStatus(customResourceFiles)
    }

    fun currentStatus(customResourceFiles: List<CustomResourceFileState> = emptyList()): ResourceFilesStatus {
        return ResourceFilesStatus(
            resourceFiles = ResourceFileKind.entries.associateWith { kind -> file(kind).toStatus() },
            customResourceFiles = customResourceFiles.map { customFile ->
                CustomResourceFileStatus(
                    file = customFile,
                    status = file(customFile).toStatus(),
                )
            },
        )
    }

    fun file(kind: ResourceFileKind): File {
        return File(dataDir, kind.fileName)
    }

    fun file(customFile: CustomResourceFileState): File {
        return File(
            dataDir,
            sanitizeCustomResourceFileName(
                value = customFile.name,
                fallback = "custom-resource-${customFile.id}.dat",
            ),
        )
    }

    fun restoreBundledDefaults() {
        val bundledUpdatedAtMillis = appContext.packageUpdatedAtMillis()
        ResourceFileKind.entries.forEach { kind ->
            val target = file(kind)
            if (!target.needsBundledRestore(bundledUpdatedAtMillis)) return@forEach
            if (!hasBundledFile(kind)) return@forEach
            runCatching { restoreBundled(kind) }
                .onFailure { error ->
                    AndroidResourceFileLogger.warn(
                        "Failed to restore bundled resource file: ${kind.fileName}",
                        error,
                    )
                }
        }
    }

    private fun hasBundledFile(kind: ResourceFileKind): Boolean {
        return when (kind) {
            ResourceFileKind.MihomoCore -> bundledMihomoCoreFileOrNull() != null
            else -> runCatching {
                appContext.assets.open(kind.bundledAssetPath()).use { input -> input.read() >= 0 }
            }.getOrDefault(false)
        }
    }

    private fun ResourceFileKind.bundledAssetPath(): String {
        return "clash/$fileName"
    }

    fun restoreBundled(kind: ResourceFileKind) {
        when (kind) {
            ResourceFileKind.MihomoCore -> restoreBundledMihomoCore()
            else -> restoreBundledResourceFile(kind)
        }
    }

    private fun restoreBundledResourceFile(kind: ResourceFileKind) {
        dataDir.mkdirs()
        appContext.assets.open(kind.bundledAssetPath()).use { input ->
            writeAtomically(file(kind)) { output -> input.copyTo(output) }
        }
        kind.applyPermissions(file(kind))
    }

    private fun restoreBundledMihomoCore() {
        val source = bundledMihomoCoreFileOrNull()
            ?: error("Bundled ${ResourceFileKind.MihomoCore.fileName} is not available for ${currentRuntimeAbi()}")
        dataDir.mkdirs()
        source.inputStream().use { input ->
            writeAtomically(file(ResourceFileKind.MihomoCore)) { output -> input.copyTo(output) }
        }
        ResourceFileKind.MihomoCore.applyPermissions(file(ResourceFileKind.MihomoCore))
    }

    private fun bundledMihomoCoreFileOrNull(): File? {
        return File(appContext.applicationInfo.nativeLibraryDir, MihomoCoreLibraryName)
            .takeIf { it.isFile && it.length() > 0 }
    }

    fun replace(kind: ResourceFileKind, uri: Uri) {
        dataDir.mkdirs()
        val replaceTempFile = file(kind).resolveSibling("${kind.fileName}.replace.tmp")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            replaceTempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw FileNotFoundException(uri.toString())

        when {
            kind == ResourceFileKind.MihomoCore && replaceTempFile.extractZipEntry("mihomo", file(kind)) -> {
                replaceTempFile.delete()
            }

            kind == ResourceFileKind.MihomoCore && replaceTempFile.extractGzip(file(kind)) -> {
                replaceTempFile.delete()
            }

            else -> replaceFile(replaceTempFile, file(kind))
        }
        kind.applyPermissions(file(kind))
    }

    fun replaceCustom(customFile: CustomResourceFileState, uri: Uri) {
        val target = file(customFile)
        if (ResourceFileKind.entries.any { kind -> kind.fileName == target.name }) return
        dataDir.mkdirs()
        val replaceTempFile = target.resolveSibling("${target.name}.replace.tmp")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            replaceTempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw FileNotFoundException(uri.toString())

        replaceFile(replaceTempFile, target)
    }

    fun applyPermissions(kind: ResourceFileKind) {
        kind.applyPermissions(file(kind))
    }

    fun deleteCustom(customFile: CustomResourceFileState) {
        val target = file(customFile)
        if (ResourceFileKind.entries.any { kind -> kind.fileName == target.name }) return
        target.delete()
    }

    fun renameCustom(previousFile: CustomResourceFileState, customFile: CustomResourceFileState) {
        val source = file(previousFile)
        val target = file(customFile)
        if (ResourceFileKind.entries.any { kind -> kind.fileName == source.name || kind.fileName == target.name }) return
        if (source.absolutePath == target.absolutePath) return
        if (!source.isFile) return

        dataDir.mkdirs()
        if (target.exists()) {
            target.delete()
        }
        if (!source.renameTo(target)) {
            source.inputStream().use { input ->
                writeAtomically(target) { output -> input.copyTo(output) }
            }
            source.delete()
        }
    }

    fun preparePaths(): MihomoResourceFilePaths {
        dataDir.mkdirs()
        return MihomoResourceFilePaths(
            dataDir = dataDir.absolutePath,
            setuidgidPath = File(appContext.applicationInfo.nativeLibraryDir, SetuidgidLibraryName).absolutePath,
            ipv6DisablerPath = File(appContext.applicationInfo.nativeLibraryDir, Ipv6DisablerLibraryName).absolutePath,
            bpfMatcherPath = File(appContext.applicationInfo.nativeLibraryDir, BpfMatcherLibraryName).absolutePath,
            mihomoCorePath = file(ResourceFileKind.MihomoCore).absolutePath,
            directCidrIpv4Path = file(ResourceFileKind.DirectCidrIpv4).absolutePath,
            directCidrIpv6Path = file(ResourceFileKind.DirectCidrIpv6).absolutePath,
            hevSocks5TunnelPath = File(appContext.applicationInfo.nativeLibraryDir, HevSocks5TunnelLibraryName).absolutePath,
        )
    }
}

private fun File.needsBundledRestore(bundledUpdatedAtMillis: Long): Boolean {
    if (!exists() || length() <= 0) return true
    return bundledUpdatedAtMillis > 0 && lastModified() < bundledUpdatedAtMillis
}

internal data class MihomoResourceFilePaths(
    val dataDir: String,
    val setuidgidPath: String,
    val ipv6DisablerPath: String,
    val bpfMatcherPath: String,
    val mihomoCorePath: String,
    val directCidrIpv4Path: String,
    val directCidrIpv6Path: String,
    val hevSocks5TunnelPath: String,
)

internal fun Context.mihomoResourceFilesDir(): File {
    return File(filesDir, MihomoHomeDirName)
}

internal fun Context.prepareMihomoResourceFilePaths(): MihomoResourceFilePaths {
    return AndroidResourceFileStore(this).preparePaths()
}

private fun currentRuntimeAbi(): String {
    return Build.SUPPORTED_ABIS.firstOrNull { abi -> abi in SupportedAndroidAbis }
        ?: error("Unsupported CPU ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
}

private fun Context.packageUpdatedAtMillis(): Long {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager
                .getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                .lastUpdateTime
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        }
    }.getOrDefault(0L)
}

private const val SetuidgidLibraryName = "libsetuidgid.so"
private const val Ipv6DisablerLibraryName = "libipv6disabler.so"
private const val BpfMatcherLibraryName = "libbpf-matcher.so"
private const val MihomoCoreLibraryName = "libmihomo.so"
private const val HevSocks5TunnelLibraryName = "libhev-socks5-tunnel-cli.so"
private const val MihomoHomeDirName = "clash"

private val SupportedAndroidAbis = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

private fun File.toStatus(): ResourceFileStatus {
    return ResourceFileStatus(
        exists = exists() && length() > 0,
        sizeBytes = takeIf { exists() }?.length() ?: 0,
        updatedAtMillis = takeIf { exists() }?.lastModified() ?: 0,
    )
}

private fun File.extractZipEntry(entryName: String, target: File): Boolean {
    return runCatching {
        ZipInputStream(inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.substringAfterLast('/') == entryName) {
                    writeAtomically(target) { output -> zip.copyTo(output) }
                    return@runCatching true
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            false
        }
    }.onFailure { error ->
        AndroidResourceFileLogger.warn("Failed to extract $entryName from $absolutePath", error)
    }.getOrDefault(false)
}

private fun File.extractGzip(target: File): Boolean {
    return runCatching {
        GZIPInputStream(inputStream()).use { input ->
            writeAtomically(target) { output -> input.copyTo(output) }
        }
        true
    }.onFailure { error ->
        AndroidResourceFileLogger.warn("Failed to extract gzip ${absolutePath}", error)
    }.getOrDefault(false)
}

private fun replaceFile(source: File, target: File) {
    if (source.length() <= 0) {
        source.delete()
        error("${target.name} is empty")
    }
    if (target.exists()) {
        target.delete()
    }
    if (!source.renameTo(target)) {
        source.inputStream().use { input ->
            writeAtomically(target) { output -> input.copyTo(output) }
        }
        source.delete()
    }
}

private fun ResourceFileKind.applyPermissions(file: File) {
    if (this == ResourceFileKind.MihomoCore) {
        file.setExecutable(true, false)
    }
}
