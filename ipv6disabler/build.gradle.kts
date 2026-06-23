import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder

plugins {
    alias(libs.plugins.android.library)
}

val generatedJniLibsDir: Provider<Directory> = layout.buildDirectory.dir("generated/jniLibs")

android {
    namespace = "ipv6disabler"
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

val buildIpv6Disabler = tasks.register<BuildIpv6DisablerTask>("buildIpv6Disabler") {
    sourceFile.set(layout.projectDirectory.file("src/main/native/ipv6disabler.c"))
    outputDirectory.set(generatedJniLibsDir)
    rootProject.layout.projectDirectory.file("local.properties")
        .takeIf { it.asFile.exists() }
        ?.let(localPropertiesFile::set)
    minSdk.set(ProjectConfig.MIN_SDK)
    targetAbis.set(ProjectConfig.SUPPORTED_ANDROID_ABIS)
}

tasks.named("preBuild") {
    dependsOn(buildIpv6Disabler)
}
