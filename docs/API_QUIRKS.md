# Hpoi API 特异性与反序列化避坑手册 (API Quirks)

> 本文档记录 Hpoi 原版后端接口的异形字段、多态类型与逆向踩坑事实。  
> 智能体在新增 DTO 模型或编写网络请求时**必须先对照本手册**，避免因字段类型假设引发运行时 JSON 解析崩溃或空指针异常。

---

## 1. 收藏接口 `common-v3` 的 `state` 字段多态类型陷阱

- **涉及接口**：`POST api/collect/common-v3` 等通用收藏状态检索接口。
- **踩坑现象**：
  - 当查询**作品类条目（Works）**时，服务端返回的 `state` 为**整型数值**（`Int`，如 `0` 未收藏，`1` 已想看，`2` 看过）；
  - 当查询**手办/周边条目（Hobby）**时，服务端返回的 `state` 为**字符串**（`String`，如 `"care"`, `"want"`, `"buy"`, `"resell"`, `""`）。
- **严重后果**：若在通用 DTO 中硬编码为 `val state: String` 或 `val state: Int`，在跨分类解析时会触发 Kotlinx Serialization 的 `SerializationException: Expected string/int, but found ...`，导致整页数据反序列化失败直接走入 Error 态。
- **推荐解法**：
  - 在 DTO 中使用 `JsonPrimitive` 或封装宽松反序列化适配器；
  - 在 Repository 层将状态归一化为领域枚举 `CollectState`。

---

## 2. 相册图片接口 `api/collect/pic-v2` 缺少绝对 URL 字段

- **涉及接口**：`POST api/collect/pic-v2`。
- **踩坑现象**：
  - 与其它详情接口不同，该接口的条目响应结构**仅包含 `path` 字段**（例如 `/album/2023/10/xxx.jpg`），**完全不返回 `picUrl` 或 `smallPicUrl`**。
- **严重后果**：若界面层或仓储层臆测存在 `item.picUrl`，会导致反序列化丢失或图片渲染为空白。
- **推荐解法**：
  - DTO 明确声明 `val path: String`；
  - 映射为领域模型时，统一通过 `CdnUrlUtils.getCoverSrUrl(path)` 或 `CdnUrlUtils.getPicSmallUrl(path)` 拼接完整 CDN 协议头。

---

## 3. 双重 ID 体系混淆：业务号 (`itemId`) vs 节点号 (`nodeId` / `id`)

- **涉及接口**：`GET api/works/get`、`POST api/comment/get`、`GET api/works/character/home/get`、`POST api/hobby/query-v2`。
- **踩坑现象**：
  - Hpoi 内部存在两套实体主键体系：
    1. **业务 ID (`itemId` / 业务号)**：例如某部 FGO 作品的业务号为 `28`；
    2. **节点 ID (`nodeId` / 平台统一节点号)**：例如该作品在全局节点表中的 ID 为 `2706`。
  - **接口入参要求不一致**：
    - 主体查询 `GET api/works/get?worksId=` 与登场角色查询 `GET api/works/character/home/get?worksId=` **必须使用业务号 (`itemId`)**，使用节点号会返回空列表；
    - 评论接口 `POST api/comment/get {id}` 与周边接口 `POST api/hobby/query-v2 {works}` **必须使用节点号 (`nodeId`)**。
- **推荐解法**：
  - 路由入参统一透传上游带过来的 `itemId`；
  - 详情页首屏加载主体接口后，提取响应中的 `works.id`（即 `nodeId`），再并发调度评论与周边子接口。

---

## 4. 空列表与状态码契约

- **空数组与空对象的混乱**：原版后端在无数据时，某些接口返回 `[]`（空数组），某些接口返回 `""`（空字符串）或 `{}`（空对象）。在 DTO 中必须将非确定结构声明为可空类型（`List<T>? = emptyList()`），并在 Ktor ContentNegotiation 中配置 `ignoreUnknownKeys = true`。
- **状态码契约**：Hpoi 原版判定成功以 `success: true` 为准。大部分接口返回 `code == 200` 或 `0`，但回帖讨论接口（`api/discuss/get`）成功时返回 `code: -1`。统一在 `CommonResponse.isSuccess` 中放行 `code == 200 || code == 0 || code == -1`；当 `code == 4001` 时为登录态失效，必须派发认证过期通知重登。

---

## 5. 动态信息流游标时间戳多态与翻页防死锁

- **涉及接口**：`GET api/user-action/list-v3`。
- **踩坑现象**：
  - `actionDate` 字段在不同数据源下返回不同格式：多数条目返回毫秒整型时间戳（如 `1792632679636`），部分条目返回日期格式化字符串（`yyyy-MM-dd HH:mm:ss`）；
  - 若单纯采用日期格式化工具解析毫秒时间戳字符串，会捕获异常导致游标降级为 `0L`；
  - 一旦翻页请求入参传递 `lastTime = 0L`，服务端将重复返回第一页数据，引发瀑布流不断追加重复数据。在 Compose 中会直接触发 `Key was already used` 崩溃；在数据量较多的专区，则表现为反复追加第一页导致向上滚动时永远滚不到顶。
- **推荐解法**：
  - 游标解析统一优先使用 `dateStr.toLongOrNull()`，失败时再降级为日期格式化；
  - 翻页拉取结果统一做列表项主键去重（`distinctBy { "${it.id}_${it.actionDate}" }`）；
  - 当下一页游标未递增（`newLastTime <= 0L || newLastTime == state.lastTime`）或无新项产出时，必须坚决置 `hasMore = false`，防止死锁与循环拉取第一页。

