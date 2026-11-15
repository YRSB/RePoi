# 聚合接口抓包验证报告（Issue #151）

> 状态：验证完成，结论已可作为实施依据
> 日期：2026-11-15（采样时间约 10:32–11:02，UTC+8）
> 关联：Issue #151（前置验证闸门）；上游设计 [docs/home-hierarchy.md](../home-hierarchy.md) 第 7.1 节；ADR-0022
> 验证对象：Hpoi 线上 API（`https://www.hpoi.net.cn/`），**匿名（utoken 为空）**、`appVersion=200135`（2.43）
> 范围：仅接口事实与上线矩阵，不含产品代码改动

---

## 0. 结论速览

| # | 验证项 | 结论 | 一句话 |
|---|---|---|---|
| 1 | 聚合态空专区集合是否等价全站 | **通过** | 分享/讨论/二手/产品/实体 5 类在「完全不带 zone 过滤」与「`zones=` / `zoneNodeId=` 空串」下均返回全站相同数据；但讨论板块的过滤参数是 `zoneNodeId` 而非 `zones` |
| 2 | 聚合标记 `homeScope` | **通过（识别）／不确定（关注语义）** | 产品 `hobby/query-v2` 与实体 `zone/items` 确实解析 `homeScope`：一旦携带**非空** `homeScope`，服务端会**忽略 `zones`** 改走服务端作用域；相册/讨论/二手忽略 `homeScope`。匿名与伪造 token 下 `all`/`follow` 均回落全站，真实「关注集合」过滤无法匿名验证 |
| 3 | 动态筛选键值 | **失败（旧键 `all`）／通过（其余键）** | `subType=all` 不等价 `allInfo`，而是静默退化为默认「推荐」；`allInfo`、`comment`、`official_pic`、`preorder`（以及其余 5 键）均有效 |
| 4 | 专区信息下发板块覆盖 | **通过** | 10 个真实专区 `zone/info.tabs[]` 只出现 12 种 type；白名单外仅 `shop_item` 与 `community`（均不应上线），无其他未知 type；各专区下发顺序与集合差异很大，必须以服务端顺序为准 |
| 5 | 关注聚合 `zones` 逗号串长度 | **通过** | 1/5/10/20/40/52/80/150 个 id（最长 1349 字符）全部 200 成功、无截断报错；全站真实专区只有 52 个，实际最长约 467 字符，无需客户端截断 |

**可上线 / 需隐藏（详见 §4）**

- **全部聚合态**：动态、分享、讨论、产品、实体 5 类均可上线（产品可不带 `homeScope` 或带 `homeScope=all`）。
- **关注聚合态**：动态、分享、实体 5 类可上线（`zoneIds`/`zones` 逗号串为**并集**语义）；**讨论与二手不可上线**（无多专区通道 / 逗号串按交集处理，会得到空列表）；**产品待定**（需登录态复验 `homeScope=follow`）。
- **永远隐藏**：`shop_item`（产品决策不做）、`community`（无实现；白名单已排除）。

---

## 1. 目的与方法

### 1.1 目的

在实现首页「聚合（全部/关注）」子板块前，用真实服务端返回验证 [docs/home-hierarchy.md](../home-hierarchy.md) 第 7.1 节的五个假设，给出逐项「通过 / 失败 / 不确定」结论、可上线的聚合板块清单，以及后续 ticket 可直接使用的参数结论。

### 1.2 验证环境

| 项 | 值 |
|---|---|
| 基址 | `https://www.hpoi.net.cn/` |
| 鉴权 | 匿名，`utoken=` 空串；另有一组「伪造 utoken」对照 |
| 公共参数 | `platform=android&appVersion=200135&channel=official&machine=SM-S9280&system=34&utoken=` |
| 请求头 | `User-Agent: okhttp/4.12.0`、`Referer: https://www.hpoi.net/android` |
| 客户端版本 | 200135（Hpoi 2.43，反编译基准）；注意 RePoi 当前 `CommonParams.API_APP_VERSION=200133` |
| 方法 | 串行、间隔约 250 ms、每接口少量请求，不做压测 |
| 样本专区 | 全站 52 个专区中取 10 个（见 §2.4），单专区样本主要用 `33476094`（手办）、`39989602`（26年10月新番）、`39228575`（机甲模型） |

### 1.3 匿名访问结论

所有本次涉及的读接口在 `utoken=` 空串下均返回 HTTP 200 且 JSON `success=true, code=200`，无需登录。仅「关注集合」本身依赖登录态（匿名 `zone/home-tabs` 的 `focusZones` 为空数组、`homeZones` 为空）。

