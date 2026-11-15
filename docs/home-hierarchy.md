# 首页三级信息架构设计规格

> 状态：设计定稿，待实施
> 关联决策：[ADR-0022](adr/0022-home-zone-three-level-and-selector.md)（取代 ADR-0020 的「专区分类栏」条款）
> 逆向基准：Hpoi 2.43（`net.hpoi`, versionCode 200135）
> 范围：仅设计规格，不含源码改动

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
二级  单区：api/zone/info?nodeId= → zone.tabs[].type（服务端顺序，客户端白名单 12 项）
      聚合：客户端固定 11 项（不含 community）
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
| `comment` | 评论 | `comment` 讨论 |
| `allInfo` | 全部情报 | `all` 全部 |
| `confirm` | 制作 | `confirm` 制作 |
| `official_pic` | 更图 | `official_pic` 官图 |
| `preorder` | 开订 | `preorder` 预定 |
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

- 聚合态各板块共用同一套包装层：统一下传 `homeScope`（`all`/`follow`/空）与 zone 串（`all`→空、`follow`→逗号串、单区→`<nodeId>`）。
- 仅「产品」与「实体 5 类」真正把 `homeScope` 发给服务端；相册/讨论/二手/商品靠 `zones` 参数表达范围；动态只用 `zoneIds`（单区额外重复传 `zoneNodeId`）。

---

## 2. 目标架构

### 2.1 层级职责

| 层级 | 交互基元 | 数据来源 | 职责 |
|---|---|---|---|
| 一级 专区 | 顶栏标题 + 切换器（底部抽屉 / 宽屏下拉） | `ZoneRepository`：`focusZonesFlow` + `settingsFlow`（复用现有 `buildCategories`） | 识别当前专区、切换专区、进入专区广场/首页设置 |
| 二级 子板块 | `PrimaryScrollableTabRow` | 单区 `zone/info.tabs` ∩ 白名单；聚合固定 10 项 | 切换内容形态 |
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

**结构（纯文本，无图标）**

```
快捷
  全部
  关注
我的专区                       ← 按 ZoneTabsSettings.sort（默认/最近/自定义）排序，hiddenZoneIds 生效
  {专区名}
  …
───────────────────────────
[浏览全部专区]   [首页设置]     ← 文本动作，分别进入 ZoneBrowse / ZoneSettings
```

- 选中态：**文字加粗 + `colorScheme.primary`**；不使用勾选图标、不使用专区区图标。
- 分组标题：`labelLarge` + `onSurfaceVariant`。
- 列表项：`bodyLarge`，最小高度 48dp，整行可点；点击 = 切换并关闭切换器。
- 「关注」在未登录或关注为空时仍可选择：进入空态（引导去浏览专区），不发请求（保留现有短路逻辑）。
- 切换器打开方式：点击顶栏标题区；关闭：选择条目、点遮罩、返回手势。
- 无障碍：列表整体 `role = List`，选中项读取「已选中」状态；切换器展开时焦点移入首个分组标题。

**自适应**

| 窗口宽度 | 呈现 |
|---|---|
| < 840dp | `ModalBottomSheet`（纯文本，最大高度约 70%，超出内部滚动） |
| ≥ 840dp | 锚定标题的 `DropdownMenu`（同内容、同顺序） |

### 3.3 子板块 TabRow

- 组件：`PrimaryScrollableTabRow`（M3 稳定 API；一级不是 Tab，故二级承担 primary 语义），`edgePadding = 16dp`，沿用主题指示器。
- 单区：`zone/info.tabs` 的 `type` ∩ 白名单（10 类），保持服务端顺序；为空时隐藏 TabRow 并展示板块空态。
- 聚合（全部/关注）：固定 10 项，顺序：`action, album, posts, hobby, resale_item, company, series, works, charactar, person`。
- 切换板块：不重建页面；内容区切换并恢复该板块的滚动位置与筛选状态（内存）。
- TabRow 常驻吸顶，不随滚动折叠。

