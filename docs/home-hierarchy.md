# 首页三级信息架构设计规格

> 状态：已实施（#151–#162 按票落地；聚合参数与上线矩阵以 [聚合接口抓包验证报告](research/aggregate-api-verification.md) 为准）
> 关联决策：[ADR-0022](adr/0022-home-zone-three-level-and-selector.md)（取代 ADR-0020 的「专区分类栏」条款）
> 逆向基准：Hpoi 2.43（`net.hpoi`, versionCode 200135）
> 范围：设计规格与实施后事实校正，不含源码改动

---

## 0. 一页速览

```
┌──────────────────────────────────────────────┐
│ 顶栏： [ 当前专区名 ▾ ]      [搜索] [讨论板] [消息] │  ← 一级：专区切换器（抽屉/下拉）
├──────────────────────────────────────────────┤
│ 动态 | 分享 | 讨论 | 产品 | 二手 | 厂商 | …    │  ← 二级：子板块 PrimaryScrollableTabRow
├──────────────────────────────────────────────┤
│ 推荐 | 评论 | 全部情报 | 制作 | 更图 | …       │  ← 三级：板块自带筛选层（可折叠）
├──────────────────────────────────────────────┤
│ 内容区（复用专区详情的板块实现）                  │
└──────────────────────────────────────────────┘
```

三条基本规则：

1. 一级不是 Tab，是**切换器**（菜单/抽屉），因此只有一级 Tab（二级板块），符合 M3「Tabs 最多两级、第三级禁用 Tab」。
2. 二级板块 = **服务端下发 ∩ 客户端已实现**；聚合态（全部/关注）用客户端固定列表。
3. 三级筛选**按板块定义**，不做全局统一行。

---

## 1. 逆向事实（Hpoi 2.43）

> 证据均来自外部反编译产物 `hpoi reverse artifacts/src/hpoi/`（jadx，包声明 `net.hpoi.*`）。本仓库只在设计文档中引用，不复制其代码。

### 1.1 三层数据流

```
一级  api/zone/home-tabs（GET，无业务参数）
      → 全部(all) / 关注(follow) / homeZones[] → z{nodeId}，字段 name/tabKey/zoneIds/icon
二级  单区：api/zone/info?nodeId= → zone.tabs[].type（服务端顺序，客户端白名单 10 项）
      聚合：客户端固定 10 项（全部）；关注态再隐藏讨论/产品/二手，剩 7 项
三级  动态：9 个 subType 常量单选 Chip → api/user-action/list-v3
      其它板块：各自的 order/category/筛选弹窗
```

### 1.2 三级取值

- 一级 `tabKey`：`all` / `follow` / `z<nodeId>`；`zoneIds`：空 / 关注集合逗号串 / 单 nodeId。
- 二级 `type`（12 项）：`action`(动态)、`album`(分享)、`posts`(讨论)、`hobby`(产品)、`resale_item`(二手)、`shop_item`(商品)、`company`(厂商)、`series`(系列)、`works`(作品)、`charactar`(角色)、`person`(人物)、`community`(社群，仅单区)。
- 三级动态 `subType`（9 项，顺序即原版顺序）：

| subType | 2.43 文案 | RePoi 现状键/文案 |
|---|---|---|
| `recommend` | 推荐 | `recommend` 推荐 |
| `comment` | 评论 | `comment` 评论 |
| `allInfo` | 全部情报 | `allInfo` 全部情报（旧持久化键 `all` 读入时映射） |
| `confirm` | 制作 | `confirm` 制作 |
| `official_pic` | 更图 | `official_pic` 更图 |
| `preorder` | 开订 | `preorder` 开订 |
| `delay` | 延期 | `delay` 延期 |
| `release` | 出荷 | `release` 出荷 |
| `reorder` | 再版 | `reorder` 再版 |

### 1.3 交互与状态（原版）

- 一级：单击切页；再点当前页 → 全部/关注刷新流、具体专区打开专区详情页；**无长按**；编辑（隐藏/排序）走「+浏览 → 首页设置」。
- 二级：ViewPager2 + FragmentStateAdapter，每页为惰性包装 Fragment；切换不重建已存活页。
- 三级：动态筛选条 `AutoHideFilterBarLayout`，累计滚动 8dp 触发、150ms 动画收起，到顶强制显示。
- 持久化：一级 `home_zone_tab`；二级 `home_zone_content_tab`＝`{tabKey}|{type}`；三级 `home_sub_type_code`（全局一份）。冷启动**不恢复「关注」**，一律回落「全部」。
- 深链 `hpoi://…/home` 不带参数，只落到首页，具体层级由上述持久化恢复。