### 1.4 复现方式（最小命令）

PowerShell（Windows 本机可直接执行）：

```powershell
$h = @{ 'User-Agent' = 'okhttp/4.12.0'; 'Referer' = 'https://www.hpoi.net/android' }
$c = 'platform=android&appVersion=200135&channel=official&machine=SM-S9280&system=34&utoken='

# 专区全量（含 52 个专区与分组）
Invoke-RestMethod "https://www.hpoi.net.cn/api/zone/home-tabs?$c" -Headers $h

# 实体 5 类（GET）：对比不带 zones / 空串 / 单专区 / 多专区
Invoke-RestMethod "https://www.hpoi.net.cn/api/zone/items?$c&itemType=company&page=1&pageSize=3&category=0" -Headers $h
Invoke-RestMethod "https://www.hpoi.net.cn/api/zone/items?$c&itemType=company&page=1&pageSize=3&category=0&zones=33476094,39989602" -Headers $h

# 相册（POST form）：空串与不带参数对比
Invoke-WebRequest 'https://www.hpoi.net.cn/api/album/list' -Method Post -Headers $h `
  -ContentType 'application/x-www-form-urlencoded' -Body "$c&page=1&pageSize=24&order=addTime&zones="

# 动态筛选键值
Invoke-RestMethod "https://www.hpoi.net.cn/api/user-action/list-v3?$c&categoryId=0&lastTime=0&pageSize=10&subType=allInfo" -Headers $h
```

完整探测脚本（6 个，串行、含公共参数与结果汇总）位于**仓库外**探测目录 `RePoi-exploration/probe-scripts/`（`probe-lib.ps1`、`probe-parameters.ps1`、`probe-refine.ps1`、`probe-union-auth.ps1`、`probe-coverage-length.ps1`、`probe-action-zones.ps1`），按团队约定不提交进仓库。原始响应与汇总 JSON 保留在同一目录的 `results/` 下。

### 1.5 判定口径

- **等价全站**：首屏条目 id 序列（及可量化的 `total`）与「不带任何 zone 过滤」完全一致；
- **过滤生效**：返回条目必须属于请求专区集合（用条目的 `zoneIds` 字段校验），或非法专区 id 返回空列表；
- **多专区语义**：用两个互不包含的专区 A/B 交叉验证。仅 A 结果 = 「仅首个」；同时属于 A 且 B 的结果 = 交集（AND）；A、B 条目混合出现 = 并集（OR）。
- 动态数据说明：Hpoi 是活跃站点，相邻分钟会有新内容插入；本报告所有 id 为采样快照，结论以「同批次对照」为准（对照请求在同一分钟内完成）。

---

## 2. 逐项验证结论

### 2.1 项 1：聚合态空专区集合是否等价全站 —— **通过**

对五类接口分别请求「完全不带过滤」「空串」「单个真实专区」「非法专区」四种形态（分享/产品/二手用 POST form，讨论/实体用 GET query）：

| 接口 | 不带过滤 | `zones=` 空串 | 单专区 | 非法专区 | 与全站等价？ |
|---|---|---|---|---|---|
| 分享 `POST api/album/list` | 24 条，首条 `41327307` | 同左，id 序列完全一致 | 手办 24 条（首条同）、新番 8 条 | 0 条 | **是** |
| 讨论 `GET api/posts/search` | 12 条，首条 `41318137` | `zones=` 与不带均被忽略；`zoneNodeId=` 空串与不带一致 | `zoneNodeId=33476094` → 另一批（首条 `41299179`）、`zoneNodeId=39989602` → 2 条 | `zoneNodeId=999999999` → 0 条 | **是（但参数名须为 `zoneNodeId`）** |
| 二手 `POST api/resale/search` | 12 条，首条 `2400543` | 同左，完全一致 | 手办 12 条（同为全站前 12）、机甲 12 条（另一批，首条 `41324067`） | 0 条 | **是** |
| 产品 `POST api/hobby/query-v2` | 12 条，首条 `41162582` | 同左，完全一致 | 新番 1 条 `40409483`、机甲 12 条另一批 | 0 条 | **是** |
| 实体 5 类 `GET api/zone/items` | `total`：company 6271 / series 5539 / works 10000 / charactar 10000 / person 10000 | 同左，`total` 完全一致 | `zones=33476094` 或 `nodeId=33476094`：6271→427、5539→3814、10000→7363、10000→8221、10000→4617 | `total=0` 且空列表 | **是** |

关键证据（同批次对照）：

```text
album  : 无zones ids=41327307,41327287,41327106,41327104,41326962,41326951,41326855,41326677
         zones=  ids=41327307,41327287,41327106,41327104,41326962,41326951,41326855,41326677  （完全一致）
