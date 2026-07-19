import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder

plugins {
    alias(libs.plugins.android.library)
}

val generatedJniLibsDir: Provider<Directory> = layout.buildDirectory.dir("generated/jniLibs")
val setuidgidSubmoduleDir = layout.projectDirectory.dir("src/main/native")

android {
    namespace = "setuidgid"
    compileSdk = ProjectConfig.TARGET_SDK

    defaultConfig {
        minSdk = ProjectConfig.MIN_SDK
        ndk {
            abiFilters += ProjectConfig.SUPPORTED_ANDROID_ABIS
        }
    }

    androidComponents {
        beforeVariants(selector().all()) { variant ->
            variant.enableAndroidTest = false
            (variant as? HasHostTestsBuilder)
                ?.hostTests
                ?.get(HostTestBuilder.UNIT_TEST_TYPE)
                ?.enable = false
        }

        onVariants { variant ->
            variant.sources.jniLibs?.addStaticSourceDirectory("build/generated/jniLibs")
        }
    }

    lint {
        disable += "ChromeOsAbiSupport"
    }
}

val syncSetuidgidVersion = tasks.register<SyncGitSubmoduleVersionTask>("syncSetuidgidVersion") {
    submoduleVersion.set(ProjectConfig.SETUIDGID_VERSION)
    repositoryRootDirectory.set(rootProject.layout.projectDirectory)
    submoduleDirectory.set(setuidgidSubmoduleDir)
    submodulePath.set(setuidgidSubmoduleDir.asFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath)
}

val buildSetuidgid = tasks.register<BuildSetuidgidTask>("buildSetuidgid") {
    dependsOn(syncSetuidgidVersion)
    sourceFile.set(setuidgidSubmoduleDir.file("setuidgid.c"))
    outputDirectory.set(generatedJniLibsDir)
    rootProject.layout.projectDirectory.file("local.properties")
        .takeIf { it.asFile.exists() }
        ?.let(localPropertiesFile::set)
    minSdk.set(ProjectConfig.MIN_SDK)
    targetAbis.set(ProjectConfig.SUPPORTED_ANDROID_ABIS)
}

tasks.named("preBuild") {
    dependsOn(buildSetuidgid)
}
