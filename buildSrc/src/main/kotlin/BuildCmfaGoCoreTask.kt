// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.util.Properties
import javax.inject.Inject

abstract class BuildCmfaGoCoreTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val goModuleDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val goSourceDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mihomoSubmoduleDirectory: DirectoryProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val localPropertiesFile: RegularFileProperty

    @get:Input
    abstract val minSdk: Property<Int>

    @get:Input
    abstract val androidAbi: Property<String>

    @get:Input
    abstract val debugBuild: Property<Boolean>

    @get:Input
    abstract val tags: ListProperty<String>

    @get:Input
    abstract val mihomoCoreVersion: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "build"
        description = "Build the vendored CMFA Go core for Android."
    }

    @TaskAction
    fun build() {
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()

        val command = buildList {
            add("go")
            add("build")
            add("-buildmode")
            add("c-shared")
            add("-trimpath")
            add("-o")
            add(output.absolutePath)
            add("-tags")
            add(tags.get().joinToString(","))
            if (!debugBuild.get()) {
                add("-ldflags")
                add("-s -w")
            }
            add("cfa/native")
        }

        execOperations.exec {
            workingDir = goModuleDirectory.get().asFile
            environment(androidGoEnvironment(androidAbi.get()))
            commandLine(command)
        }

        if (!output.exists() || output.length() <= 0) {
            throw GradleException("Failed to build CMFA Go core: ${output.absolutePath}")
        }
    }

    private fun androidGoEnvironment(abi: String): Map<String, String> {
        val target = abi.toCmfaGoAbiTarget()
        val clang = findNdkClang(findNdkDir(), target)
        return buildMap {
            put("CC", clang.absolutePath)
            put("GOOS", "android")
            put("GOARCH", target.goArch)
            if (target.goArm.isNotEmpty()) {
                put("GOARM", target.goArm)
            }
            put("CGO_ENABLED", "1")
            put("CFLAGS", "-O3 -Werror")
        }
    }

    private fun findNdkClang(ndkDir: File, target: CmfaGoAbiTarget): File {
        val executableName = if (hostPrebuiltName().startsWith("windows")) {
            "${target.clangTarget}${minSdk.get()}-clang.cmd"
        } else {
            "${target.clangTarget}${minSdk.get()}-clang"
        }
        val clang = ndkDir.resolve("toolchains/llvm/prebuilt/${hostPrebuiltName()}/bin/$executableName")
        if (!clang.exists()) {
            throw GradleException("Android NDK clang not found: ${clang.absolutePath}")
        }
        return clang
    }

    private fun findNdkDir(): File {
        listOf("ANDROID_NDK_HOME", "ANDROID_NDK_ROOT").forEach { name ->
            System.getenv(name)?.takeIf(String::isNotBlank)?.let { path ->
                return File(path).also { directory ->
                    if (!directory.isDirectory) {
                        throw GradleException("Android NDK directory from $name does not exist: ${directory.absolutePath}")
                    }
                }
            }
        }

        val localProperties = localPropertiesFile.orNull?.asFile
        if (localProperties != null && localProperties.exists()) {
            val properties = Properties()
            localProperties.inputStream().use(properties::load)
            properties.getProperty("ndk.dir")?.takeIf(String::isNotBlank)?.let { path ->
                return File(path).also { directory ->
                    if (!directory.isDirectory) {
                        throw GradleException("Android NDK directory from local.properties ndk.dir does not exist: ${directory.absolutePath}")
                    }
                }
            }
            properties.getProperty("sdk.dir")?.takeIf(String::isNotBlank)?.let { path ->
                File(path, "ndk").latestChildDirectory()?.let { return it }
            }
        }

        listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").forEach { name ->
            System.getenv(name)?.takeIf(String::isNotBlank)?.let { path ->
                File(path, "ndk").latestChildDirectory()?.let { return it }
            }
        }

        throw GradleException("Android NDK not found. Set ndk.dir, ANDROID_NDK_HOME, or install an NDK under the Android SDK.")
    }
}

private fun File.latestChildDirectory(): File? {
    return listFiles()
        ?.filter(File::isDirectory)
        ?.maxByOrNull { directory -> directory.name }
}

private enum class CmfaGoAbiTarget(
    val androidAbi: String,
    val clangTarget: String,
    val goArch: String,
    val goArm: String = "",
) {
    Arm64("arm64-v8a", "aarch64-linux-android", "arm64"),
    Arm32("armeabi-v7a", "armv7a-linux-androideabi", "arm", "7"),
    X86("x86", "i686-linux-android", "386"),
    X64("x86_64", "x86_64-linux-android", "amd64"),
}

private fun String.toCmfaGoAbiTarget(): CmfaGoAbiTarget {
    return CmfaGoAbiTarget.entries.firstOrNull { target -> target.androidAbi == this }
        ?: throw GradleException("Unsupported CMFA Go ABI: $this")
}

private fun hostPrebuiltName(): String {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.startsWith("windows") -> "windows-x86_64"
        "mac" in os || "darwin" in os -> "darwin-x86_64"
        "linux" in os -> "linux-x86_64"
        else -> throw GradleException("Unsupported host OS: ${System.getProperty("os.name")}")
    }
}
