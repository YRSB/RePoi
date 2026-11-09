# 统一收藏领域事件多态流 (Unified Collect Domain Event Stream)

## 状态
Accepted

## 背景与决策
在 RePoi 原有架构中，收藏状态同步存在双轨分立问题：`CollectRepository.collectUpdates` 仅覆盖手办且使用弱类型的 `Pair<Long, CollectData?>`，而画廊跨路由图片失效则依赖独立浅模块 `CollectInvalidationBus`，导致 `PictureGalleryViewModel` 等调用端必须手动编排两次广播，且作品与批量取消完全缺乏广播自愈。

我们决定彻底废除 `CollectInvalidationBus`，由 `CollectRepository` 独占承载统一的强类型热流 `collectEvents: SharedFlow<CollectEvent>`（`replay = 0`, `extraBufferCapacity = 64`, `DROP_OLDEST`）。通过密封层次 `CollectEvent`（覆盖手办变更、通用条目变更、作品变更与批量取消四种多态），所有向服务端的写操作均在仓储层内生自动发射事件，严禁外部手动触发。消费端采用「取消操作内存就地过滤剔除，新增/修改操作局部分类按需刷新」的细粒度自愈策略。

## 备选方案
1. **保留并扩充 CollectInvalidationBus**：在 `util` 层继续新增通用方法。被拒绝，因为收藏失效属于数据仓储的固有生命周期职责，保留工具总线会持续造成职责泄漏与双依赖。
2. **保留 collectUpdates 并添加多流**：为不同业务类型暴露多个独立 `SharedFlow`。被拒绝，多流并发增加了订阅模板代码与维护成本，无法利用单一密封接口的编译期穷尽检查（exhaustive `when`）。

## 影响与后果
- **积极影响**：
  - 彻底消除了浅模块 `CollectInvalidationBus` 与 Koin 冗余注入，跨页面收藏依赖收敛至单一事实来源 `CollectRepository`。
  - 消除 ViewModel 忘记广播的副效应编排风险，写操作成功即保证全站实时自愈。
  - 角色要素收藏链路统一，消除了 `CharacterDetailRepository` 的私有 API 旁路。
- **注意事项**：
  - `UserCollectViewModel`、`HobbyDetailViewModel` 与相关单测需同步迁移至 `collectEvents` 模式匹配。

## 补充决策：活跃分类门控与八类统一 ID 口径（PR #92 评审收口）
消费端的「精准刷新」在初版实现中缺少活跃判定，且取消事件只有 5 类兼容实体 id。评审后补充两条约束：

1. **仅活跃分类立即刷新，其余登记脏标记**：`UserCollectViewModel` 收到新增/修改事件时，若目标分类是当前选中分类则立即 `refresh`；否则仅在**该分类已加载过**（`SubPageData.isLoaded`）时登记到 `UserCollectUiState.staleCategories`，用户切回该分类时由 `switchCategory` 触发一次刷新。这样既不为后台隐藏页签发请求，也不会让切回的列表停留在静置期间的旧数据。
2. **id 口径二选一，不做「两个都比」**：单项取消（`matchEntityId = true`）只比真实实体 id（手办节点 id、相册 `actualAlbumId`、图片 `actualId`、通用条目 `actualItemId`），实体 id 缺失时才回落外层收藏记录 `id`；批量取消（`matchEntityId = false`）只比外层 `id`。避免收藏记录 id 与他条实体 id 数值相同导致误删两条。若内存剔除一条未命中且该分类非活跃，同样登记脏标记兜底。
