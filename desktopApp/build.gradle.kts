import de.stefan_oltmann.msix.CreateAppxManifestTask
import de.stefan_oltmann.msix.CreateMsixIconsTask
import de.stefan_oltmann.msix.CreateMsixTask
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import repoi.buildlogic.configureWindowsMsiOpenWith
import repoi.buildlogic.repoiFileAssociations

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.gradleMsix)
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
        mainClass = "com.nobody.repoi.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.nobody.repoi"
            packageVersion = "1.0.0"

            windows {
                // M6: 无 RePoi .ico 资产时不设 iconFile（jpackage 回落默认图标）；
                // 补入 desktopApp/icons/RePoi.ico 后取消注释下一行。
                // iconFile = project.file("icons/RePoi.ico")
                dirChooser = true
                shortcut = true
                menu = true
                menuGroup = "RePoi"
                // PoiShelf 无文件扩展名注册（见 repoi.buildlogic)：fileAssociation 省略。
                // https://wixtoolset.org/documentation/manual/v3/howtos/general/generate_guids.html
                // M6 生成一次、永不改动。
                upgradeUuid = "7C4E9A2B-3F1D-4E8A-9C6B-5D2F0A1B8E47"
            }

            modules(
                "jdk.unsupported",
                "java.sql",
            )
        }

        buildTypes.release.proguard {
            version = "7.9.1"
            // obfuscate = true
            configurationFiles.from(project.file("compose-desktop.pro"))
        }
    }
}

msix {
    svgIcon = rootProject.layout.projectDirectory.file("desktopApp/icons/RePoi.svg")

    manifest {
        appId = "RePoi"
        displayName = "RePoi"
        description = "PoiShelf 跨平台客户端（Android + Windows）。"
        identityName = "RePoi.PoiShelf"
        publisher = "CN=RePoi-SelfSign"
        publisherDisplayName = "RePoi"
        version = "1.0.0.0"
        processorArchitecture = "x64"
        // jpackage 以 packageName 作 launcher 名；packageName 更名时同步改此处。
        appExecutable = "com.nobody.repoi.exe"
        targetDeviceFamilyMinVersion = "10.0.17763.0"
    }
}

// gradle-msix-plugin cannot discover the package name from Compose 1.12.
// jpackage app-image 目录名同样取 packageName；createMsix 系 M7 试跑项
// （自签 publisher 待定），此处先按派生规则写准路径。
val msixApplicationDirectory =
    layout.buildDirectory.dir("compose/binaries/main-release/app/com.nobody.repoi")

tasks.named<CreateMsixIconsTask>("createMsixIcons").configure {
    outputDir = msixApplicationDirectory.map { it.dir("resources") }
}
tasks.named<CreateAppxManifestTask>("createAppxManifest") {
    outputFile = msixApplicationDirectory.map { it.file("AppxManifest.xml") }
    // M6: 无文件关联/通知协议需求，media/notification doLast 钩子省略。
}
tasks.named<CreateMsixTask>("createMsix") {
    appDirectory = msixApplicationDirectory
    msixOutputFile = layout.buildDirectory.file("com.nobody.repoi.msix")
}

tasks.withType<AbstractJPackageTask>().configureEach {
    if (targetFormat == TargetFormat.Msi) {
        inputs.property("openWithExtensions", repoiFileAssociations().keys)
        doLast {
            configureWindowsMsiOpenWith(
                destinationDir.get().asFile.resolve("${packageName.get()}-${packageVersion.get()}.msi"),
                temporaryDir,
                repoiFileAssociations().keys,
            )
        }
    }
}