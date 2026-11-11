# ADR-0014: 封面新鲜度持久化缓存与列表前置自愈架构

## 状态
已采纳 (Accepted) - 2026-11-11

## 上下文与问题陈述
在 Hpoi 业务生态中，条目封面（手办 Hobby、相册 Album）在服务端更新或重新上传后，历史相对路径必然失效变更为新路径。然而浏览历史接口（`api/user/read/item/get`）等列表接口返回的 `cover` 仅为**产生行为那一刻的快照**。旧路径在 CDN 上已遭下线（返回 HTTP 404），而详情接口（`api/item/get-v2`、`api/album/detail`）每次均能返回当前最新真实路径。

PR#113 提出了基于 `PoiAsyncImage` 的 `onError` 回调触发按条单飞回源修复机制，但该方案依赖纯内存 LRU 维护（30 分钟 TTL）。随之暴露出以下两项关键体验痛点：
1. **冷启动闪烁**：应用重新冷启动后，内存清空，历史列表再次展示服务端过期快照，必须等待 Coil 加载遭遇 404 触发 `onError` 后再异步打详情接口修补，用户在首屏仍会看到破损图或重新加载闪烁；
2. **跨会话请求冗余**：即便用户昨日已修复过或刚刚进入过详情页，冷启动后依然会重复发起详情接口请求。

## 候选方案评估

### 方案 A：引入跨平台关系型数据库 (Room KMP / SQLDelight)
- **优点**：具备完善的表结构、索引与事务管理能力，方便未来扩展离线浏览模式。
- **缺点**：RePoi 目前跨 Android 与 Windows Desktop (JVM) 运行，整个代码库**零 SQLite 依赖**（仅依赖 `androidx.datastore`）。仅为存储 `(entityType, id) -> freshCoverPath` 的简单映射表引入完整 SQL 引擎属于严重过度工程（大炮打蚊子），需要承受 KSP 生成、平台 SQLite 驱动编译与单测环境隔离等沉重开销。

### 方案 B：纯响应式持久化（仅将内存缓存落盘，仍等 404 触发修复）
- **优点**：改动最小，直接将 `CoverFreshnessRepository` 的内存 map 序列化至本地。
- **缺点**：治标不治本。冷启动后列表依然先拿过期的服务端快照去渲染，Coil 依然发起无意义的 404 请求，未能消除首屏闪烁感。

### 方案 C (采纳)：双层自愈闭环——DataStore 内存镜像 + 前置主动重写 + 永久 LRU 穿透自愈
- **优点**：
  1. **零新增外部依赖**：复用既有的 `androidx.datastore:datastore-preferences`，配置独立的 `cover_freshness.preferences_pb` 文件；
  2. **读写分离与零 I/O 阻塞**：仓储初始化时从 DataStore 一次性预热至内存 `LinkedHashMap` 镜像。所有读操作（`resolveCover` / `freshCover`）均为微秒级同步内存读取，绝不阻塞 UI 渲染或主线程；写入操作即时更新内存，并由内部协程发起防抖异步落盘；
  3. **前置主动重写 (Proactive Rewrite)**：在 `UserHistoryRepository.getUserHistoryList()` 的 DTO 映射阶段直接匹配本地缓存，命中则直出最新封面。冷启动**零 404、零视觉闪烁、零网络请求**；
  4. **极简数据模型与永久有效**：条目封面设为永久生效（去除时间 TTL），仅受 `MAX_COVERS = 1000` LRU 容量上限约束（持久化 JSON 文件严格锁死在 ~40KB 以内），数据模型仅为纯粹的 `Map<String, String>`；
  5. **失效穿透自愈**：当已缓存封面在服务端二次失效导致 404 时，`onCoverLoadFailed` 传入 `forceRefresh = true` 强制穿透缓存发起详情单飞请求，拉取最新路径后原子覆写本地永久缓存。负缓存（服务端无封面）保持纯内存 10 分钟不落盘。

## 决策结论

### 1. 存储底座与仓储架构深化 (`CoverFreshnessRepository`)
- 新增 `DataStoreFiles.COVER_FRESHNESS = "cover_freshness.preferences_pb"`，并在平台 Koin 模块中注入专用的 `DataStore<Preferences>`；
- `CoverFreshnessRepository` 维护纯内存 `covers: LinkedHashMap<String, String>` 镜像，冷启动时触发一次性预热；
- 暴露同步查询接口 `fun getCachedCover(id: Long, type: EntityType): String?`，支持列表同步直接重写；
- 写入接口（`rememberFreshCover` 与 `fetch` 成功）即时更新内存镜像，并通过 `Channel` / 协程以 500ms 防抖写入 DataStore；
- `freshCover(id: Long, type: EntityType, forceRefresh: Boolean = false)` 支持强制穿透回源。

### 2. 前置主动重写内聚于数据仓储 (`UserHistoryRepository`)
- 在 `UserHistoryRepository.getUserHistoryList()` 解析出 `readList` 后，立即调用 `coverFreshnessRepository.getCachedCover` 进行前置主动替换；
- ViewModel、`PagedListLoader` 与 UI Composable 始终接收干净的领域模型，完全屏蔽快照替换细节。

### 3. 响应式自愈与兜底防风暴保留
- 维持 PR#113 建立的单飞请求合并（Single-Flight）、在途信号量限流（Semaphore ≤ 3）、实体单会话失败重试上限（≤ 2 次）机制；
- 封面加载失败（404）时，UI 调用 `onCoverLoadFailed` → `freshCover(forceRefresh = true)` 触发强制回源自愈。

## 架构收益与影响
1. **冷启动体验质变**：曾经访问过的历史条目在冷启动后直接以最新封面呈现，彻底消除了列表加载时的 404 错误与占位闪烁；
2. **极大降低服务端负载**：将绝大多数历史列表浏览的详情回源请求收敛至零；
3. **架构极简且跨平台安全**：规避了关系型数据库的引入成本，维持了 KMP 纯净的架构与测试隔离性。
