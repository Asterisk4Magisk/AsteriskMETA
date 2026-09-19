// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

@file:Suppress("UnstableApiUsage")

rootProject.name = "AsteriskMETA"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://jitpack.io")
        exclusiveContent {
            forRepository {
                ivy {
                    name = "AndroidLibClashLiteGitHubRelease"
                    url = uri("https://github.com/Asterisk4Magisk/AndroidLibClashLite/releases/download")
                    patternLayout { artifact("[revision]/[artifact].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.github.Asterisk4Magisk", "libclash") }
        }
        maven("https://raw.githubusercontent.com/MetaCubeX/maven-backup/main/releases")
    }
}

include(":app")
include(":asteriskd")
include(":bpfmatcher")
include(":bpf2socks")
include(":hevtun")
