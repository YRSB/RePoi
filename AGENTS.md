# RePoi — 项目宪章与导航索引

RePoi 是基于 Kotlin Multiplatform 的 Hpoi 维基跨平台客户端（包名 `com.nobody.repoi`，支持 Android 与 Windows Desktop）。
所有用户可见文本、代码注释及 Commit 信息**一律使用中文（简体）**。

---

## 领域实现规范索引 (Context Pointers)

进入具体功能开发时，按需查阅对应章节与逆向产物：
- **Topology**（特性入口、NavKey 路由、主界面与仓储对应表）：查阅 [CODING_STANDARDS.md#路由契约与拓扑速查-navigation-3](CODING_STANDARDS.md#路由契约与拓扑速查-navigation-3)
- **Architecture**（KMP 单 shared 模块、Koin 依赖注入、单向数据流与分页加载器）：查阅 [CODING_STANDARDS.md#架构与数据流-architecture--di](CODING_STANDARDS.md#架构与数据流-architecture--di)
- **Libraries**（KMP 依赖选型与 klibs.io MCP 权威校验）：查阅 [CODING_STANDARDS.md#5-跨平台依赖引入规范-kmp-library-selection](CODING_STANDARDS.md#5-跨平台依赖引入规范-kmp-library-selection)
- **UI**（Material 3 Expressive 规范、自适应双窗格、悬浮栏、网格与 CDN 图片变体）：查阅 [CODING_STANDARDS.md#自适应与-material-3-expressive-设计规范](CODING_STANDARDS.md#自适应与-material-3-expressive-设计规范)
- **Collect**（收藏状态池、8 类列表与去重键、`UserCollectIntent`、画廊脏位恢复感知、排序记忆、滚动键）：查阅 [docs/collect.md](docs/collect.md)
- **Media**（图片显示、CDN 变体档位、404 避坑、`PoiAsyncImage` / `PoiZoomableImage`、全屏看图）：查阅 [docs/media.md](docs/media.md)
- **Quirks**（Hpoi API 反序列化多态、图片 path 拼接与逆向踩坑）：查阅 [docs/API_QUIRKS.md](docs/API_QUIRKS.md)
- **Domain**（业务模型、实体命名契约与领域术语）：查阅 [docs/domain.md](docs/domain.md)
- **ADR**（架构演进决策与选型归档）：查阅 [docs/adr/](docs/adr/)
- **Tracker**（GitHub Issue 与任务分流管理）：查阅 [docs/issue-tracker.md](docs/issue-tracker.md) 与 [docs/triage-labels.md](docs/triage-labels.md)
- **Testing**（单测调度器三件套、CI 取证回路与被隔离测试登记表）：查阅 [docs/testing.md](docs/testing.md)

---

## 交付完成门禁 (Completion Criteria)

完成代码变更并在交付时，必须满足以下硬性判据：
1. **CI 裁决权威**：验证统一交由 GitHub Actions 远端流水线执行，判定标准见
   [CODING_STANDARDS.md#4-验证原则ci-裁决权威-ci-authority](CODING_STANDARDS.md#4-验证原则ci-裁决权威-ci-authority)。
   提交前可本地跑 `pwsh -File scripts/check-ui-rules.ps1`（纯静态检查，**不需要 Gradle / JDK**），
   它能抓住编译与单测都发现不了的机械错误。
2. **规范提交信息**：遵循 Conventional Commits 1.0.0，提交信息全中文、无末尾句号：
   `<type>(<scope>): <subject>`（例如 `feat(detail): 增加手办发售信息卡片`；scope 统一为小写特性名，如 `home`, `detail`, `search`, `network`, `nav` 等）。
3. **渲染与性能规范**：Lazy 列表项必须显式配置稳定的 `key` 与 `contentType`（骨架屏占位项同样适用），细则见 [CODING_STANDARDS.md#自适应与-material-3-expressive-设计规范](CODING_STANDARDS.md#自适应与-material-3-expressive-设计规范)。
4. **KMP 依赖选型门禁 (Kotlin Multiplatform Library Selection)**：
   在添加或推荐 Kotlin Multiplatform 依赖前，必须先查询 klibs.io MCP (`https://api.klibs.io/mcp`) 或借助 `kmp-libraries-expert` 技能核验依赖元数据：
   - 目标平台兼容性（本项目必须支持 Android 与 Desktop/JVM）；
   - Maven 准确坐标（Maven coordinate）；
   - 最新稳定版本（Latest stable version）；
   - 开源许可证（License）与维护活跃度指标；
   - 严禁凭记忆臆测版本号或引入未经平台验证的单端库。
