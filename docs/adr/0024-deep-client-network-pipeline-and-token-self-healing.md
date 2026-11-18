# ADR-0024: 客户端网络管道深模块化、响应解包与令牌失效自愈下沉

## 状态
已采纳 (Accepted) - 2026-11-18

## 上下文与问题陈述

在审查报告（Candidate 2: Sink HTTP unwrap and token self-healing into a deep client pipeline）与存量代码扫描中，RePoi 数据层存在严重的职责泄漏与鉴权自愈缺失：

1. **仓储层职责严重泄漏与退化为浅传递器**：
   - 现存 15+ 个仓储直接暴露在 HTTP 传输信封 `CommonResponse<T>` 之下；
   - 6 个仓储（`CollectRepository`、`CommentRepository`、`FollowRepository`、`ForumRepository`、`MessageRepository`、`SettingsRepository`）为了在接口失败时触发 4001 令牌失效，强行在构造函数中注入 `UserSession`，并在每个请求处重复编写在途凭据捕获与 `notifyTokenExpired(snapshotToken)` 代码；
   - 10 个条目与详情仓储（如 `HomeRepository`、`WorksDetailRepository`、`CharacterDetailRepository` 等）完全遗漏了 4001 令牌失效校验。更严重的是，`WorksDetailRepository` 等仓储直接使用 `if (response.isSuccess) response.data?.list ?: emptyList() else emptyList()` 将 4001 及服务端异常静默吞没为空列表，导致登录态失效后用户进入假象空白幽灵态。
2. **过渡样板与散装状态码判断蔓延**：
   - 存在 `ResponseUnwrap.kt`（45 行）等全局散装扩展函数，且各仓储中存在 ad-hoc 字符串匹配（如 `msg?.contains("找不到该评论")`）与非统一的异常抛出；
   - Hpoi 原生服务端存在状态码特化行为（`code in (200, 0, -1)` 均为业务成功，回帖接口成功返回 `-1`，评论不存在返回 `-9007`），缺乏单点收敛。

依据深模块设计哲学（Deep Module：简单的接口隐藏巨大的内部复杂性），网络客户端应当是一道坚固的深接缝，对外只交付洁净的领域/业务数据模型 `T`，并在内部自主闭环反序列化解包、异常分类映射与 4001 令牌失效自愈。

## 候选方案评估

### 方案 A：在 `HpoiApi` 之上包装新客户端 `HpoiClient`
- **做法**：保留现有 `HpoiApi` 返回 `CommonResponse<T>`，在其上新增一套 `HpoiClient` 包装类提供解包与自愈。
- **优点**：现有 `HpoiApi` 接口无需重构。
- **缺点**：制造了典型的浅层适配器（`HpoiClient` ➔ `HpoiApi` ➔ `HttpClient`），间接层冗余，且原 `HpoiApi` 的信封泄露漏洞依然存在。

### 方案 B：Ktor 引擎级管道插件拦截 (Ktor Response Pipeline)
- **做法**：在 Ktor `HttpClient` 层通过 `HttpResponseValidator` 或自定义插件统一拦截反序列化。
- **优点**：下沉到 HTTP 引擎底层。
- **缺点**：Hpoi 接口存在 FastJson `@id` / `@ref` 循环引用解析（`decodeRefAware`）以及特化的成功码判定（如 `-1` 判定为成功）。在无具体 DTO 上下文的纯 Ktor 插件层直接穿透包装容易破坏 `JsonRefResolver` 的生命周期，难以兼顾泛型擦除与静态强类型推导。

### 方案 C：直接将 `HpoiApi` 升级为核心深模块接缝（采纳）
- **做法**：保持 `HpoiApi` 作为网络请求门面，内部统合 `decodeRefAware`、请求 Attribute 快照比对、状态码白名单校验、异常分流抛出与 4001 令牌自愈回调；对外签名彻底抹去 `CommonResponse<T>`，直接返回洁净载荷 `T` 或 `Unit`。
- **优点**：接缝极窄且内聚，消灭多层包装开销；仓储层从根本上杜绝感知传输信封；集中维护 91 个端点的空安全与异常语义。
- **缺点**：需要对 `HpoiApi` 的公开方法签名进行集中批量改造。

## 决策结论