### 3.4 板块筛选层

| 板块 | 三级控件 | 请求参数 |
|---|---|---|
| 动态 `action` | 9 项单选 Chip（文案/顺序对齐 2.43） | `subType` |
| 分享 `album` | 排序 Chip（推荐/最新创建/最近更新/图片数量/总热度/一天热度）+ 分类入口 | `order`、`category` |
| 讨论 `posts` | 排序 Chip（回复/新贴/推荐）+ 分类入口 | `order`、`category` |
| 产品 `hobby` | 排序菜单（7 天热度/1 天热度/总热度/最新入库/发售时间/综合评分）+ 年份 + 月份 + 分类级联 + 高级筛选抽屉 | `order`、`releaseYear/Month`、`category`、`r18/sex/scale` |
| 二手 `resale_item` | 出售/求购 `SegmentedButton`（2 项单选视图切换）+ 成色 Chip | `type`、`purity` |
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
- 聚合态某板块经验证不可用：**直接从 TabRow 中隐藏**，不展示「暂不支持」占位。

### 3.7 手势与滚动

- 下拉刷新：刷新当前板块内容与未读数（现状）。
- FAB：回顶并刷新当前板块（现状）。
- 切换专区/板块/筛选：滚动复位到顶、筛选条展开。
- 状态保存：每个板块独立滚动位置（内存），跨“专区↔板块”切换不丢。

---

## 4. 板块矩阵

| 板块 | type | 内容形态 | 接口 | 单区筛选 | 聚合参数 | RePoi 现状 |
|---|---|---|---|---|---|---|
| 动态 | `action` | 瀑布流卡片 | `GET api/user-action/list-v3`（`lastTime` 游标、`subType`、`zoneNodeId`、`zoneIds`） | 9 Chip | `zoneIds` | 已实现，需改为多板块状态 |
| 分享 | `album` | 相册网格 | `POST api/album/list`（`order`、`zones`、`category`、`page`） | 排序 + 分类 | `zones` 逗号串 | 已实现，可直接接聚合 |
| 讨论 | `posts` | 帖子列表 | `GET api/posts/search`（`order`、`zones`、`itemType`、`keyword`、`page`） | 排序 + 分类 | `zones` | 已实现，zone 参数需 `Long`→`String` |
| 产品 | `hobby` | 网格/列表 | `POST api/hobby/query-v2`（`order`、`zones`、`category`、`releaseYear/Month`、`r18/sex/scale`、`page`） | 排序 + 年份/月份 + 分类 + 高级筛选 | `zones` + `homeScope` | 已实现，需补 `homeScope` |
| 二手 | `resale_item` | 列表 | `POST api/resale/search`（`zones`、`type`、`purity`、`page`） | 出售/求购 + 成色 | `zones` | 已实现，zone 参数需放宽 |
| 厂商 | `company` | 实体网格 | `GET api/zone/items`（`zones`/`nodeId`、`itemType`、`category`、`page`） | — | `zones` + `homeScope`（待验证） | 已实现，参数需改造 |
| 系列 | `series` | 同上 | 同上 | — | 同上 | 同上 |
| 作品 | `works` | 同上 | 同上 | — | 同上 | 同上 |
| 角色 | `charactar` | 同上 | 同上 | — | 同上 | 同上 |
| 人物 | `person` | 同上 | 同上 | — | 同上 | 同上 |
| 商品 | `shop_item` | — | `POST api/goods/tb/query`（原版） | — | — | **不做**（产品决策：不替官方商城导流） |
| 社群 | `community` | — | 仅消费 `communityLinks` JSON，不发请求 | — | — | **不做**（无实现，聚合态原版不含） |

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

### 7.1 抓包验证闸门（先于编码）

