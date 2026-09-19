// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

abstract class UpdateResourceFileAssetsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val resourceFileAssetsDir: DirectoryProperty

    init {
        group = "resources"
        description = "Download bundled native runtime assets."
    }

    @TaskAction
    fun updateAssets() {
        AndroidMihomoResourceFileAssets.forEach { asset ->
            downloadFile(
                url = asset.url,
                target = File(resourceFileAssetsDir.get().asFile, "clash/${asset.fileName}"),
            )
        }
    }

    private fun useExistingFile(target: File): Boolean {
        if (!target.isFile) return false
        logger.lifecycle("Using existing ${target.absolutePath} (${target.length()} bytes)")
        return true
    }

    private fun downloadFile(url: String, target: File) {
        if (useExistingFile(target)) return
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

private data class MihomoResourceFileAsset(
    val fileName: String,
    val url: String,
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
    MihomoResourceFileAsset(
        fileName = "direct-cidr-v4.txt",
        url = "https://raw.githubusercontent.com/mayaxcn/china-ip-list/master/chnroute.txt",
    ),
    MihomoResourceFileAsset(
        fileName = "direct-cidr-v6.txt",
        url = "https://raw.githubusercontent.com/mayaxcn/china-ip-list/master/chnroute_v6.txt",
    ),
)
