# RePoi 泡碗柜 🍱

[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Windows-3DDC84.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.12-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

基于 Kotlin Multiplatform 的 Hpoi 维基跨平台客户端，支持 **Android** 与 **Windows Desktop**。界面采用 Compose Multiplatform 与 Material 3 Expressive 设计规范，提供自适应大屏布局（Supporting Pane 双栏、侧边栏自适应）与全屏高清画廊。

---

## 🛠️ 技术选型

- **跨平台框架**：Kotlin Multiplatform (KMP) + Compose Multiplatform
- **设计规范**：Material 3 Expressive (`material3:1.12-alpha`) + Adaptive Suite
- **路由导航**：Jetpack Navigation 3 (`navigation3:1.1.2`)
- **依赖注入**：Koin 4.2 (`koin-core` + `koin-compose-viewmodel`)
- **网络与序列化**：Ktor 3.6 + kotlinx.serialization
- **图片与持久化**：Coil 3.6 + Multiplatform DataStore Preferences

---

## 🏗️ 模块架构

```text
RePoi/
├── shared/                             # 核心跨平台业务与 UI
│   ├── src/commonMain/                 # 领域模型、Ktor API、仓储、ViewModel 与 Compose 页面
│   ├── src/androidMain/                # Android 平台 expect/actual 垫片
│   ├── src/jvmMain/                    # Desktop (JVM) 平台 expect/actual 垫片
│   └── src/commonTest/                 # 跨平台单元测试套件
├── androidApp/                         # Android 宿主外壳
├── desktopApp/                         # Desktop 宿主外壳 (MSI / EXE 打包)
└── docs/                               # 架构规范、ADR 决策与 API Quirks 避坑手册
```

---

## 🚀 CI 验证与构建

> [!IMPORTANT]
> **CI 裁决权威（统一走 CI，严禁本地编译）**  
> 本项目的编译、单测与产物打包验证**全量交由 GitHub Actions CI 执行**，本地严禁执行 Gradle 编译或单测任务。代码改动直接提交并推送到 PR 分支，通过 `gh pr checks` 或 Actions 页面查看执行状态。

CI 远端流水线执行任务：
- **Android APK 构建**：`:androidApp:assembleDebug`
- **桌面端打包**：`:desktopApp:packageDistributionForCurrentOS` + `:desktopApp:packageMsi`
- **跨平台单元测试**：`:shared:jvmTest` + `:shared:testAndroidHostTest`

---

## 📄 开源许可

本项目遵循 [MIT License](LICENSE) 开源许可协议。
