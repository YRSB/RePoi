import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    // 冷启动启动遮罩（ADR-0012）：API 24+ 向后兼容的官方启动画面支持库。
    // 仅 Android 宿主需要，shared 公共代码不引用，故留在单端 shell。
    implementation(libs.androidx.core.splashscreen)

    // M2: Koin android 扩展（androidLogger/androidContext，供 RepoiApp thin init）。
    implementation(libs.koin.android)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.nobody.repoi"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.nobody.repoi"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    val signingPropsFile = rootProject.file("signing.properties")
    val signingProps = if (signingPropsFile.exists()) {
        Properties().apply {
            signingPropsFile.inputStream().use { load(it) }
        }
    } else null

    signingConfigs {
        // 固定内置 debug 签名，确保本地开发与 CI 构建产物签名指纹始终一致，支持直接覆盖安装更新
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (signingProps != null) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("KEYSTORE_FILE"))
                storePassword = signingProps.getProperty("KEYSTORE_PASSWORD")
                keyAlias = signingProps.getProperty("KEY_ALIAS")
                keyPassword = signingProps.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 存在外部 release 签名配置时优先使用；未配置时默认回退到 debug 签名，确保产物已签名且可与 debug 构建相互覆盖安装
            signingConfig = if (signingProps != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}
