# RePoi — 项目宪章与导航索引

RePoi 是基于 Kotlin Multiplatform 的 Hpoi 维基跨平台客户端（包名 `com.nobody.repoi`，支持 Android 与 Windows Desktop）。
所有用户可见文本、代码注释及 Commit 信息**一律使用中文（简体）**。

---

## 领域实现规范索引 (Context Pointers)

进入具体功能开发时，按需查阅对应章节与逆向产物：
- **Topology**（特性入口、NavKey 路由、主界面与仓储对应表）：查阅 [CODING_STANDARDS.md#路由契约与拓扑速查-navigation-3](CODING_STANDARDS.md#路由契约与拓扑速查-navigation-3)
- **Architecture**（KMP 单 shared 模块、Koin 依赖注入、单向数据流与分页加载器）：查阅 [CODING_STANDARDS.md#架构与数据流-architecture--di](CODING_STANDARDS.md#架构与数据流-architecture--di)
- **UI**（Material 3 Expressive 规范、自适应双窗格、悬浮栏、网格与 CDN 图片变体）：查阅 [CODING_STANDARDS.md#自适应与-material-3-expressive-设计规范](CODING_STANDARDS.md#自适应与-material-3-expressive-设计规范)
- **Quirks**（Hpoi API 反序列化多态、图片 path 拼接与逆向踩坑）：查阅 [docs/API_QUIRKS.md](docs/API_QUIRKS.md)
- **Domain**（业务模型、实体命名契约与领域术语）：查阅 [docs/domain.md](docs/domain.md)
- **ADR**（架构演进决策与选型归档）：查阅 [docs/adr/](docs/adr/)
- **Tracker**（GitHub Issue 与任务分流管理）：查阅 [docs/issue-tracker.md](docs/issue-tracker.md) 与 [docs/triage-labels.md](docs/triage-labels.md)

---

## 交付完成门禁 (Completion Criteria)

完成代码变更并在交付时，必须满足以下硬性判据：
1. **CI 裁决权威（全量走 CI，严禁本地编译）**：
   - 验证统一交由 GitHub Actions CI 远端流水线执行，**本地严禁执行任何 Gradle 编译或测试任务**；
   - 代码变更直接提交并推送到对应分支或 PR，通过 `gh pr checks` 或 `gh run list` 查看远端流水线状态；
   - 判定通过标准：远端 CI 双端工作流（Android 编译、JVM 目标单测、Android-host 宿主单测、Desktop 产物验证）全绿。
2. **规范提交信息**：遵循 Conventional Commits 1.0.0，提交信息全中文、无末尾句号：
   `<type>(<scope>): <subject>`（例如 `feat(detail): 增加手办发售信息卡片`；scope 统一为小写特性名，如 `home`, `detail`, `search`, `network`, `nav` 等）。
3. **渲染与性能规范**：所有 Lazy 列表项强制显式配置稳定的 `key` 与 `contentType`。
