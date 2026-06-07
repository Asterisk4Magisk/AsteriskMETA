plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register<UpdateResourceFileAssetsTask>("updateResourceFileAssets") {
    mihomoCoreVersion.set(ProjectConfig.MIHOMO_CORE_VERSION)
    hevSocks5TunnelVersion.set(ProjectConfig.HEV_SOCKS5_TUNNEL_VERSION)
    mihomoCoreJniLibsDir.set(layout.projectDirectory.dir("app/build/generated/mihomoCoreJniLibs"))
    hevSocks5TunnelJniLibsDir.set(layout.projectDirectory.dir("app/build/generated/hevSocks5TunnelJniLibs"))
    resourceFileAssetsDir.set(layout.projectDirectory.dir("app/build/generated/resourceFileAssets"))
}
