# ADR-0001: PoiShelf 迁移至 KMP 跨平台架构与技术栈对齐

## 状态
已采纳 (Accepted) - 2026-10-30

## 上下文与问题陈述
原应用 PoiShelf 采用纯原生 Android 单模块架构（Dagger Hilt + Retrofit 2 + Coil 2 + 原生 Navigation 3），仅能在 Android 设备运行。为拓展至桌面端（Windows / Linux / macOS），并实现一套代码驱动多端 UI 与业务，需将工程全面重构为 Kotlin Multiplatform (KMP) 跨平台架构。

## 决策结论
1. **工程结构**：
   - 采用单 `:shared` 业务模块（内含 `commonMain`、`androidMain`、`jvmMain`、`commonTest`）；
   - `:androidApp` 与 `:desktopApp` 仅保留极薄入口外壳，统一调用 `shared` 中的根组件 `PoiShelfApp()`。
2. **跨平台技术栈选型**：
   - **依赖注入**：Dagger Hilt → Koin 4.2+（跨平台原生支持，模块化声明）。
   - **网络客户端**：Retrofit 2 + OkHttp 4 → Ktor 3.6+（`HttpClient` + `ContentNegotiation`）。
   - **图片加载**：Coil 2 → Coil 3.6+（全平台 Compose 支持）。
   - **数据持久化**：Jetpack DataStore → Multiplatform DataStore Preferences。
   - **设计系统**：Material 3 Expressive（`material3:1.12.0-alpha03` + Expressive Tokens）。
   - **平台差异收敛**：通过 `com.nobody.repoi.platform` 下的 `expect/actual` 垫片隔离（如 `AppDirs`, `BackPress`, `Sharing`, `ThemeCaps` 等）。

## 影响
- **积极影响**：
  - 95% 以上的 UI、数据模型、仓储与 ViewModel 完全复用；
  - 桌面端具备热重载与原生 MSI/EXE 分发能力；
  - CI 支持多平台并行编译与快速单元测试。
- **消极影响 / 妥协**：
  - 架构迁移初期部分紧耦合于 Android Looper 的 ViewModel 测试需隔离（Quarantine），由纯逻辑单测与端侧 E2E 作为质量兜底。
