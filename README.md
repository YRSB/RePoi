# RePoi 泡碗柜 🍱

[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Windows-3DDC84.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.12.1-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Material 3 Expressive](https://img.shields.io/badge/Design-Material%203%20Expressive-E91E63.svg)](https://m3.material.io/)
[![Navigation 3](https://img.shields.io/badge/Navigation-Jetpack%20Navigation%203-00C853.svg)](https://developer.android.com/guide/navigation)
[![Koin](https://img.shields.io/badge/DI-Koin%204.2-orange.svg)](https://insert-koin.io/)
[![Ktor](https://img.shields.io/badge/Network-Ktor%203.6-blue.svg)](https://ktor.io/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**RePoi 泡碗柜**（原名 PoiShelf）是一款专为手办与二次元模型爱好者打造的现代跨平台 Hpoi 维基客户端，支持 **Android** 与 **Windows Desktop**。基于 **Kotlin Multiplatform + Compose Multiplatform + Navigation 3 + Koin + Ktor + Coil 3 + kotlinx.serialization** 现代跨平台技术栈构建，全面践行 **Material 3 Expressive** 设计规范与大屏自适应架构，提供沉浸式高清原图画廊、多维条目检索、双栏自适应相册评论交互以及流畅的视觉体验。

---

## ✨ 核心特性

### 🎨 Material 3 Expressive 设计与自适应体系
- **标准 Material 3 组件库**：全应用统一采用官方 `FilterChip`、`InputChip`、`SuggestionChip`、`ListItem`、`ToggleButton` 连体组、`SingleChoiceSegmentedButtonRow`、`LoadingIndicator` 等标准控件，原生具备细腻的涟漪水波纹动画与微交互触感。
- **全场景大屏自适应 (Adaptive Layout)**：
  - **Adaptive Navigation Suite**：竖屏手机默认底部导航栏（Navigation Bar），桌面端、折叠屏展开态或横屏平板平滑自适应为侧边导航栏（Navigation Rail）。
  - **双窗格评论区 (Supporting Pane)**：相册详情在桌面及宽屏设备上自动展开主内容与评论双栏并排结构（`SupportingPaneScaffold`），紧凑屏智能自动退化为单列折叠。
- **全局 Edge-to-Edge 沉浸视效**：针对 Android 与桌面多窗口规范全面适配，精准协调系统状态栏、导航条避让与软键盘（IME）联动边距。

### 🎎 全面丰富的手办与模型情报
- **首页聚合资讯流**：支持最新发售日历、热门排行、周边情报等多维信息，提供瀑布流（Staggered Grid）与紧凑宫格多视图平滑无缝切换。
- **深度手办详情**：
  - 完整展示比例、尺寸、涂装、发售时间、官方定价与多版本规格参数。
  - 制作商、发行商、原型师、角色/作品原案链路快速关联跳转。
  - 同系列手办聚合横滑流、五星用户评分与维基条目直达。
- **厂商与系列专页**：支持按品牌厂商与作品系列全量聚合并筛选相关手办，内置厂商屏蔽机制。

### 🖼️ 沉浸式高清相册与大图画廊
- **高清原画浏览**：支持双指自由缩放、阻尼平移手势与全屏沉浸大图交互。
- **相册社区互动**：支持点赞与收藏状态实时同步，评论区支持“热门 / 最新”两级分段排序筛选与楼中楼展开。

### 🔍 智能检索与多维筛选系统
- **全要素即时搜索**：支持关键词与拼音模糊检索，提供搜索历史标签管理（支持一键点选与单项删除）。
- **多分类 Tab 过滤**：手办、周边、相册等多重条目类别一键切换。
- **多维 FilterChip 筛选矩阵**：发售状态、预售状态、价格区间与排序字段组合检索。

### 👤 个人中心与账号同步
- **Hpoi 账号鉴权绑定**：支持安全登录并实时同步个人收藏、心愿单、预定与已入手办列表。
- **外观主题模式**：支持跟随系统、浅色模式与深色模式热切换。

---

## 🛠️ 现代跨平台技术栈

| 层次 / 领域 | 技术选型 | 版本 / 说明 |
|---|---|---|
| **语言环境** | [Kotlin](https://kotlinlang.org/) | `2.4.20` (Kotlin Multiplatform) |
| **UI 工具包** | [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) | `1.12.1` (Android & Desktop JVM) |
| **设计规范** | [Material 3](https://m3.material.io/) | `material3:1.12.0-alpha03` + Expressive Tokens |
| **自适应布局** | [Compose Adaptive](https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive) | `material3-adaptive-navigation-suite` + `adaptive-layout:1.3.0-rc01` |
| **导航架构** | [Jetpack Navigation 3](https://developer.android.com/guide/navigation) | `navigation3:1.1.2` + `navigationevent:1.1.0` |
| **依赖注入** | [Koin](https://insert-koin.io/) | `4.2.2` (Koin Core + Compose Multiplatform ViewModel) |
| **网络引擎** | [Ktor Client](https://ktor.io/) | `3.6.0` (OkHttp 引擎 / CIO 引擎 + ContentNegotiation) |
| **数据序列化** | [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | `1.11.0` (JSON 多态与非对称结构宽容解析) |
| **图片加载与缓存** | [Coil 3](https://coil-kt.github.io/coil/) | `3.6.3` (跨平台 Coil 3，支持多级磁盘与内存缓存) |
| **本地持久化** | [DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore) | `1.2.1` (KMP 多平台文件目录存储) |
| **构建分发** | Gradle + Compose Desktop Packaging | 支持 Windows MSIX / MSI / EXE 安装包与免安装分发包 |

---

## 🏗️ 架构概览

RePoi 严格遵循 **单 shared 业务模块 + 极薄宿主入口** 规范：

```text
RePoi/
├── shared/                             # 核心跨平台业务与 UI 模块
│   └── src/
│       ├── commonMain/                 # 跨平台共用业务逻辑与界面
│       │   └── kotlin/com/nobody/repoi/
│       │       ├── data/               # 本地存储 (DataStore)、网络接口 (Ktor) 与仓储实现
│       │       ├── di/                 # Koin 模块注入 (Network, Repository, ViewModel 等)
│       │       ├── platform/           # 平台能力 expect 接口定义
│       │       ├── ui/                 # 全部 Compose Multiplatform 界面与设计系统
│       │       │   ├── navigation/     # Navigation 3 强类型路由与 AppNavDisplay
│       │       │   └── theme/          # Material 3 Expressive 主题与 Tokens
│       │       └── util/               # CDN 图片路径、富文本解析与分页加载器
│       ├── androidMain/                # Android 平台 actual 实现与原生接入
│       ├── jvmMain/                    # Desktop (JVM) 平台 actual 实现与文件目录
│       └── commonTest/                 # 跨平台逻辑与仓储单元测试套件
├── androidApp/                         # Android 端极薄启动宿主 (MainActivity / RepoiApp)
├── desktopApp/                         # Desktop 端极薄启动宿主 (main.kt 与 MSI 打包配置)
└── docs/                               # 领域架构文档、ADR 决策与 API Quirks 避坑手册
```

---

## 📱 运行与构建

> [!IMPORTANT]
> **CI 裁决权威（统一走 CI，严禁本地编译）**  
> 本项目的静态编译、单元测试与双端分发产物验证**全量交由 GitHub Actions CI 权威执行**。日常开发与智能体交付严禁在本地执行任何 Gradle 编译或单测任务；提交并推送至 PR 分支后，统一通过 `gh pr checks` 或 `gh run list` 查验远端流水线状态。

### CI 流水线核心任务说明

- **Android 构建**：`:androidApp:assembleDebug`（生成 Debug APK 产物）
- **桌面构建与打包**：`:desktopApp:packageDistributionForCurrentOS` + `:desktopApp:packageMsi`（生成 Windows/Linux 可分发包与 MSI）
- **跨平台单元测试**：
  - `:shared:jvmTest`（JVM 目标逻辑单测）
  - `:shared:testAndroidHostTest`（Android-Host 宿主逻辑单测）

---

## 📄 开源许可

本项目遵循 [MIT License](LICENSE) 开源许可协议。