### 1.4 与原版一致的实现约束

- 聚合态各板块共用同一套包装层：按作用域给出 zone 参数——`all`→省略、`follow`→关注专区逗号串、单区→`<nodeId>`。
- 参数事实以抓包实测为准（`docs/research/aggregate-api-verification.md` §2.1/§2.2/§3）：
  - 动态只用 `zoneIds`（单区额外重复传 `zoneNodeId`）；
  - 分享 / 二手 / 实体 5 类用 `zones`；实体 5 类另可用单值 `nodeId`，且**不得下发 `homeScope`**；
  - 讨论的专区过滤参数是 **`zoneNodeId`（String、仅单值）**，`zones` 参数被服务端完全忽略；关注聚合无多专区通道，必须隐藏该板块；
  - 产品 `zones` 与非空 `homeScope` **互斥**：只要 `homeScope` 非空，服务端会忽略 `zones` 改走服务端作用域（空串不触发）；单区只传 `zones`；
  - 相册 / 二手忽略 `homeScope`；`zones` 多值语义按接口不同（分享/实体=并集、产品/二手=交集、讨论无参数），不能一概而论。

---

## 2. 目标架构

### 2.1 层级职责

| 层级 | 交互基元 | 数据来源 | 职责 |
|---|---|---|---|
| 一级 专区 | 顶栏标题 + 切换器（底部抽屉 / 宽屏下拉） | `ZoneRepository`：`focusZonesFlow` + `settingsFlow`（复用现有 `buildCategories`） | 识别当前专区、切换专区、进入专区广场/首页设置 |
| 二级 子板块 | `PrimaryScrollableTabRow` | 单区 `zone/info.tabs` ∩ 白名单；聚合固定 10 项（关注态 7 项） | 切换内容形态 |
| 三级 板块筛选 | 板块自有控件（Chip / 排序菜单 / SegmentedButton / 高级筛选抽屉） | 各板块常量或服务端分类 | 过滤/排序当前板块内容 |
| 内容 | 各板块内容组件（复用专区详情实现） | 各业务仓储 | 分页列表、空态、错误态 |

### 2.2 Compose 结构（示意）

```
HomeScreen
├─ TopAppBar(title = ZoneSwitcherAnchor, actions = 搜索/讨论板/消息)
├─ HeaderStack（高度栈统一计算，避免拼死的 totalHeaderHeight）
│  ├─ PrimaryScrollableTabRow(板块)
│  └─ BoardFilterBar（可折叠，测量高度）
├─ PullToRefreshBox
│  └─ ZoneBoardPane(当前板块)         ← 与专区详情共享
└─ FAB（回顶并刷新，保留现状）
```

`ZoneSwitcherAnchor` 紧凑屏展开 `ModalBottomSheet`，≥840dp 展开锚定 `DropdownMenu`；两者共用同一份「切换器数据模型与选中态」。

### 2.3 复用边界（明确）

- **直接复用**：`FeedCategory` 与 `buildCategories`（一级数据与排序/隐藏规则）、`ZoneTabsSettings`、`zone/info` 的 `tabs` 解析、专区详情各板块的分页 Loader（`PagedListLoader` / `KeyedListLoader` / 实体分块加载）与筛选状态字段、卡片基元（`PoiPosterCard` / `PoiListCard` / `PoiCaptionGridCard`）、`AdaptiveChunkedGrid`、`PoiLoadingView`。
- **必须抽取**：`ZoneDetailScreen` 内联的板块内容区、各板块筛选条与搜索/控制栏 → 抽为可参数化的 `ZoneBoardPane`（板块类型、查询作用域、筛选状态、滚动状态）。
- **必须改造**：首页状态从单一 `HomeFeedUiState` 改为「当前专区 + 当前板块 + 各板块独立状态」；头部高度栈由固定两层改为可配置。
- **不复用**：首页现有「单一动态流 + 单一筛选」状态机（无法承载多板块）。

---

## 3. 交互规格

### 3.1 顶栏