---

## 6. 专区详情接口（`api/zone/info`）数据结构与 Tabs 数组多态

- **涉及接口**：`GET api/zone/info`。
- **踩坑现象**：
  - 生产接口下发的 `tabs` 字段并非普通的字符串数组（如 `["action", "album"]`），而是包含 `{ count, type, title }` 的复杂对象数组（如 `[{"type": "action", "title": "动态", "count": 44}]`）；
  - 若将 DTO 直接声明为 `List<String>`，Kotlinx Serialization 会抛出反序列化类型不匹配异常（`Expected start of the string, but had START_OBJECT`），导致整个请求失败，进而使专区详情页无法获取专区真实封面（`cover`/`webCover`）与图标（`icon`），降级为占位符矢量图与空背景；
  - 专区简介在后端存在 `description` 与 `desc` 两个别名字段，横幅存在 `cover` 与 `webCover` 双字段回退。
- **推荐解法**：
  - 定义 `ZoneTabItem` 并配套自定义反序列化器，同时兼容字符串基本类型（`JsonPrimitive`）与对象类型（`JsonObject`），并且能对空字符串 `""`、空数组 `[]` 或缺失字段安全返回空列表；
  - 实体模型中同时声明 `cover` 与 `webCover`，UI 层渲染封面优先取 `cover`，若无则回退至 `webCover`；
  - 专区简介统一支持 `description` 与 `desc`。

---

## 7. 讨论帖图片路径仅部署单规格 `posts/n/`，不存在 `posts/raw/`

- **涉及接口**：`GET api/posts/search`（条目中的 `pics` 字段）。
- **踩坑现象**：
  - 与手办条目封面（`cover/raw/`）和相册相片（`pic/raw/`）不同，Hpoi 服务端对讨论帖配图只部署了 `posts/n/` 路径，**完全不存在 `posts/raw/` 独立目录**；
  - 若臆测存在 `posts/raw/` 原图并在全屏图片查看器中加载，服务端会直接返回 `404 Not Found`，导致全屏画廊一直白屏加载失败。
- **推荐解法**：
  - 讨论帖缩略图与大图画廊统一使用 `posts/n/` 路径（`CdnUrlUtils.getPostsUrl` 与 `CdnUrlUtils.getPostsRawUrl` 一致指向 `posts/n/`）。

---

## 8. 讨论帖双重 ID 与回帖契约踩坑

- **涉及接口**：`GET api/posts/info-v2`、`GET api/posts/info`、`POST api/discuss/get`、`POST api/discuss/add`、`POST api/posts/del`。
- **踩坑现象**：
  - **主楼接口强校验 `itemId`**：`api/posts/info-v2` 强制要求传入合法 `itemId`（4~5位流水号，如 `5450`）。若将全局节点号（如 `40819834`）误传给 `itemId`，服务端直接返回 `code: -9005, msg: "找不到该帖子，可能已被删除"`；若省略 `itemId` 只传 `nodeId`，服务端返回 `code: -16, msg: "参数错误！itemId不能为空"`。
  - **基础接口支持多态反解**：`GET api/posts/info` (v1) 支持以 `nodeId` 查询，并在返回的 `posts` 中同时携带 `id`（nodeId）与 `itemId`。
  - **讨论接口强制绑定 `itemNodeId`**：`POST api/discuss/get` 和 `POST api/discuss/add` 必须传入 `itemNodeId = nodeId`（8位数全局节点号）；若误传 `itemId`，服务端返回 0 条回帖。
  - **讨论接口成功状态码特异性**：`api/discuss/get` 成功获取回帖列表时，返回 `{ success: true, code: -1, data: { discussList: [...] } }`，`code` 为 `-1` 而非 `200`。
- **推荐解法**：
  - 路由传递与卡片跳转时同时附带 `itemId` 与 `nodeId`（`item.itemId` 与 `item.id`）；
  - `PostRepository.getPostDetail` 采用双向容错解析：优先调用 `info-v2?itemId=`，若传入为 `nodeId` 或 v2 失败则降级调用 `info?nodeId=` 反解 `itemId` 并二次补齐 `relateItem`；
  - `PostDetailViewModel` 绑定 `nodeId` 调度 `getDiscussList` 与 `addDiscuss`。

---

## 9. Hpoi BaseURL 动态解析与图片 CDN 域名契约

- **涉及类与配置**：`POST api/common/serverSetting`。
- **踩坑现象**：
  - Hpoi 开机首屏通过 `POST api/common/serverSetting` 下发动态域名配置：
    - 主 BaseURL 默认硬编码为 `https://www.hpoi.net.cn/`，备用为 `https://www.hpoi.net/`；
    - 资源与图片 CDN 默认指向 `https://rfx.hpoi.net/gk/`；
    - 商城周边根路径为 `mall_root`。
  - 公共请求参数（Ktor 插件）：自动包含 `utoken`、`platform=android`、`appVersion` (versionCode `200133`)、`channel` (`official`)、`machine` (`SM-S9280`)、`system` (API Level `34`)。
  - 会话失效判定：网络层统一判定 `code == 4001`，命中即清空本地 `utoken` 并通知重登。
- **推荐解法**：
  - 网络基类统一配置默认 BaseURL `https://www.hpoi.net.cn/`；
  - 图片与静态资源 CDN 工具类 `CdnUrlUtils` 确保指向高可用域名；
  - 网络插件在接收到 `code == 4001` 时发布全局认证失效事件，由 `TokenManager` 统一切断。
