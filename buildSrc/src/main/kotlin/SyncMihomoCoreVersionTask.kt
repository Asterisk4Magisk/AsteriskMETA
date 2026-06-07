// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class SyncMihomoCoreVersionTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Input
    abstract val mihomoCoreVersion: Property<String>

    @get:Internal
    abstract val repositoryRootDirectory: DirectoryProperty

    @get:Internal
    abstract val submoduleDirectory: DirectoryProperty

    @get:Input
    abstract val submodulePath: Property<String>

    init {
        group = "build setup"
        description = "Checkout vendored mihomo submodule to the configured core version."
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun sync() {
        val rootDir = repositoryRootDirectory.get().asFile
        val submoduleDir = submoduleDirectory.get().asFile
        val version = mihomoCoreVersion.get()

        if (!submoduleDir.resolve(".git").exists()) {
            runGit(rootDir, "submodule", "update", "--init", "--recursive", submodulePath.get())
        }

        if (runGit(submoduleDir, "rev-parse", "--verify", "refs/tags/$version", ignoreExitValue = true) != 0) {
            runGit(submoduleDir, "fetch", "--depth", "1", "origin", "tag", version)
        }
        runGit(submoduleDir, "checkout", "--detach", version)
    }

    private fun runGit(workingDirectory: java.io.File, vararg args: String, ignoreExitValue: Boolean = false): Int {
        val result = execOperations.exec {
            commandLine(listOf("git", "-C", workingDirectory.absolutePath) + args)
            isIgnoreExitValue = ignoreExitValue
        }
        if (!ignoreExitValue && result.exitValue != 0) {
            throw GradleException("Command failed with exit code ${result.exitValue}: git -C ${workingDirectory.absolutePath} ${args.joinToString(" ")}")
        }
        return result.exitValue
    }
}
