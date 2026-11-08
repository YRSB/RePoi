# ADR-0006: 统一路由与动作解析器 (AppRouteResolver) 架构深化

## 状态
已采纳 (Accepted) - 2026-11-08

## 上下文与问题陈述
在 RePoi 现存的导航与动态消费链路中，存在两套各自为政、浅度耦合的调度分发机制：
1. `FeedActionDispatcher`：消费动态卡片数据模型 `ActionItem`，但在分发跳转时通过 10 个 nullable lambda 回调向 UI 层层透传（Callback Drilling），且在工具类中硬编码 UI 提示；
2. `HpoiLinkDispatcher`：消费富文本超链接，自行维护内部路由枚举 `HpoiRoute`，并直接耦合 Compose 运行时平台类 `UriHandler`；
3. **脱节 Navigation 3 强类型拓扑**：两者均未直接输出 Navigation 3 的核心契约 `AppNavKey`，导致 `HomeFeedScreen`、`FollowScreen`、`VendorDetailScreen`、`ZoneDetailScreen` 等 5 处屏幕和子卡片被迫声明并透传 8~10 个特定类型的导航回调；
4. **单测成本高昂**：逻辑夹杂副作用与 UI 回调，难以在脱离 Compose 环境的纯 JVM/KMP 测试中快速验证分发规则。

## 候选方案评估

### 方案 A：在既有 Dispatcher 上叠加 Navigator 回调封装
- 做法：保留 `FeedActionDispatcher` 与 `HpoiLinkDispatcher`，但在最外层提供传入 `RePoiNavigator` 的辅助函数。
- 优点：对旧调用链改动较小。
- 缺点：治标不治本；未消除“浅调度器”本质，依然存在双轨模型（`FeedTargetDestination` 与 `HpoiRoute`），回调击穿问题仍残留在屏幕层，旧技术债长期滞留。

### 方案 B：统一纯计算深模块 `AppRouteResolver` 与强类型 `RouteTarget` 契约（采纳）
- 做法：
  1. **纯计算解析深模块**：建立 `com.nobody.repoi.ui.navigation.AppRouteResolver`，零 Compose 依赖、零平台副作用，纯函数解析 `ActionItem` 与 `URL` 为统一强类型多态模型 `RouteTarget`；
  2. **统一路由目标契约**：`RouteTarget` 包含 `Internal(val key: AppNavKey)`（直连 Navigation 3）、`External(val url: String)`（浏览器打开）与 `Notice(val message: String)`（交互提示）；
  3. **高杠杆执行层扩展**：在 `RePoiNavigator` 提供 `fun RePoiNavigator.navigate(target: RouteTarget, uriHandler: UriHandler, onMessage: (String) -> Unit)` 及其动作重载 `navigateAction` 与 `navigateUrl`，副作用收拢单点；
  4. **屏幕层参数解耦**：屏幕消除 10 个层层透传的特定回调，仅暴露单一语义化动作回调（动态卡片 `onActionClick: (ActionItem) -> Unit`，富文本 `onUrlClick: (String) -> Unit`），由导航声明入口 `*Entries.kt` 集中分发。
- 优点：
  - 杠杆率极高：消除 10 个向下击穿的透传回调；
  - 100% 纯测试友好：全量分发规则可在纯 JVM 环境（毫秒级）由 `AppRouteResolverTest` 全量覆盖，无需 Compose 环境与 Mock；
  - 路由拓扑一致：全面对齐 Navigation 3 原生单根栈架构；
  - 正则规则补全：URL 解析补全人物、厂商、作品、系列、用户等全量 8 维实体原生支持。
- 缺点：
  - 需要一次性清理 5 处核心 Screen 及其子卡片的回调签名。

## 决策结论
采用方案 B。
1. **模块与模型归宿**：
   - 模型文件：`shared/src/commonMain/kotlin/com/nobody/repoi/ui/navigation/RouteTarget.kt`
   - 解析器文件：`shared/src/commonMain/kotlin/com/nobody/repoi/ui/navigation/AppRouteResolver.kt`
   - 测试文件：`shared/src/commonTest/kotlin/com/nobody/repoi/ui/navigation/AppRouteResolverTest.kt`
2. **模型契约**：
   ```kotlin
   sealed interface RouteTarget {
       data class Internal(val key: AppNavKey) : RouteTarget
       data class External(val url: String) : RouteTarget
       data class Notice(val message: String) : RouteTarget
   }
   ```
3. **副作用执行层**：
   在 `NavExtensions.kt` 为 `RePoiNavigator` 提供 `navigate(RouteTarget, UriHandler, (String) -> Unit)`、`navigateAction(ActionItem, UriHandler, (String) -> Unit)` 与 `navigateUrl(String, UriHandler, (String) -> Unit)`。
4. **废弃与替代**：
   全量删除 `FeedActionDispatcher.kt` 与 `HpoiLinkDispatcher.kt`，将既有单测用例无损平移至 `AppRouteResolverTest.kt`。
5. **UI 层重构**：
   `HomeFeedScreen`、`FollowScreen`、`VendorDetailScreen`、`VendorActionsScreen`、`ZoneDetailScreen` 移除繁冗回调，统一采用 `onActionClick` / `onUrlClick`。

## 影响
- **积极影响**：
  - 全面清除各层 UI 的回调穿透（Callback Drilling），使卡片与页面恢复简洁，Compose Preview 更轻量；
  - 业务跳转规则与 Navigation 3 的 `AppNavKey` 单根栈模型强类型绑定，从根本上防止参数漂移；
  - URL 正则扩充覆盖全量实体详情，富文本内链原生跳转体验显著提升；
  - 测试架构简化，JVM 毫秒级单测即可验证复杂路由分发。
- **消极影响 / 迁移成本**：
  - 需同步调整涉及的 5 个 Screen 组件参数签名及其在 `*Entries.kt` 中的绑定调用；
  - 需同步删除旧单测并建立新单测。
