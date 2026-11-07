# ADR-0005: 登录令牌导入导出与 Token 登录原子验权

## 状态
已采纳并全量执行 (Accepted & Completed) - 2026-11-07

## 上下文与问题陈述
RePoi 仅支持账号密码登录，多设备流转、第三方授权会话迁移、非私人设备避输明文密码等场景无法复用既有 `utoken`。
同时已登录用户无法把当前会话令牌安全导出给另一端使用。

核心约束：
1. 无效令牌绝不能进入半登录态（不落盘、不污染内存档案）；
2. 候选令牌透传不能依赖全局注入快照，避免并发与旧会话污染；
3. 导出链路默认脱敏、防窥屏、可一键复制到跨端剪贴板。

## 候选方案评估
### 方案 A：复用全局注入验权
- 做法：`loginWithToken` 先 `saveToken(candidate)`，再调 `getUserSelf()`（公共参数插件自动带新令牌），失败回滚旧令牌。
- 优点：零 API 新增。
- 缺点：热切换内存镜像存在竞态窗口；失败回滚需处理 DataStore 异步落盘与 `_currentUserProfile` 污染；批量并发下旧会话易被覆盖。

### 方案 B：请求体候选令牌直达验权（采纳）
- 做法：`HpoiApi.getUserSelfWithToken(candidateToken)` 经请求体 `utoken` 显式透传（`merge` 保留调用方同名参数不覆盖），与全局内存镜像解耦；成功才 `saveToken`/`saveCurrentUser` 落盘。
- 优点：验权与旧会话天然隔离，原子性只依赖单次请求结果；失败路径零回滚、零清盘；单测可用 `formEntries()["utoken"]` 直接断言。
- 缺点：新增一个 API 方法（与 `getUserSelf` 共用 `api/user/self` 端点）。

## 决策结论
采用方案 B：
1. `UserRepository.loginWithToken(rawToken)`：trim + 空输入短路（`400 请输入登录令牌`，零请求）；`getUserSelfWithToken` 验权；成功落盘并刷新档案；失败映射友好错误（4001 令牌失效、-1 网络异常透传、其他业务码透传），旧会话与输入保留。
2. `LoginViewModel` 双模状态机：`LoginMode.ACCOUNT/TOKEN` + `token/tokenVisible` + 复用 `isLoading/errorMessage`；`loginWithToken` 失败保留输入。
3. 登录页：Flower 异形 Hero + 连结 `ToggleButton` 双模 + `Crossfade` 表单切换 + Token 表单（遮蔽/显隐/粘贴/清空/计数）。
4. 设置页：`SettingsAccountViewModel` 导出弹窗状态 + `SettingsRepository.activeToken` 只读透传 + `SettingsAccountPane` 安全弹窗（警示横幅、脱敏、`plainTextClipEntry` 复制反馈）。

## 影响
- 积极影响：
  - Token 登录失败零落盘，旧会话不断裂；
  - 候选令牌走请求体，与全局注入并发隔离；
  - 导出默认脱敏，复制链路复用既有跨平台剪贴板。
- 消极影响 / 迁移成本：
  - `LoginContent` 签名扩展（新增双模回调），调用方仅登录页与预览，已同步更新；
  - `SettingsAccountPane` 签名扩展（新增 `activeToken` 与导出回调），调用方仅设置页，已同步更新。
