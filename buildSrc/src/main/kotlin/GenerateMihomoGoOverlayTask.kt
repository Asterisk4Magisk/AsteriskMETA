// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class GenerateMihomoGoOverlayTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val originalSourceFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val replacementSourceFile: RegularFileProperty

    @get:OutputFile
    abstract val overlayFile: RegularFileProperty

    init {
        group = "build setup"
        description = "Generate the Go overlay for Mihomo log event timestamps."
    }

    @TaskAction
    fun generate() {
        val originalSource = originalSourceFile.get().asFile
        val replacementSource = replacementSourceFile.get().asFile
        validateSourceContract(originalSource)

        overlayFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                """
                {
                  "Replace": {
                    "${originalSource.absolutePath.jsonEscaped()}": "${replacementSource.absolutePath.jsonEscaped()}"
                  }
                }
                """.trimIndent(),
            )
        }
    }

    private fun validateSourceContract(sourceFile: File) {
        val source = sourceFile.readText()
        val missingContracts = listOf(
            "type Event struct",
            "func newLog(",
        ).filterNot(source::contains)
        if (missingContracts.isNotEmpty()) {
            throw GradleException(
                "Pinned Mihomo log source no longer matches the timestamp overlay contract: " +
                    "${sourceFile.absolutePath}; missing ${missingContracts.joinToString()}",
            )
        }
    }
}

private fun String.jsonEscaped(): String {
    return buildString(length) {
        this@jsonEscaped.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(char)
            }
        }
    }
}
