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
import features.resources.ResourceFileSourceMetaCubeXGithub
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
            resourceFiles = ResourceFileKind.entries.associateWith { kind -> file(kind).toStatus(kind) },
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

    fun restoreBundledDefaults(resourceFileSource: Int = ResourceFileSourceMetaCubeXGithub) {
        val bundledUpdatedAtMillis = appContext.packageUpdatedAtMillis()
        ResourceFileKind.entries.forEach { kind ->
            if (kind == ResourceFileKind.MihomoCore) return@forEach
            val target = file(kind)
            if (!target.needsBundledRestore(kind, resourceFileSource, bundledUpdatedAtMillis)) return@forEach
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
        require(kind != ResourceFileKind.MihomoCore) { "Mihomo core must be restored through the locked publisher" }
        restoreBundledResourceFile(kind)
    }

    private fun restoreBundledResourceFile(kind: ResourceFileKind) {
        dataDir.mkdirs()
        appContext.assets.open(kind.bundledAssetPath()).use { input ->
            writeAtomically(file(kind)) { output -> input.copyTo(output) }
        }
        kind.applyPermissions(file(kind))
    }

    fun stageBundledMihomoCoreCandidate(): File {
        val source = bundledMihomoCoreFileOrNull()
            ?: error("Bundled ${ResourceFileKind.MihomoCore.fileName} is not available for ${currentRuntimeAbi()}")
        return source.inputStream().use(::writeMihomoCoreCandidate)
    }

    fun shouldPublishBundledMihomoCore(resourceFileSource: Int): Boolean {
        return bundledMihomoCoreFileOrNull() != null && file(ResourceFileKind.MihomoCore).needsBundledRestore(
            ResourceFileKind.MihomoCore,
            resourceFileSource,
            appContext.packageUpdatedAtMillis(),
        )
    }

    private fun bundledMihomoCoreFileOrNull(): File? {
        return File(appContext.applicationInfo.nativeLibraryDir, MihomoCoreLibraryName)
            .takeIf { it.isFile }
    }

    fun replace(kind: ResourceFileKind, uri: Uri) {
        require(kind != ResourceFileKind.MihomoCore) { "Mihomo core must be replaced through the locked publisher" }
        dataDir.mkdirs()
        val replaceTempFile = file(kind).resolveSibling("${kind.fileName}.replace.tmp")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            replaceTempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw FileNotFoundException(uri.toString())

        replaceFile(replaceTempFile, file(kind))
        kind.applyPermissions(file(kind))
    }

    fun stageMihomoCoreCandidate(uri: Uri): File {
        val uploaded = appContext.contentResolver.openInputStream(uri)?.use(::writeMihomoCoreCandidate)
            ?: throw FileNotFoundException(uri.toString())
        return normalizeMihomoCoreCandidate(uploaded)
    }

    fun createMihomoCoreDownloadCandidate(): File = createMihomoCoreCandidateFile("mihomo-download-")

    fun normalizeMihomoCoreCandidate(uploaded: File): File {
        val extracted = createMihomoCoreCandidateFile("mihomo-extracted-")
        try {
            val found = uploaded.extractZipEntry("mihomo", extracted) || uploaded.extractGzip(extracted)
            return if (found) {
                uploaded.delete()
                extracted
            } else {
                extracted.delete()
                uploaded
            }
        } catch (error: Throwable) {
            extracted.delete()
            uploaded.delete()
            throw error
        }
    }

    fun installInitialMihomoCoreCandidate(candidate: File): Boolean {
        return publishCoreBinaryCandidate(candidate, file(ResourceFileKind.MihomoCore), replaceExisting = false)
    }

    fun replaceMihomoCoreCandidate(candidate: File) {
        publishCoreBinaryCandidate(candidate, file(ResourceFileKind.MihomoCore), replaceExisting = true)
    }

    private fun writeMihomoCoreCandidate(input: java.io.InputStream): File {
        val candidate = createMihomoCoreCandidateFile("mihomo-core-")
        try {
            candidate.outputStream().use { output ->
                input.copyTo(output)
                output.flush()
                output.fd.sync()
            }
            return candidate
        } catch (error: Throwable) {
            candidate.delete()
            throw error
        }
    }

    private fun createMihomoCoreCandidateFile(prefix: String): File {
        require(appContext.cacheDir.exists() || appContext.cacheDir.mkdirs())
        return File.createTempFile(prefix, ".candidate", appContext.cacheDir)
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
        return currentPaths()
    }

    fun currentPaths(): MihomoResourceFilePaths {
        return MihomoResourceFilePaths(
            dataDir = dataDir.absolutePath,
            asteriskdPath = File(appContext.applicationInfo.nativeLibraryDir, AsteriskdLibraryName).absolutePath,
            bpfMatcherPath = File(appContext.applicationInfo.nativeLibraryDir, BpfMatcherLibraryName).absolutePath,
            bpf2socksPath = File(appContext.applicationInfo.nativeLibraryDir, Bpf2SocksLibraryName).absolutePath,
            mihomoCorePath = file(ResourceFileKind.MihomoCore).absolutePath,
            directCidrIpv4Path = file(ResourceFileKind.DirectCidrIpv4).absolutePath,
            directCidrIpv6Path = file(ResourceFileKind.DirectCidrIpv6).absolutePath,
            hevSocks5TunnelPath = File(appContext.applicationInfo.nativeLibraryDir, HevSocks5TunnelLibraryName).absolutePath,
        )
    }
}