posts  : 无过滤 / zones= / zones=33476094 / zones=999999999 均同一批 id（说明 zones 被忽略）
         zoneNodeId=33476094 → 另一批；zoneNodeId=999999999 → 0 条
resale : 无zones 与 zones= 同批；zones=33476094 同批（全站最新二手多数挂在手办专区）；zones=39228575 另一批
hobby  : 无zones 与 zones= 同批；zones=33476094 同批；zones=39989602 仅 1 条；zones=39228575 另一批
实体    : 无zones 与 zones= 的 total 完全一致（见上表）
```

**重要纠正**：讨论板块的专区过滤参数是 **`zoneNodeId`（String）**，`zones` 参数被服务端完全忽略。因此设计文档 [home-hierarchy.md](../home-hierarchy.md) §4「讨论：`zones`」与 §1.4「相册/讨论/二手/商品靠 `zones` 参数表达范围」不准确。

**结论**：五类在聚合「全部」态都可以用「不传 zone 过滤（或空串）」表达全站；单专区用各自真实参数即可。**通过**。

### 2.2 项 2：聚合标记 `homeScope` 是否被服务端识别 —— **通过（识别）／不确定（关注过滤）**

验证方法：对同一接口比较「无 `homeScope`」「`homeScope=all`」「`homeScope=follow`」「`homeScope=zzz`」，并在携带 `homeScope` 时叠加真实 `zones`，观察 `zones` 是否仍生效。

| 接口 | `homeScope` 是否被识别 | 证据 |
|---|---|---|
| 产品 `hobby/query-v2` | **是（会覆盖 `zones`）** | `zones=新番` → 1 条；`zones=新番&homeScope=follow` → 12 条（=不带过滤的全站结果）；`zones=手办,新番&homeScope=follow` → 12 条；`homeScope` 为 `all`/`follow`/非法值 `zzz` 表现一致；**空串 `homeScope=` 不触发覆盖**（结果与仅 `zones` 一致） |
| 实体 5 类 `zone/items` | **是（会覆盖 `zones`）** | `zones=33476094` → company `total=427`；叠加**非空** `homeScope`（all/follow）→ `total=6271`（全站）；works/charactar/person 同样回到全站 `total`；**`homeScope=` 空串 + `zones=33476094` → 仍为 `total=427`（空串等价于不传）** |
| 分享 `album/list` | 否（忽略，`zones` 仍生效） | `homeScope=follow&zones=33476094` 与 `zones=33476094` 的 id 序列一致 |
| 讨论 `posts/search` | 否 | `homeScope=follow&zoneNodeId=33476094` 与仅 `zoneNodeId` 一致 |
| 二手 `resale/search` | 否 | `homeScope=follow&zones=33476094` 与仅 `zones` 一致 |

**服务端作用域语义（推断，已由「覆盖 zones」证明）**：产品与实体 5 类在收到 `homeScope` 后不再使用显式 `zones`，而按服务端解析的首页作用域取数：

- `homeScope=all` → 全站（匿名下与不带一致）；
- `homeScope=follow` → 登录用户的关注专区集合；匿名（`utoken` 空）或伪造 token 时实测回落为全站，**不会报 4001**，因此无法验证真实关注集合过滤结果；
- 传**非空** `homeScope` 时 **`zones` 会被完全忽略**，两者不能同时使用；`homeScope=` 空串等价于不传（`zones` 仍生效）。

伪造 token 对照（`utoken=fake-invalid-token-probe`）：

```text
hobby homeScope=follow      → 200 success=true，12 条（全站）
hobby homeScope=all         → 200 success=true，12 条
zone/items company homeScope=follow+zones=33476094 → 200 success=true，total=6271（全站，zones 被忽略）
```

**结论**：「服务端识别 `homeScope`」**通过**；「`homeScope=follow` 能按真实关注集合过滤」**不确定**（匿名无法验证，需登录态复验）。因此产品板块的「关注」聚合不能直接判定可上线（见 §4）。

### 2.3 项 3：动态筛选键值 —— **失败（旧键 `all`）／通过（其余键）**

`GET api/user-action/list-v3`（`categoryId=0&lastTime=0&pageSize=10`）逐一请求 9 个官方键值 + 旧键 `all` + 非法键：

| subType | HTTP/业务 | 首屏 id（前 4 个） | actionData 分布 | 判定 |
|---|---|---|---|---|
| `recommend` | 200 / success | `41327408,41327376,41327298,41327314` | 无（空） | 有效 |
| `all`（旧键） | 200 / success | 与 `recommend` **完全相同** | 无 | **无效，静默回落推荐** |
| `allInfo` | 200 / success | `41320531,41319013,41318921,41316191` | confirm:1 official_pic:3 preorder:6 | 有效（「全部情报」） |
| `comment` | 200 / success | `41327376,41327235,41327193,41327170` | 无（空） | 有效 |
| `official_pic` | 200 / success | `41315967,41312266,41309983,41307572` | official_pic:10 | 有效 |
| `preorder` | 200 / success | `41319013,41318921,41316191,41309585` | preorder:10 | 有效 |
| `confirm` | 200 / success | `41320531,...` | confirm:10 | 有效 |
| `delay` | 200 / success | `41293781,41293770,37307479,...` | delay:10 | 有效 |
| `release` | 200 / success | `41293739,41293737,...` | release:10 | 有效 |
| `reorder` | 200 / success | `41276063,41228896,...` | reorder:10 | 有效 |
| `zzz_bogus`（非法） | 200 / success | 与 `recommend` 相同 | 无 | 静默回落推荐 |

- `allInfo` 连续两次请求 id 完全一致，可排除数据漂移干扰；
- `all` 与 `recommend` 完全相同、与 `allInfo` 完全不同，说明服务端把未知/旧键当作默认「推荐」，**不会报错但语义错误**。

**结论**：旧键 `all` 必须做客户端兼容映射（`all` → `allInfo`），否则用户看到的「全部」实际是「推荐」。其余 8 个键值均有效。**失败（旧键）／通过（其余键）**。

### 2.4 项 4：专区信息下发板块覆盖 —— **通过**

从 `api/zone/home-tabs` 的 6 个分组中取 10 个真实专区（覆盖手办、机甲、特摄、IP、游戏、活动等）逐个 `GET api/zone/info?nodeId=`：

| nodeId | 专区 | `zone.tabs[].type`（服务端顺序） |
|---|---|---|
| 39989602 | 26年10月新番 | action, album, works, charactar, posts, shop_item |
| 33476094 | 手办 | action, album, posts, hobby, shop_item, resale_item, company, series, works, charactar, person, community |
| 39228575 | 机甲模型 | action, album, posts, hobby, resale_item, company, series, works, charactar, shop_item, community |
| 25619129 | 奥特曼 | action, album, hobby, works, series, charactar, resale_item, shop_item, community |
| 25734924 | 假面骑士 | action, album, hobby, works, charactar, series, resale_item, shop_item, community |
| 25194324 | 赛马娘 | action, album, charactar, works, hobby, resale_item, shop_item, community |
| 25998424 | 碧蓝航线 | action, album, works, hobby, posts, resale_item, shop_item, community |
| 39274231 | IP作品库 | action, works, album, posts, charactar, person, shop_item |
| 39274091 | 动漫 | action, works, charactar, album, posts, person, shop_item |
| 36138460 | WF2026上海 | action, album, hobby, posts, resale_item, shop_item, community |

统计：

- 10 个样本全部 HTTP 200 / `success=true`；
- `type` 全集：`action, album, posts, hobby, resale_item, shop_item, company, series, works, charactar, person, community`（共 12 种，与 2.43 已知定义一致）；
- **白名单（10 类）之外仅出现 `shop_item`、`community`**，未发现任何其他未知 type（也没有 `works_series` 之类）；
- `action` 在所有样本中都排第一；其余顺序随专区差异明显，必须按服务端顺序渲染，不能客户端固定排序；
- 各专区下发集合差异很大：新番只有 6 个 tab（无产品/二手/厂商/系列），IP作品库/动漫只有 7 个（无 hobby），说明「服务端下发 ∩ 客户端白名单」会天然得到不同长度的二级 Tab；
- 注意：`home-tabs` 里 `zones[].itemTypes` 与 `zone/info.tabs` **并不一致**（例如新番的 `itemTypes` 含 hobby/resale_item，但 `zone/info.tabs` 未下发 hobby/resale_item），实现应以 `zone/info.tabs` 为准。

**结论**：白名单 10 项足够，无需扩展；`shop_item`、`community` 保持隐藏。**通过**。

### 2.5 项 5：关注聚合 `zones` 逗号串长度上限 —— **通过**

用全站 52 个真实专区 id 循环拼接，对分享 `album/list`（POST）与实体 `zone/items`（GET，`total` 可量化）递增测试：

| 请求 id 个数 | 字符串长度 | album 结果 | zone/items(company) 结果 | 是否 200/success |
|---|---|---|---|---|
| 1 | 8 | 12 条 | total=0（该专区本身无厂商条目，过滤正常） | 是 |
| 5 | 44 | 12 条 | total=1，命中校验通过 | 是 |
| 10 | 89 | 12 条 | total=9，命中校验通过 | 是 |
| 20 | 179 | 12 条 | total=13，命中校验通过 | 是 |
| 40 | 359 | 12 条 | total=546，命中校验通过 | 是 |
| 52 | 467 | 12 条 | total=550，命中校验通过 | 是 |
| 80（循环拼接） | 719 | 12 条 | total=550，命中校验通过 | 是 |
| 150（循环拼接） | 1349 | 12 条 | total=550，命中校验通过 | 是 |

对照基线：

```text
zone/items(company) 无 zones → total=6271（全站，含未挂专区的厂商）
zone/items(company) zones=52 个全站专区 → total=550（只统计挂了专区的厂商，属正常过滤）
album/list 无 zones 首屏 id = 41327307,41327287,...；zones=52 个全站专区后 id 与该基线一致（并集=全站）
```

- 1–150 个 id 全部返回 200 且 `success=true`，无报错、无截断迹象；
- 长度增长到 150 个 id（1349 字符，GET URL 约 1500 字符）仍被正常解析；
- 现实上限：`zone/home-tabs` 下发的全站专区总数只有 **52** 个，用户最多也只能关注这 52 个，对应 `zones` 串最长约 **467 字符**，远低于实测通过的长度；
- 80/150 采用循环拼接（真实 id 重复），用于测字符串长度容忍度，不能证明「150 个不同专区」存在。

**结论**：客户端无需对关注专区串做截断；服务端可安全接受至少 150 个 id / 1349 字符。**通过**。

---

## 3. 多专区集合语义（上线矩阵的关键依据）

「关注」聚合需要把「多个专区的并集」交给服务端。用互不包含的专区 A/B 交叉验证（A、B 单集各取 12/24 条，观察 A,B 与 B,A 两个顺序）：

| 接口 | A,B 结果 | 反向 B,A | 语义 | 对关注聚合 |
|---|---|---|---|---|
| 动态 `user-action/list-v3`（`zoneIds`） | 手办,机甲：10 条中 8 条来自手办集、2 条来自机甲集 | 与新番单集不同（命中并集） | **并集（OR）** | ✅ 可直接用 `zoneIds=逗号串` |
| 分享 `album/list`（`zones`） | 机甲,新番：24 条中 22 条属于机甲单集、2 条属于新番单集 | 与正向一致（22+2） | **并集（OR）** | ✅ 可直接用 `zones=逗号串` |
| 产品 `hobby/query-v2`（`zones`） | 机甲,新番：1 条（该条同时属于两个专区）；手办,新番：1 条 | 与正向一致 | **交集（AND）** | ❌ `zones` 逗号不可用于关注聚合 |
| 二手 `resale/search`（`zones`） | 手办,机甲：0 条（两个单集各 12 条，无同时属于两区的最新二手） | 与正向一致（0） | **交集（AND）** | ❌ `zones` 逗号不可用于关注聚合 |
| 实体 `zone/items`（`zones`） | 手办,新番：works 7363→7413（+50）、charactar 8221→9033（+812） | — | **并集（OR）** | ✅ 可直接用 `zones=逗号串` |
| 讨论 `posts/search` | `zones`/`zoneIds` 被完全忽略；`zoneNodeId` 只接受单值，逗号串**静默退化为全站**（返回全站同一批帖子，不报错） | — | **无多专区通道** | ❌ 关注聚合必须隐藏或客户端多请求合并 |

补充：`nodeId`（实体）与 `zones=<单值>` 等价；`zoneIds` 参数只有动态流识别，其他接口传入被忽略。二手实测 `zoneIds` 通道也被忽略。

---

## 4. 聚合板块上线矩阵（最终结论）

约定：`type` 为 `zone/info.tabs[].type` / 客户端白名单键；「全部」= 首页聚合 scope=all；「关注」= scope=follow。

| 板块 | type | 接口 | 全部聚合（scope=all） | 关注聚合（scope=follow） | 单专区 | 上线结论 |
|---|---|---|---|---|---|---|
| 动态 | `action` | `GET api/user-action/list-v3` | ✅ 省略 zone 参数 = 全站 | ✅ `zoneIds=<关注专区逗号串>`（并集） | ✅ `zoneIds=<nodeId>`；另可带 `zoneNodeId=<nodeId>` | **两种聚合态均可上线** |
| 分享 | `album` | `POST api/album/list` | ✅ 省略 / `zones=` | ✅ `zones=<逗号串>`（并集） | ✅ `zones=<nodeId>` | **两种聚合态均可上线** |
| 讨论 | `posts` | `GET api/posts/search` | ✅ 省略 `zoneNodeId` | ❌ 无多专区通道；逗号串会退化为全站 | ✅ `zoneNodeId=<nodeId>`（String） | **全部可上线；关注隐藏**（或另立多请求合并方案） |
| 产品 | `hobby` | `POST api/hobby/query-v2` | ✅ 省略 `zones`，或带 `homeScope=all`（等价全站） | ⚠️ **不确定**：`zones` 逗号是交集（不可用）；只能依赖 `homeScope=follow`（服务端识别，但真实关注过滤需登录态复验） | ✅ `zones=<nodeId>`，**不传非空 `homeScope`** | **全部可上线；关注暂缓（先隐藏，登录态验证后再开）** |
| 二手 | `resale_item` | `POST api/resale/search` | ✅ 省略 / `zones=` | ❌ `zones` 逗号按交集处理（实测 0 条） | ✅ `zones=<nodeId>` | **全部可上线；关注隐藏** |
| 实体-厂商 | `company` | `GET api/zone/items` | ✅ 省略 / `zones=` | ✅ `zones=<逗号串>`（并集，**不传 `homeScope`**） | ✅ `zones=<nodeId>` 或 `nodeId=<nodeId>` | **两种聚合态均可上线** |
| 实体-系列 | `series` | 同上 | ✅ | ✅ | ✅ | 同上 |
| 实体-作品 | `works` | 同上 | ✅（`total` 服务端封顶 10000） | ✅ | ✅ | 同上 |
| 实体-角色 | `charactar` | 同上 | ✅（封顶 10000） | ✅ | ✅ | 同上 |
| 实体-人物 | `person` | 同上 | ✅（封顶 10000） | ✅ | ✅ | 同上 |
| 商品 | `shop_item` | — | ❌ | ❌ | ❌ | **永远隐藏**（产品决策不做） |
| 社群 | `community` | — | ❌ | ❌ | ❌ | **永远隐藏**（无实现；白名单已排除） |

### 4.1 可上线的聚合板块

- **全部聚合**：`action`、`album`、`posts`、`hobby`、`resale_item`、`company`、`series`、`works`、`charactar`、`person`（共 10 项，正好等于白名单）。
- **关注聚合**：`action`、`album`、`company`、`series`、`works`、`charactar`、`person`（共 7 项）。
  - `hobby` 需登录态复验后才能加入（当前先隐藏）。

### 4.2 需隐藏的聚合板块

- 任一聚合态永久隐藏：`shop_item`、`community`。
- 关注聚合隐藏：`posts`、`resale_item`；`hobby` 暂缓（待复验）。
- 全部聚合无需隐藏任何白名单板块。

### 4.3 参数建议（实现时直接照此拼参）

| 场景 | 动态 | 分享 | 讨论 | 产品 | 二手 | 实体 5 类 |
|---|---|---|---|---|---|---|
| 全部聚合 | 不传 `zoneIds`/`zoneNodeId` | 不传 `zones`（或空串） | 不传 `zoneNodeId` | 不传 `zones`（可带 `homeScope=all`） | 不传 `zones` | 不传 `zones`（可带 `homeScope=all`） |
| 关注聚合 | `zoneIds=<id,id,...>` | `zones=<id,id,...>` | **隐藏** | **暂缓**（如带则 `homeScope=follow`，不得带 `zones`） | **隐藏** | `zones=<id,id,...>`（**不得带 `homeScope`**） |
| 单专区 | `zoneIds=<nodeId>`（+`zoneNodeId`） | `zones=<nodeId>` | `zoneNodeId=<nodeId>` | `zones=<nodeId>`（不传 `homeScope`） | `zones=<nodeId>` | `zones=<nodeId>` 或 `nodeId=<nodeId>` |
| 关注为空 | 客户端短路不发请求（现状） | 同左 | 同左 | 同左 | 同左 | 同左 |

> ⚠️ 两个最容易踩的坑：
> 1. **产品与实体 5 类不能同时传 `zones` 和非空 `homeScope`**——只要 `homeScope` 非空，`zones` 就被忽略，聚合会变成全站（`homeScope=` 空串无害）；
> 2. **讨论板块没有 `zones` 参数**——必须用 `zoneNodeId`，且只能单值；传逗号串会静默返回全站帖子（假「关注」内容），比隐藏更危险。

---

## 5. 对后续 ticket 的直接结论

| Ticket | 结论 |
|---|---|
| **#153** 动态筛选共享定义 | 共享定义必须是 9 项：`recommend/comment/allInfo/confirm/official_pic/preorder/delay/release/reorder`；旧值 `all` 必须映射为 `allInfo`（实测 `all` 会静默变「推荐」）；9 键全部有效 |
| **#157** 分享板块 | 聚合两态均可：全部=省略 `zones`；关注=`zones` 逗号串（并集）；单专区=`zones=<nodeId>`。排序/分类参数不受影响 |
| **#158** 讨论板块 | **参数要从 `zones` 改为 `zoneNodeId`（String）**；全部聚合省略；单专区传单值；**关注聚合无服务端多专区通道**，上线时隐藏该板块（如需保留，客户端按专区逐个请求再合并去重，另行评估分页成本） |
| **#159** 产品板块 | 单专区：`zones=<nodeId>` 且不传 `homeScope`；全部聚合：不传 `zones`（`homeScope=all` 与省略等价）；关注聚合：只能 `homeScope=follow`（服务端识别，但匿名/伪造 token 均回落全站），**需登录态复验通过后才能上线**，否则隐藏 |
| **#160** 二手板块 | 单专区/全部可用；关注聚合不可用（`zones` 逗号=交集，实测 0 条），隐藏 |
| **#161** 实体 5 类 | 单专区与两种聚合都可用：单专区 `zones=<nodeId>`（或 `nodeId`），全部省略，关注 `zones=<逗号串>`（并集）；**不要传 `homeScope`**；注意 works/charactar/person 的 `total` 服务端封顶 10000，翻页逻辑按 `hasMore` 处理 |
| **#162** 聚合二级板块 | 聚合固定 10 项展示顺序可保留，但需按能力矩阵过滤：全部态 10 项全显示；关注态只显示 `action/album/company/series/works/charactar/person`（`hobby` 待定，`posts/resale_item` 隐藏）；`shop_item/community` 永不出现 |

### 5.1 需要同步修订的设计文档（本 ticket 不改产品/设计代码，仅记录）

[docs/home-hierarchy.md](../home-hierarchy.md) 建议由 #162 或后续文档票修订：

1. §1.4「相册/讨论/二手/商品靠 `zones` 参数表达范围」→ 讨论实际是 `zoneNodeId`（String，仅单值）。
2. §4 讨论行：`zones` → `zoneNodeId`；聚合参数列应为「无多专区通道」。
3. §4 产品行与实体行：`zones + homeScope` → **二者互斥**；非空 `homeScope` 会覆盖 `zones`（空串不触发）。
4. §4 二手行：`zones` 聚合参数的实际语义是**交集**，不能用于关注聚合。
5. §3.3/§4 白名单：`zone/info.tabs` 会出现 `shop_item`、`community`，客户端白名单过滤正确，无需扩展。
6. §7.1 验证闸门五项本报告已全部给出结论，可标记完成。

---

## 6. 风险、限制与未决项

| # | 项 | 说明 |
|---|---|---|
| R1 | 关注集合过滤未验证（产品） | 匿名 `utoken` 空串下 `homeScope=follow` 与 `all` 都返回全站；伪造 token 也不报错（200），推测读接口不强制校验 token。真实关注过滤只能登录后验证。**这是「关注聚合产品板块」唯一的不确定项** |
| R2 | 采样时点 | 所有 id 均为 2026-11-15 10:32–11:02 快照；重跑时数值会漂移，但同批次对照（同一分钟内）结论稳定 |
| R3 | 客户端版本差异 | 本次用 `appVersion=200135`（2.43）；RePoi 当前发 `200133`。本报告涉及的参数解析未发现版本敏感项；如后续出现差异，以线上实测为准 |
| R4 | 服务端 `total` 封顶 | works/charactar/person 的 `zone/items` `total` 最大 10000，不可用 `total` 判定「全部数据量」，翻页以 `hasMore` 为准 |
| R5 | 讨论关注态的实现选择 | 隐藏板块 vs 客户端多请求合并（每专区一请求、按游标归并）。后者成本高且难以稳定分页，建议按 ADR-0022「不可用即隐藏」先隐藏 |
| R6 | 产品关注态的实现选择 | 若 #159 实现时能拿到可登录测试 token，优先复验 `homeScope=follow`；不能复验则该板块在关注态隐藏，避免展示全站假数据 |
| R7 | 边角观察（与本票结论无关） | `album/list` 的 `pageSize` 与实际返回条数不完全对应（请求 1/2/3/5 返回 7/10/12/10 条，请求 ≥10 时按请求值返回），疑似服务端另有分页/置顶策略；#157 实现分页时应按现有 `hasMore` 规则实测，不要用本报告推断 |

---

## 附录 A：请求/响应样本（截断）

```text
# 1) 专区全量（匿名）
GET https://www.hpoi.net.cn/api/zone/home-tabs?platform=android&appVersion=200135
    &channel=official&machine=SM-S9280&system=34&utoken=
