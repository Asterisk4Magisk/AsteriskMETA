import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder

plugins {
    alias(libs.plugins.android.library)
}

val hevSocks5TunnelSubmoduleDir = layout.projectDirectory.dir("src/main/jni/hev-socks5-tunnel")
val generatedJniLibsDir: Provider<Directory> = layout.buildDirectory.dir("generated/jniLibs")
val rootLocalPropertiesFile = rootProject.layout.projectDirectory.file("local.properties")

val syncHevSocks5TunnelVersion = tasks.register<SyncHevSocks5TunnelVersionTask>("syncHevSocks5TunnelVersion") {
    hevSocks5TunnelVersion.set(ProjectConfig.HEV_SOCKS5_TUNNEL_VERSION)
    repositoryRootDirectory.set(rootProject.layout.projectDirectory)
    submoduleDirectory.set(hevSocks5TunnelSubmoduleDir)
    submodulePath.set(hevSocks5TunnelSubmoduleDir.asFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath)
}

android {
    namespace = "hevtun"
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

val buildHevTun = tasks.register<BuildHevTunTask>("buildHevTun") {
    dependsOn(syncHevSocks5TunnelVersion)
    sourceDirectory.set(hevSocks5TunnelSubmoduleDir)
    outputDirectory.set(generatedJniLibsDir)
    if (rootLocalPropertiesFile.asFile.exists()) {
        localPropertiesFile.set(rootLocalPropertiesFile)
    }
    minSdk.set(ProjectConfig.MIN_SDK)
    targetAbis.set(ProjectConfig.SUPPORTED_ANDROID_ABIS)
}

tasks.named("preBuild") {
    dependsOn(buildHevTun)
}