### 1. `HpoiApi` 作为核心深模块接缝，对外提供洁净载荷契约
`HpoiApi` 对外方法签名彻底移除 `CommonResponse<T>`，改为直接返回洁净业务载荷：
- 业务查询接口直接返回 `T`（如 `suspend fun getActionList(...): ActionListData`）；
- 状态更新或写操作接口直接返回 `Unit`（如 `suspend fun updateMysqlSetting(...): Unit`）；
- 内部收敛为三套私有解包模板：
  - `unwrapData<reified T>()`：成功且 `data != null` 返回 `T`；若 `success == true` 但 `data == null` 则抛出 `ApiException(code, "服务端响应数据为空")`，建立空安全防线；
  - `unwrapUnit()`：写操作断言成功即返回 `Unit`；
  - `unwrapDataOrNull<reified T>()`：显式允许空返回接口返回 `T?`。

### 2. 在途凭据快照与 4001 令牌失效自愈解耦
为杜绝网络层与 `UserSession` 的双向依赖，并防止多账号切换及候选验权误伤：
1. **快照绑定**：`CommonParamsPlugin` 注入公共参数时，在 `HttpRequestBuilder.attributes` 写入在途凭据快照 `PoiRequestTokenAttributeKey`；
2. **惰性回调注入**：`HpoiApi` 构造接收函数式接口 `onTokenExpired: suspend (triggeredToken: String) -> Unit = {}`，由 Koin 单例中惰性连接至 `UserSession.notifyTokenExpired(token)`；
3. **自愈触发**：遇 4001 时自动提取在途快照并调用回调，由 `UserSession` 原子验证在途快照是否匹配当前活跃会话后清空凭据；
4. **候选验权旁路（ADR-0005 隔离）**：`getUserSelfWithToken(candidate)` 显式附加 `BypassTokenSelfHealingAttributeKey = true`，候选令牌验权失败时仅抛出异常，绝不触发当前设备的活跃会话自愈。

### 3. 业务异常体系与边缘错误码（Quirks）收敛
1. **成功白名单**：统合 `code in (200, 0, -1)` 为服务端业务成功判定；
2. **特定业务异常分流**：
   - 评论未命中（`code == -9007` 或 "找不到该评论"）由网络管道统一抛出专用异常 `CommentNotFoundException : ApiException`；
   - 仓储层（`CommentRepository`）捕获 `CommentNotFoundException` 干净转换为 `Result.success(emptyList())`，彻底移除散装字符串匹配；
3. **统合异常抛出**：其余业务错误统一抛出强类型 `ApiException(code, msg)`。

### 4. 仓储层（Repository）职责净化与代码坏味道清除
1. **物理删除样板**：物理删除 `shared/src/commonMain/kotlin/com/nobody/repoi/data/repository/ResponseUnwrap.kt`；
2. **剥离会话注入**：从 `CollectRepository`、`CommentRepository`、`FollowRepository`、`ForumRepository`、`MessageRepository`、`SettingsRepository` 中彻底移除 `UserSession` 构造参数；
3. **修复静默吞没**：条目与详情仓储（如 `WorksDetailRepository` 等）不再静默吃掉错误码，4001 均由深管道自动自愈；
4. **契约全局归一**：所有仓储层挂起方法统一遵循 `suspend fun getFoo(...): Result<T> = runCatching { api.getFoo() }` 暴露错误边界。

### 5. `MockApi` 测试脚手架平滑演进与取证增强
1. `MockApi.api(dispatcher)` 默认向 `HpoiApi` 传入内部自愈收集器，保证存量仓储与 API 单测零改动直接兼容；
2. 为 `MockApi` 新增 `assertTokenExpiredNotified(expectedToken: String)` 与 `assertNoTokenExpiredNotified()` 断言方法，使得单测可独立校验网络管道的 4001 自愈行为。

## 影响

- **积极影响**：
  - **高内聚自治**：4001 令牌自愈由客户端网络管道闭环，全工程 15+ 个仓储彻底告别鉴权状态管理与遗漏风险；
  - **代码大精简**：删除 45 行全局顶层样板 `ResponseUnwrap.kt`，从 6 个重度仓储中剥离 `UserSession` 依赖，消灭数十处冗余的 `isSuccess` 与 null 检查；
  - **安全与可测性**：通过在途快照杜绝并发切换账号误伤，通过验权旁路捍卫 ADR-0005，通过 `MockApi` 断言增强自动化测试能力。
- **消极影响 / 迁移成本**：
  - `HpoiApi` 中 91 个方法签名由 `CommonResponse<T>` 改造为 `T`，所有仓储调用点需做相应的解包语法清洗（去除 `.data?.list ?: emptyList()` 等过渡代码）。
- **相关文档与参考**：
  - 前序决策：[ADR-0005 登录令牌导入导出与 Token 登录原子验权](0005-token-auth-import-export.md)；
  - 架构审查：`architecture-review-20261118-105430.html` (Candidate 2)；
  - 术语表：[GLOSSARY.md](../../GLOSSARY.md)。