→ 200 {"success":true,"code":200,"data":{...,"groups":[6 个分组],"zones":[52 个专区],
      "focusZones":[],"homeZones":[],"focusZoneIds":[]}}

# 2) 实体（company）全站 vs 单专区 vs 多专区
GET api/zone/items?...&itemType=company&page=1&pageSize=3&category=0
→ data: {"total":6271,"hasMore":true,"list":[{id:2518,...},{id:2531,...},{id:2515,...}]}
GET ...&itemType=company&...&zones=33476094
→ data: {"total":427,"hasMore":true,"list":[2518,2531,2515,...]}
GET ...&itemType=company&...&zones=33476094,39989602
→ data: {"total":427,...}            # 新番无 company 条目，并集仍为 427
# works 同法：7363 → 7413（+50）；charactar：8221 → 9033（+812）

# 3) 相册（POST form）
POST api/album/list  body: platform=android&...&utoken=&page=1&pageSize=24&order=addTime
→ 200 {"success":true,"data":{"page":1,"list":[{"id":41327307,"zoneIds":"33476094",...},...]}}
# 追加 &zones= → 完全相同的 id 序列；&zones=33476094 → 另一批

# 4) 讨论（GET）
GET api/posts/search?...&page=1&pageSize=12&order=addTime
→ 200 {"success":true,"data":{"list":[{"id":41318137,...},...]}}
# &zones=33476094 / &zones=999999999 → 同一批（zones 被忽略）
# &zoneNodeId=33476094 → 另一批；&zoneNodeId=999999999 → 空；&zoneNodeId=33476094,39989602 → 回落全站

