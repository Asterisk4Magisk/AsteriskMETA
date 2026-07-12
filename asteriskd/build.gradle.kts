// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

plugins {
    alias(libs.plugins.android.library)
}

val generatedJniLibsDir: Provider<Directory> = layout.buildDirectory.dir("generated/jniLibs")

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

val buildAsteriskd = tasks.register<BuildAsteriskdTask>("buildAsteriskd") {
    sourceDirectory.set(layout.projectDirectory.dir("src/main/native"))
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




