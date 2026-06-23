import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder

plugins {
    alias(libs.plugins.android.library)
}

val generatedJniLibsDir: Provider<Directory> = layout.buildDirectory.dir("generated/jniLibs")

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

val buildSetuidgid = tasks.register<BuildSetuidgidTask>("buildSetuidgid") {
    sourceFile.set(layout.projectDirectory.file("src/main/native/setuidgid.c"))
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
