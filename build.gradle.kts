plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register<UpdateResourceFileAssetsTask>("updateResourceFileAssets") {
    val core = providers.gradleProperty("core").orElse("mihomo").get()
    coreId.set(core)
    resourceFileAssetsDir.set(layout.projectDirectory.dir("app/build/generated/resourceFileAssets/$core"))
}
