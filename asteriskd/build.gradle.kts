// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

plugins {
    alias(libs.plugins.android.library)
}

val generatedJniLibsDir: Provider<Directory> = layout.buildDirectory.dir("generated/jniLibs")
val asteriskdSubmoduleDir = layout.projectDirectory.dir("src/main/native")

android {
    namespace = "asteriskd"
    compileSdk = ProjectConfig.TARGET_SDK

    defaultConfig {
        minSdk = ProjectConfig.MIN_SDK
        ndk {
            abiFilters += ProjectConfig.SUPPORTED_ANDROID_ABIS
        }
    }

    androidComponents {
        onVariants { variant ->
            variant.sources.jniLibs?.addStaticSourceDirectory("build/generated/jniLibs")
        }
    }

    lint {
        disable += "ChromeOsAbiSupport"
    }
}

val syncAsteriskdVersion = tasks.register<SyncGitSubmoduleVersionTask>("syncAsteriskdVersion") {
    submoduleVersion.set(ProjectConfig.ASTERISKD_VERSION)
    repositoryRootDirectory.set(rootProject.layout.projectDirectory)
    submoduleDirectory.set(asteriskdSubmoduleDir)
    submodulePath.set(asteriskdSubmoduleDir.asFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath)
}

val buildAsteriskd = tasks.register<BuildAsteriskdTask>("buildAsteriskd") {
    dependsOn(syncAsteriskdVersion)
    sourceDirectory.set(asteriskdSubmoduleDir)
    outputDirectory.set(generatedJniLibsDir)
    rootProject.layout.projectDirectory.file("local.properties")
        .takeIf { it.asFile.exists() }
        ?.let(localPropertiesFile::set)
    minSdk.set(ProjectConfig.MIN_SDK)
    targetAbis.set(ProjectConfig.SUPPORTED_ANDROID_ABIS)
}

tasks.named("preBuild") {
    dependsOn(buildAsteriskd)
}
