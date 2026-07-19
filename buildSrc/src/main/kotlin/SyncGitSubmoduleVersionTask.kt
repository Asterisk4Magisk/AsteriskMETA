// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class SyncGitSubmoduleVersionTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Input
    abstract val submoduleVersion: Property<String>

    @get:Internal
    abstract val repositoryRootDirectory: DirectoryProperty

    @get:Internal
    abstract val submoduleDirectory: DirectoryProperty

    @get:Input
    abstract val submodulePath: Property<String>

    init {
        group = "build setup"
        description = "Checkout a Git submodule to its configured version."
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun sync() {
        val rootDirectory = repositoryRootDirectory.get().asFile
        val moduleDirectory = submoduleDirectory.get().asFile
        val modulePath = submodulePath.get()
        val version = submoduleVersion.get()

        if (!moduleDirectory.resolve(".git").exists()) {
            runGit(rootDirectory, "submodule", "update", "--init", "--recursive", modulePath)
        }
        requireClean(moduleDirectory, modulePath)
        if (runGit(moduleDirectory, "rev-parse", "--verify", "refs/tags/$version^{commit}", ignoreExitValue = true) != 0) {
            runGit(moduleDirectory, "fetch", "--depth", "1", "origin", "tag", version)
        }
        runGit(moduleDirectory, "checkout", "--detach", "refs/tags/$version")
        runGit(moduleDirectory, "submodule", "update", "--init", "--recursive")
        requireClean(moduleDirectory, modulePath)
    }

    private fun requireClean(moduleDirectory: File, modulePath: String) {
        val status = gitOutput(
            moduleDirectory,
            "status",
            "--porcelain=v1",
            "--untracked-files=all",
            "--ignore-submodules=none",
        )
        if (status.isNotBlank()) {
            throw GradleException(
                "Submodule '$modulePath' has local changes. " +
                    "Commit, stash, or remove them before building:\n$status",
            )
        }
    }

    private fun gitOutput(workingDirectory: File, vararg arguments: String): String {
        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(listOf("git", "-C", workingDirectory.absolutePath) + arguments)
            standardOutput = output
        }
        return output.toString(Charsets.UTF_8).trim()
    }

    private fun runGit(
        workingDirectory: File,
        vararg arguments: String,
        ignoreExitValue: Boolean = false,
    ): Int {
        val result = execOperations.exec {
            commandLine(listOf("git", "-C", workingDirectory.absolutePath) + arguments)
            isIgnoreExitValue = ignoreExitValue
        }
        if (!ignoreExitValue && result.exitValue != 0) {
            throw GradleException(
                "Command failed with exit code ${result.exitValue}: " +
                    "git -C ${workingDirectory.absolutePath} ${arguments.joinToString(" ")}",
            )
        }
        return result.exitValue
    }
}