- 标题区：`Text(当前专区名, 单行省略)` + 展开箭头，整块可点击（`role = Button`，`contentDescription = "切换专区"`），最小触控 48dp。
- 动作区：`[搜索] [讨论板] [消息]`：
  - 搜索：进入搜索落地页，`initialCategory = if (nodeId == 0) 10000 else nodeId`（保留现状透传）；
  - 讨论板、消息（含未读 Badge）：保持 ADR-0020 结论。
- 顶栏保持 `WindowInsets.statusBars`，滚动时保持不变（现状）。

### 3.2 专区切换器

**结构（纯文本 Chip，无图标）**

```
快捷
  [全部] [关注]                ← 单选 Chip（FlowRow，整组单选）
我的专区                       ← 按 ZoneTabsSettings.sort（默认/最近/自定义）排序，hiddenZoneIds 生效
  [{专区名}] …
───────────────────────────
[浏览全部专区] [首页设置]        ← 主 / 次按钮，分别进入 ZoneBrowse / ZoneSettings
```

- 选中态：Chip 选中容器（tonal）+ **文字加粗 + `colorScheme.primary`**；不使用勾选图标、不使用专区区图标。
- 分组标题：`labelLarge` + `onSurfaceVariant`。
- 列表项：M3 `FilterChip`；视觉容器 32dp，触控目标由 `minimumInteractiveComponentSize` 保证 ≥48dp；点击 = 切换并关闭切换器。
- 「关注」在未登录或关注为空时仍可选择：进入空态（引导去浏览专区），不发请求（保留现有短路逻辑）。
- 切换器打开方式：点击顶栏标题区；关闭：选择条目、点遮罩、返回手势。
- 无障碍：每组 Chip 外层 `selectableGroup()`，读屏按单选组朗读选中态；选中项由 M3 `FilterChip` 提供选择语义，
  调用侧另声明 `Role.RadioButton`（M3 Chip 内部角色为 Checkbox，合并结果以系统实际读屏为准，当前未新增 UI 断言）。
- `ModalBottomSheet` / `DropdownMenu` 自带焦点圈定与遮罩关闭，不再额外把焦点移到分组标题。

**自适应**

| 窗口宽度 | 呈现 |
|---|---|
| < 840dp | `ModalBottomSheet`（纯文本，最大高度约 70%，超出内部滚动） |
| ≥ 840dp | 锚定标题的 `DropdownMenu`（同内容、同顺序） |

### 3.3 子板块 TabRow

- 组件：`PrimaryScrollableTabRow`（M3 稳定 API；一级不是 Tab，故二级承担 primary 语义），`edgePadding = 16dp`，沿用主题指示器。
- 单区：`zone/info.tabs` 的 `type` ∩ 白名单（10 类），保持服务端顺序；服务端可能下发白名单外的 `shop_item`/`community`，客户端直接过滤（白名单无需扩展）；为空时隐藏 TabRow 并展示板块空态。
- 聚合（全部/关注）：客户端固定列表，全部 10 项，顺序：`action, album, posts, hobby, resale_item, company, series, works, charactar, person`；关注聚合仅 7 项（隐藏讨论 `posts`、产品 `hobby`、二手 `resale_item`，见 §4 上线矩阵）。
- 切换板块：不重建页面；内容区切换并恢复该板块的滚动位置与筛选状态（内存）。
- TabRow 常驻吸顶，不随滚动折叠。

### 3.4 板块筛选层

