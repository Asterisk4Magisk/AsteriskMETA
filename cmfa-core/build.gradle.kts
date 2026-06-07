@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

val golangSource = layout.projectDirectory.dir("src/main/golang/native")
val goModuleDir = layout.projectDirectory.dir("src/foss/golang")
val mihomoSubmoduleDir = layout.projectDirectory.dir("src/foss/golang/clash")
val goOutputDir = layout.buildDirectory.dir("outputs/golang")
val rootLocalPropertiesFile = rootProject.layout.projectDirectory.file("local.properties")

val syncMihomoCoreVersion by tasks.registering(SyncMihomoCoreVersionTask::class) {
    mihomoCoreVersion.set(ProjectConfig.MIHOMO_CORE_VERSION)
    repositoryRootDirectory.set(rootProject.layout.projectDirectory)
    submoduleDirectory.set(mihomoSubmoduleDir)
    submodulePath.set(mihomoSubmoduleDir.asFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath)
}

android {
    namespace = "com.github.kr328.clash.core"
    compileSdk = ProjectConfig.TARGET_SDK

    defaultConfig {
        minSdk = ProjectConfig.MIN_SDK
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += ProjectConfig.SUPPORTED_ANDROID_ABIS
        }
        externalNativeBuild {
            cmake {
                abiFilters += ProjectConfig.SUPPORTED_ANDROID_ABIS
            }
        }
    }

    flavorDimensions += "cmfa"
    productFlavors {
        create("alpha") {
            dimension = "cmfa"
        }
        create("meta") {
            dimension = "cmfa"
        }
        all {
            externalNativeBuild {
                cmake {
                    arguments("-DGO_SOURCE:STRING=${golangSource.asFile.absolutePath}")
                    arguments("-DGO_OUTPUT:STRING=${goOutputDir.get().asFile.absolutePath}")
                    arguments("-DFLAVOR_NAME:STRING=$name")
                    arguments("-DMIHOMO_CORE_VERSION:STRING=${ProjectConfig.MIHOMO_CORE_VERSION}")
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    lint {
        disable += "ChromeOsAbiSupport"
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}

val abis = listOf(
    "arm64-v8a" to "Arm64V8a",
    "armeabi-v7a" to "ArmeabiV7a",
    "x86" to "X86",
    "x86_64" to "X8664",
)

androidComponents.onVariants { variant ->
    val cmakeName = if (variant.buildType == "debug") "Debug" else "RelWithDebInfo"
    val variantOutputDir = goOutputDir.map { directory -> directory.dir(variant.name) }

    variant.sources.jniLibs?.addStaticSourceDirectory(variantOutputDir.get().asFile.absolutePath)

    abis.forEach { (abi, goAbi) ->
        val taskName = "externalGolangBuild${variant.name.capitalizedForTask()}$goAbi"
        val outputDir = variantOutputDir.map { directory -> directory.dir(abi) }
        val outputFile = outputDir.map { directory -> directory.file("libclash.so") }
        val debug = variant.buildType == "debug"
        val tags = buildList {
            add("foss")
            add("with_gvisor")
            add("cmfa")
            if (debug) {
                add("debug")
            }
        }

        tasks.register<BuildCmfaGoCoreTask>(taskName) {
            description = "Build CMFA Go core for ${variant.name} $abi."
            dependsOn(syncMihomoCoreVersion)
            goModuleDirectory.set(goModuleDir)
            goSourceDirectory.set(golangSource)
            mihomoSubmoduleDirectory.set(mihomoSubmoduleDir)
            if (rootLocalPropertiesFile.asFile.exists()) {
                localPropertiesFile.set(rootLocalPropertiesFile)
            }
            minSdk.set(ProjectConfig.MIN_SDK)
            androidAbi.set(abi)
            debugBuild.set(debug)
            this.tags.set(tags)
            mihomoCoreVersion.set(ProjectConfig.MIHOMO_CORE_VERSION)
            this.outputFile.set(outputFile)
        }

        tasks.configureEach {
            if (name == "merge${variant.name.capitalizedForTask()}JniLibFolders") {
                dependsOn(taskName)
            }
            if (name.startsWith("configureCMake$cmakeName[$abi]")) {
                dependsOn(syncMihomoCoreVersion)
            }
            if (name.startsWith("buildCMake$cmakeName[$abi]")) {
                dependsOn(taskName)
            }
        }
    }
}

private fun String.capitalizedForTask(): String {
    return replaceFirstChar { char -> char.uppercase() }
}
