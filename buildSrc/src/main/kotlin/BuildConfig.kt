// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

object ProjectConfig {
    const val JVM_VERSION = 26
    const val PROJECT_NAME = "AsteriskMETA"
    const val VERSION_NAME = "1.1.4-dev"
    const val PACKAGE_NAME = "org.asterisk.zcc.ameta"
    const val ASTERISKD_VERSION = "v1.0.0"
    const val BPF2SOCKS_VERSION = "v1.0.1"
    const val BPF_MATCHER_VERSION = "v1.0.0"
    const val SETUIDGID_VERSION = "v1.0.0"
    const val CMFA_WRAPPER_VERSION = "v2.11.32-asterisk"
    const val MIHOMO_CORE_VERSION = "v1.19.29"
    const val HEV_SOCKS5_TUNNEL_VERSION = "2.16.0"
    const val TARGET_SDK = 37
    const val MIN_SDK = 24
    val SUPPORTED_ANDROID_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
}

fun org.gradle.api.Project.getGitVersionCode(): Int {
    return providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
    }.standardOutput.asText.get().trim().toInt()
}

abstract class GenerateProjectInfoTask : DefaultTask() {
    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val versionCode: Property<Int>

    @get:Input
    abstract val mihomoCoreVersion: Property<String>

    @get:Input
    abstract val cmfaWrapperVersion: Property<String>

    @get:Input
    abstract val hevSocks5TunnelVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val packagePath = packageName.get().replace('.', '/')
        val file = outputDirectory.file("$packagePath/ProjectInfo.kt").get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package ${packageName.get()}

            object ProjectInfo {
                const val PROJECT_NAME = "${projectName.get()}"
                const val VERSION_NAME = "${versionName.get()}"
                const val VERSION_CODE = ${versionCode.get()}
                const val CMFA_WRAPPER_VERSION = "${cmfaWrapperVersion.get()}"
                const val MIHOMO_CORE_VERSION = "${mihomoCoreVersion.get()}"
                const val HEV_SOCKS5_TUNNEL_VERSION = "${hevSocks5TunnelVersion.get()}"
            }
            """.trimIndent(),
        )
    }
}