| 板块 | 三级控件 | 请求参数 |
|---|---|---|
| 动态 `action` | 9 项单选 Chip（文案/顺序对齐 2.43） | `subType` |
| 分享 `album` | 排序 Chip（推荐/最新创建/最近更新/图片数量/总热度/一天热度）+ 分类入口 | `order`、`category` |
| 讨论 `posts` | 排序 Chip（回复/新贴/推荐） | `order`（无分类过滤：原版无分类入口、接口忽略分类参数，见 [抓包报告附录 C](research/aggregate-api-verification.md#附录-c讨论板块分类参数探测)） |
| 产品 `hobby` | 排序菜单（7 天热度/1 天热度/总热度/最新入库/发售时间/综合评分）+ 年份 + 月份 + 分类级联 + 高级筛选抽屉 | `order`、`releaseYear/Month`、`category`、`r18/sex/scale`；关注聚合当前隐藏（`homeScope=follow` 需登录态复验） |
| 二手 `resale_item` | 出售/求购 `SegmentedButton`（2 项单选视图切换）+ 成色 Chip | `type`、`puritys` |
| 实体 5 类 | 暂无筛选（保留后续扩展位） | — |

- 折叠行为沿用现状：随列表滚动收起，回到顶部强制展开；切换板块/筛选时复位到顶部。
- 筛选状态：动态筛选全局记忆（与 2.43 一致）；其余板块按板块记忆在内存中，页面重建后回到默认值。

### 3.5 内容区

- 复用 `ZoneBoardPane`：按板块渲染瀑布流/网格/列表，沿用现有 `PagedListLoader` 系列与游标规则。
- 列表项 `key` 与 `contentType` 必填（混合类型列表必须给 `contentType`）。
- 瀑布流列数沿用 `rememberFeedStaggeredGridColumns()` 与各板块既有规则（网格用 `AdaptiveChunkedGrid`）。

### 3.6 空态 / 错误态 / 加载态

- 首屏加载：`PoiLoadingView`（禁骨架屏，ADR-0018）。
- 板块为空：卡片式空态 + 「刷新看看」。
- 板块错误：卡片式错误 + 「重新加载」。
- 「关注」聚合为空：空态文案引导「去专区广场关注感兴趣的专区」，不发起网络请求（现状短路）。
- 聚合态某板块经验证不可用：**直接从 TabRow 中隐藏**，不展示「暂不支持」占位；当前关注态隐藏讨论/产品/二手，商品/社群在任何作用域都不出现。

### 3.7 手势与滚动

- 下拉刷新：刷新当前板块内容与未读数（现状）。
- FAB：回顶并刷新当前板块（现状）。
- 切换专区/板块/筛选：滚动复位到顶、筛选条展开。
- 状态保存：每个板块独立滚动位置（内存），跨“专区↔板块”切换不丢。

---

## 4. 板块矩阵

| 板块 | type | 内容形态 | 接口 | 单区筛选 | 聚合参数 | RePoi 现状 |
|---|---|---|---|---|---|---|
| 动态 | `action` | 瀑布流卡片 | `GET api/user-action/list-v3`（`lastTime` 游标、`subType`、`zoneNodeId`、`zoneIds`） | 9 Chip | 全部：省略 zone 参数；关注：`zoneIds=<逗号串>`（并集） | 已实现（三作用域可用） |
| 分享 | `album` | 相册网格 | `POST api/album/list`（`order`、`zones`、`category`、`page`） | 排序 + 分类（分类入口仅单专区） | 全部：省略 `zones`；关注：`zones=<逗号串>`（并集） | 已实现（三作用域可用） |
| 讨论 | `posts` | 帖子列表 | `GET api/posts/search`（`order`、**`zoneNodeId`（String、仅单值）**、`keyword`、`page`；`itemType`/`category`/`categoryId`/`zoneCategoryId`/`plateId` 实测被忽略，见 [附录 C](research/aggregate-api-verification.md#附录-c讨论板块分类参数探测)） | 排序（无分类过滤） | 全部：省略 `zoneNodeId`；**关注：无多专区通道，隐藏该板块** | 已实现（全部/单区可用，关注态隐藏） |
| 产品 | `hobby` | 网格/列表 | `POST api/hobby/query-v2`（`order`、`zones`、`category`、`releaseYear/Month`、`r18/sex/scale`、`page`） | 排序 + 年份/月份 + 分类 + 高级筛选 | 全部：省略 `zones`；单区：`zones=<nodeId>` 且**不传 `homeScope`**；`zones` 与 `homeScope` **互斥**；关注态需登录态复验 `homeScope=follow`，当前隐藏 | 已实现（全部/单区可用，关注态隐藏） |
| 二手 | `resale_item` | 列表 | `POST api/resale/search`（`zones`、`type`、`puritys`、`page`） | 出售/求购 + 成色 | 全部：省略 `zones`；**关注：`zones` 逗号串为交集语义（不可用），隐藏该板块** | 已实现（全部/单区可用，关注态隐藏） |
| 厂商 | `company` | 实体网格 | `GET api/zone/items`（`zones`/`nodeId`、`itemType`、`category`、`page`） | — | 全部：省略 `zones`；关注：`zones=<逗号串>`（并集）；**不下发 `homeScope`** | 已实现（三作用域可用） |
| 系列 | `series` | 同上 | 同上 | — | 同上 | 同上 |
| 作品 | `works` | 同上 | 同上 | — | 同上（`total` 服务端封顶 10000，翻页按 `hasMore`） | 同上 |
| 角色 | `charactar` | 同上 | 同上 | — | 同上（`total` 封顶 10000） | 同上 |
| 人物 | `person` | 同上 | 同上 | — | 同上（`total` 封顶 10000） | 同上 |
| 商品 | `shop_item` | — | `POST api/goods/tb/query`（原版） | — | — | **不做**（产品决策：不替官方商城导流）；`zone/info.tabs` 会出现该 type，客户端白名单直接隐藏 |
| 社群 | `community` | — | 仅消费 `communityLinks` JSON，不发请求 | — | — | **不做**（无实现；聚合态原版不含）；`zone/info.tabs` 会出现该 type，客户端白名单直接隐藏 |

矩阵要点（实测结论见 [抓包验证报告](research/aggregate-api-verification.md) §4）：

- **全部聚合**：10 项全可见；空专区集合（省略 zone 参数）即等价全站。
- **关注聚合**：仅 `action/album/company/series/works/charactar/person` 7 项可见；讨论、产品、二手隐藏，不展示「暂不支持」占位，也不发起请求。
- **单专区**：服务端 `zone/info.tabs` ∩ 白名单并保留服务端顺序；各专区下发集合差异很大（6–12 项），不能客户端固定排序。
- **永不出现**：`shop_item`、`community`，任何作用域都不得渲染入口或发起请求。

---

## 5. 状态与持久化

| 键 | 存储 | 值 | 默认 | 说明 |
|---|---|---|---|---|
| `feed_zone_tab_key` | DataStore | `all` / `follow` / `z{nodeId}` | `all` | 已有，复用 |
| `feed_zone_board` | DataStore（新增） | `{tabKey}\|{type}` | 空 | 二级板块，按一级前缀校验 |
| `feed_sub_type` | DataStore | 9 项 subType | `recommend` | 已有，复用（`all`→`allInfo` 兼容映射） |
| 板块滚动位置 / 折叠比例 | 内存 | 板块 key → index/offset/折叠比例 | — | 不持久化（与现状一致） |

恢复规则：

1. 冷启动读取一级；若值为 `follow` 或已不可用 → 回落 `all`。
2. 二级：`feed_zone_board` 前缀与当前一级 `tabKey` 匹配才恢复；否则取该专区首板块（聚合=`action`，单区=服务端首个）。
3. 三级：`feed_sub_type` 恢复；非法值回落 `recommend`。
4. 切换专区时写入一级键；切换板块时写入二级键（`{tabKey}|{type}`）。
5. 兼容：历史 `feed_sub_type=all` 读入时映射为 `allInfo`；旧版 `feed_category_id` 不再参与首页筛选。

---

## 6. 自适应与 M3 对照

| 宽度 | 一级切换器 | 二级 | 三级 | 内容 |
|---|---|---|---|---|
| < 600dp（紧凑） | 底部抽屉 | 可滚动 Tab | 可折叠筛选行 | 1 列瀑布流 |
| 600–839dp（中等） | 底部抽屉 | 同上 | 同上 | 2 列 / 网格 4 列 |
| ≥ 840dp（扩展） | 锚定下拉菜单 | 同上 | 同上 | 3 列 / 网格 6 列，不做双窗格与竖排 |

M3 对照要点：

- 一级为菜单/抽屉（单选项列表），**不是 Tab**；二级为 `PrimaryScrollableTabRow`（主内容目的地）；三级为 Chip / Menu / SegmentedButton。全局仅两级 Tab，符合规范。
- 可滚动 Tab 不设数量上限；固定数量 Tab 才受 ≤4 约束。
- 筛选 Chip 单选、排序用独立菜单，不与筛选混在同一组。
- 触控目标 ≥ 48dp；列表项高度 48dp；不使用硬编码 shape（ADR-0004），颜色取主题 token。

---

## 7. 实施分期与验证闸门

### 7.1 抓包验证闸门（已完成，结论见 [聚合接口抓包验证报告](research/aggregate-api-verification.md)）

1. ✅ 聚合态空 `zones` 等价全站：相册、讨论、二手、产品、实体 5 类逐一验证通过（讨论须改用 `zoneNodeId`）。
2. ✅ `homeScope` 被产品/实体 5 类识别：非空即覆盖 `zones`；关注语义需登录态复验（产品关注态当前隐藏）。
3. ✅ `subType=allInfo` 有效、旧键 `all` 静默回落「推荐」（客户端做兼容映射）；其余 8 键均有效。
4. ✅ 10 个真实专区 `zone/info.tabs` 覆盖：白名单外仅 `shop_item`/`community`，白名单 10 项足够。
5. ✅ 关注聚合 `zones`（分享 / 实体）逗号串长度：实测 150 个 id / 1349 字符仍成功；全站 52 专区最长约 467 字符，无需截断。动态 `zoneIds` 仅验证过并集语义（2 个 id），长度按同一上限推定。

### 7.2 实施顺序（已按序完成）

1. **公共筛选定义**：把两处重复的 9 项 `subType` 抽为单一共享定义（含 2.43 文案与顺序）。
2. **数据层参数改造**：讨论专区参数改为 `zoneNodeId`（String、仅单值）；产品/实体保持 `zones` 且**不下发 `homeScope`**。
3. **抽取 `ZoneBoardPane`**：从 `ZoneDetailScreen` 拆出板块内容 + 筛选 + 分页状态，专区详情改为使用同一面板。
4. **顶栏与切换器**：标题切换器 + 抽屉/下拉 + 分组列表。
5. **首页装配**：二级 TabRow、三级筛选层、状态键与恢复、空/错/加载态。
6. **测试与门禁**：新增 ViewModel 纯逻辑测试（fake 仓储）；本地跑 `pwsh -File scripts/check-ui-rules.ps1`；CI 全量绿。

### 7.3 验证约束

- 本地禁止 Gradle 编译/单测（`CODING_STANDARDS.md`「CI 裁决权威」），验证统一交 CI。
- 首页 `HomeFeedViewModelTest` 已解除 CI 隔离（`docs/testing.md` 登记表）；新增测试不得再进隔离表。
- 静态门禁：Lazy `key` 必填、混合列表 `contentType` 必填、禁骨架屏、禁硬编码 shape。

---

## 8. 风险与未决项

> 以下为设计阶段的风险清单；#151–#162 实施时已闭环 R1/R2/R3/R4/R6（结论见抓包报告与 CI），保留作历史记录。

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| R1 | 聚合态空 zones 语义未验证 | 聚合板块可能返回非全站数据或报错 | 抓包闸门；失败板块自动隐藏（无假入口） |
| R2 | 首页 VM 测试被隔离 | 重构无回归网 | 先解除隔离 / 新增测试类，再改行为 |
| R3 | `ZoneDetailScreen` 抽取回归 | 专区详情体验回退 | 先复制共享、再删重复，分两步提交；补板块级测试 |
| R4 | `zoneNodeId` / `zoneIds` 双轨参数 | 单区/聚合拼参错误 | 统一封装作用域对象（单区/聚合/全站三态），禁止散拼 |
| R5 | 抽屉纯文本无搜索 | 专区极多时定位慢 | 已关注专区 > 15 个时再增设搜索框 |
| R6 | 2.43 文案/键值变更 | 旧筛选请求失效 | 兼容映射 + 抓包验证 `allInfo` |
| R7 | 切换器偏离 2.43 视觉 | 用户观感差异 | 本 ADR 已记录为有意偏离；后续可评估图标方案 |

实施后的开放项（结论已定，保留供追溯）：

- 实体 5 类在聚合态**不做**额外排序/分类入口（原版无入口，本项目按 §3.4「暂无筛选」落地）。
- 抽屉**不新增**「最近访问」排序：仍按 `ZoneTabsSettings.sort`（默认 `default`），排序切换留在首页设置。
- 首页顶部**不保留**独立「专区广场」入口：改为切换器尾部「浏览全部专区」（主按钮），与 ADR-0022 决策 1 一致。

---

## 附录 A：2.43 一级/二级视觉参数（参考，非本设计目标）

- 一级条：容器高 78dp；单项宽 70dp；图标区 54dp（图标 48dp，圆角 8dp）；文案 12sp；选中 3dp 描边；「+浏览」56×56、13sp。
- 二级 Tab：14sp，内边距 12dp/3dp，选中加粗 + 2dp 下划线，容器上下 padding 5dp。
- 三级 Chip：12sp，内边距 14dp/4dp，单选；自动收起阈值 8dp、动画 150ms。

本设计采用纯文本切换器与 M3E 原生组件，不复刻上述自绘样式。