1. 聚合态空 `zones` 是否等价全站：相册、讨论、二手、产品、实体 5 类逐一验证。
2. `homeScope`（`all`/`follow`）在产品的请求中是否被服务端识别；实体 5 类传 `zones + homeScope` 的实际返回。
3. `subType=allInfo` 与旧 `all` 的返回是否等价；`comment/official_pic/preorder` 键值未变。
4. 生产样本 `zone/info.tabs` 的板块覆盖（哪些专区下发哪些板块、是否存在白名单外的 type）。
5. 关注聚合 `zoneIds` 逗号串长度上限（几十个专区时的表单/URL 限制）。

### 7.2 实施顺序

1. **公共筛选定义**：把两处重复的 9 项 `subType` 抽为单一共享定义（含 2.43 文案与顺序）。
2. **数据层参数改造**：讨论/二手 zone 参数放宽为 `String`；产品补 `homeScope`；实体 5 类改 `zones + homeScope`。
3. **抽取 `ZoneBoardPane`**：从 `ZoneDetailScreen` 拆出板块内容 + 筛选 + 分页状态，专区详情改为使用同一面板。
4. **顶栏与切换器**：标题切换器 + 抽屉/下拉 + 分组列表。
5. **首页装配**：二级 TabRow、三级筛选层、状态键与恢复、空/错/加载态。
6. **测试与门禁**：新增 ViewModel 纯逻辑测试（fake 仓储）、`jvmTest` 布局测试；本地跑 `pwsh -File scripts/check-ui-rules.ps1`；CI 全量绿。

### 7.3 验证约束

- 本地禁止 Gradle 编译/单测（`CODING_STANDARDS.md`「CI 裁决权威」），验证统一交 CI。
- 首页 `HomeFeedViewModelTest` 目前被 CI 隔离（`docs/testing.md` 登记表），实施时须先解除隔离或新增未隔离测试类，保证回归网有效。
- 静态门禁：Lazy `key` 必填、混合列表 `contentType` 必填、禁骨架屏、禁硬编码 shape。

---

## 8. 风险与未决项

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| R1 | 聚合态空 zones 语义未验证 | 聚合板块可能返回非全站数据或报错 | 抓包闸门；失败板块自动隐藏（无假入口） |
| R2 | 首页 VM 测试被隔离 | 重构无回归网 | 先解除隔离 / 新增测试类，再改行为 |
| R3 | `ZoneDetailScreen` 抽取回归 | 专区详情体验回退 | 先复制共享、再删重复，分两步提交；补板块级测试 |
| R4 | `zoneNodeId` / `zoneIds` 双轨参数 | 单区/聚合拼参错误 | 统一封装作用域对象（单区/聚合/全站三态），禁止散拼 |
| R5 | 抽屉纯文本无搜索 | 专区极多时定位慢 | 已关注专区 > 15 个时再增设搜索框 |
| R6 | 2.43 文案/键值变更 | 旧筛选请求失效 | 兼容映射 + 抓包验证 `allInfo` |
| R7 | 切换器偏离 2.43 视觉 | 用户观感差异 | 本 ADR 已记录为有意偏离；后续可评估图标方案 |

未决项（实施前确认）：

- 实体 5 类在聚合态是否需要额外排序/分类入口（原版无，先不做）。
- 抽屉是否需要「最近访问」排序作为默认（现按 `ZoneTabsSettings.sort`，默认 `default`）。
- 首页顶部是否需要保留原有「专区广场」的显式入口（现改为抽屉尾部「浏览全部专区」）。

---

## 附录 A：2.43 一级/二级视觉参数（参考，非本设计目标）

- 一级条：容器高 78dp；单项宽 70dp；图标区 54dp（图标 48dp，圆角 8dp）；文案 12sp；选中 3dp 描边；「+浏览」56×56、13sp。
- 二级 Tab：14sp，内边距 12dp/3dp，选中加粗 + 2dp 下划线，容器上下 padding 5dp。
- 三级 Chip：12sp，内边距 14dp/4dp，单选；自动收起阈值 8dp、动画 150ms。

本设计采用纯文本切换器与 M3E 原生组件，不复刻上述自绘样式。