# 5) 产品（POST form）
POST api/hobby/query-v2  body: ...&page=1&pageSize=12&category=10000&order=hits7Day
→ 200 {"success":true,"data":{"list":[{"id":41162582,"zoneIds":[...]},...]}}
# &zones=39989602 → 1 条（40409483）；&zones=39228575 → 另一批 12 条
# &zones=39228575,39989602 → 1 条（交集）；&homeScope=follow 叠加 zones → 回到全站 12 条（zones 被忽略）

# 6) 二手（POST form）
POST api/resale/search  body: ...&type=1&pageSize=12&state=1
→ 200 {"success":true,"data":{"list":[{"id":2400543,"zoneIds":["33476094"],...},...]}}
# &zones=33476094 → 与全站同批；&zones=39228575 → 另一批 12 条
# &zones=33476094,39228575 → 0 条（交集）

# 7) 动态（GET）
GET api/user-action/list-v3?...&categoryId=0&lastTime=0&pageSize=10&subType=allInfo
→ 200 {"success":true,"data":{"list":[{"id":41320531,"actionData":"confirm",...},...]}}
# subType=all → 与 subType=recommend（以及非法值）完全相同的 id 列表
# zoneIds=33476094,39228575 → 10 条：8 条来自手办单集、2 条来自机甲单集（并集）
```

## 附录 B：样本专区清单（10 个）

`39989602 26年10月新番 / 33476094 手办 / 39228575 机甲模型 / 25619129 奥特曼 / 25734924 假面骑士 / 25194324 赛马娘 / 25998424 碧蓝航线 / 39274231 IP作品库 / 39274091 动漫 / 36138460 WF2026上海`

全站专区总数 52（`api/zone/home-tabs.data.zones`），分 6 组：活动/推荐专区、模玩产品、动漫影视IP、游戏IP、文娱爱好、其他。