private fun File.needsBundledRestore(
    kind: ResourceFileKind,
    resourceFileSource: Int,
    bundledUpdatedAtMillis: Long,
): Boolean {
    return shouldRestoreBundledResourceFile(
        kind = kind,
        resourceFileSource = resourceFileSource,
        targetExists = exists(),
        targetLength = takeIf { exists() }?.length() ?: 0L,
        targetLastModifiedMillis = takeIf { exists() }?.lastModified() ?: 0L,
        bundledUpdatedAtMillis = bundledUpdatedAtMillis,
    )
}

internal fun shouldRestoreBundledResourceFile(
    kind: ResourceFileKind,
    resourceFileSource: Int,
    targetExists: Boolean,
    targetLength: Long,
    targetLastModifiedMillis: Long,
    bundledUpdatedAtMillis: Long,
): Boolean {
    if (!targetExists || (kind != ResourceFileKind.MihomoCore && targetLength <= 0)) return true
    if (kind != ResourceFileKind.MihomoCore && resourceFileSource != ResourceFileSourceMetaCubeXGithub) {
        return false
    }
    return bundledUpdatedAtMillis > 0 && targetLastModifiedMillis < bundledUpdatedAtMillis
}

internal data class MihomoResourceFilePaths(
    val dataDir: String,
    val asteriskdPath: String,
    val bpfMatcherPath: String,
    val bpf2socksPath: String,
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

internal fun Context.mihomoResourceFilePaths(): MihomoResourceFilePaths {
    return AndroidResourceFileStore(this).currentPaths()
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

private const val AsteriskdLibraryName = "libasteriskd.so"
private const val BpfMatcherLibraryName = "libbpf-matcher.so"
private const val Bpf2SocksLibraryName = "libbpf2socks.so"
private const val MihomoCoreLibraryName = "libmihomo.so"
private const val HevSocks5TunnelLibraryName = "libhev-socks5-tunnel-cli.so"
private const val MihomoHomeDirName = "clash"

private val SupportedAndroidAbis = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

internal enum class CoreCandidateInstallPath {
    ReplaceAppOwned,
    ReplaceWithRoot,
    InitialNoReplace,
}

internal fun chooseCoreCandidateInstallPath(
    rootModeActive: Boolean,
    targetExists: Boolean,
): CoreCandidateInstallPath = when {
    !targetExists -> CoreCandidateInstallPath.InitialNoReplace
    rootModeActive -> CoreCandidateInstallPath.ReplaceWithRoot
    else -> CoreCandidateInstallPath.ReplaceAppOwned
}

internal inline fun resolveCoreCandidateInstallPath(
    targetExists: Boolean,
    rootModeActive: () -> Boolean,
): CoreCandidateInstallPath {
    if (!targetExists) return CoreCandidateInstallPath.InitialNoReplace
    return chooseCoreCandidateInstallPath(rootModeActive(), targetExists = true)
}

internal fun resourceFileExists(
    kind: ResourceFileKind?,
    targetExists: Boolean,
    targetLength: Long,
): Boolean {
    return targetExists && (kind == ResourceFileKind.MihomoCore || targetLength > 0)
}

private fun File.toStatus(kind: ResourceFileKind? = null): ResourceFileStatus {
    val targetExists = exists()
    val targetLength = takeIf { targetExists }?.length() ?: 0L
    return ResourceFileStatus(
        exists = resourceFileExists(kind, targetExists, targetLength),
        sizeBytes = targetLength,
        updatedAtMillis = takeIf { targetExists }?.lastModified() ?: 0,
    )
}

private fun File.extractZipEntry(entryName: String, target: File): Boolean {
    return runCatching {
        ZipInputStream(inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.substringAfterLast('/') == entryName) {
                    target.outputStream().use { output ->
                        zip.copyTo(output)
                        output.flush()
                        output.fd.sync()
                    }
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
            target.outputStream().use { output ->
                input.copyTo(output)
                output.flush()
                output.fd.sync()
            }
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
