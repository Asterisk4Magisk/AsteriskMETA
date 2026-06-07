// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.GZIPInputStream

abstract class UpdateResourceFileAssetsTask : DefaultTask() {
    @get:Input
    abstract val mihomoCoreVersion: Property<String>

    @get:Input
    abstract val hevSocks5TunnelVersion: Property<String>

    @get:OutputDirectory
    abstract val mihomoCoreJniLibsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val hevSocks5TunnelJniLibsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val resourceFileAssetsDir: DirectoryProperty

    init {
        group = "resources"
        description = "Download bundled native runtime assets."
    }

    @TaskAction
    fun updateAssets() {
        AndroidMihomoAssets.forEach { asset ->
            downloadGzipFile(
                url = mihomoCoreArchiveUrl(asset.releaseName),
                target = File(mihomoCoreJniLibsDir.get().asFile, "${asset.androidAbi}/libmihomo.so"),
            )
        }
        AndroidHevSocks5TunnelAssets.forEach { asset ->
            downloadFile(
                url = hevSocks5TunnelArchiveUrl(asset.releaseName),
                target = File(hevSocks5TunnelJniLibsDir.get().asFile, "${asset.androidAbi}/libhev-socks5-tunnel.so"),
            )
        }
        AndroidMihomoResourceFileAssets.forEach { asset ->
            downloadFile(
                url = asset.url,
                target = File(resourceFileAssetsDir.get().asFile, "clash/${asset.fileName}"),
            )
        }
    }

    private fun mihomoCoreArchiveUrl(releaseName: String): String {
        val version = mihomoCoreVersion.get()
        return "https://github.com/MetaCubeX/mihomo/releases/download/$version/$releaseName"
    }

    private fun hevSocks5TunnelArchiveUrl(releaseName: String): String {
        val version = hevSocks5TunnelVersion.get()
        return "https://github.com/heiher/hev-socks5-tunnel/releases/download/$version/$releaseName"
    }

    private fun downloadGzipFile(url: String, target: File) {
        target.parentFile.mkdirs()
        val tempFile = target.resolveSibling("${target.name}.tmp")
        logger.lifecycle("Downloading $url")
        val connection = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw GradleException("Failed to download $url: HTTP $code")
            }
            GZIPInputStream(connection.inputStream).use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        if (tempFile.length() <= 0) {
            tempFile.delete()
            throw GradleException("Downloaded file is empty: $url")
        }
        if (target.exists()) {
            target.delete()
        }
        if (!tempFile.renameTo(target)) {
            throw GradleException("Unable to move ${tempFile.absolutePath} to ${target.absolutePath}")
        }
        logger.lifecycle("Updated ${target.absolutePath} (${target.length()} bytes)")
    }

    private fun downloadFile(url: String, target: File) {
        target.parentFile.mkdirs()
        val tempFile = target.resolveSibling("${target.name}.tmp")
        logger.lifecycle("Downloading $url")
        val connection = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw GradleException("Failed to download $url: HTTP $code")
            }
            connection.inputStream.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        if (tempFile.length() <= 0) {
            tempFile.delete()
            throw GradleException("Downloaded file is empty: $url")
        }
        if (target.exists()) {
            target.delete()
        }
        if (!tempFile.renameTo(target)) {
            throw GradleException("Unable to move ${tempFile.absolutePath} to ${target.absolutePath}")
        }
        logger.lifecycle("Updated ${target.absolutePath} (${target.length()} bytes)")
    }
}

private data class HevSocks5TunnelAsset(
    val androidAbi: String,
    val releaseName: String,
)

private data class MihomoAsset(
    val androidAbi: String,
    val releaseName: String,
)

private data class MihomoResourceFileAsset(
    val fileName: String,
    val url: String,
)

private val AndroidMihomoAssets = listOf(
    MihomoAsset("arm64-v8a", "mihomo-android-arm64-v8-${ProjectConfig.MIHOMO_CORE_VERSION}.gz"),
    MihomoAsset("armeabi-v7a", "mihomo-android-armv7-${ProjectConfig.MIHOMO_CORE_VERSION}.gz"),
    MihomoAsset("x86", "mihomo-android-386-${ProjectConfig.MIHOMO_CORE_VERSION}.gz"),
    MihomoAsset("x86_64", "mihomo-android-amd64-${ProjectConfig.MIHOMO_CORE_VERSION}.gz"),
)

private val AndroidHevSocks5TunnelAssets = listOf(
    HevSocks5TunnelAsset("arm64-v8a", "hev-socks5-tunnel-linux-arm64"),
    HevSocks5TunnelAsset("armeabi-v7a", "hev-socks5-tunnel-linux-arm32v7"),
    HevSocks5TunnelAsset("x86", "hev-socks5-tunnel-linux-i686"),
    HevSocks5TunnelAsset("x86_64", "hev-socks5-tunnel-linux-x86_64"),
)

private val AndroidMihomoResourceFileAssets = listOf(
    MihomoResourceFileAsset(
        fileName = "GeoIP.dat",
        url = "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.dat",
    ),
    MihomoResourceFileAsset(
        fileName = "GeoSite.dat",
        url = "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat",
    ),
    MihomoResourceFileAsset(
        fileName = "geoip.metadb",
        url = "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb",
    ),
    MihomoResourceFileAsset(
        fileName = "ASN.mmdb",
        url = "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/GeoLite2-ASN.mmdb",
    ),
)
