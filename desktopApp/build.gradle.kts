import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)

    // main.kt 调用 shared 的 initKoin()（签名含 KoinAppDeclaration/KoinApplication）：
    // shared 的 implementation 依赖不向消费端编译期泄漏（PodAura 同范式：
    // 其 androidApp 自行声明 koin-core），故桌面壳直引 koin-core。
    implementation(libs.koin.core)
}

compose.desktop {
    application {
        mainClass = "com.example.repoi.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.example.repoi"
            packageVersion = "1.0.0"
        }
    }
}