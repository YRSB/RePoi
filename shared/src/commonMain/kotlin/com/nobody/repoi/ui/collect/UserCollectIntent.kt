package com.nobody.repoi.ui.collect

import com.nobody.repoi.data.remote.model.UserCollectCategory

/**
 * 收藏中心用户意图全集（MVI 单向数据流）。
 *
 * 所有用户操作只表达"发生了什么"，由 [UserCollectViewModel.onIntent] 统一归约为状态变更；
 * 视图层不再直接调用若干状态改写方法，也不再自行拼装请求参数。
 *
 * @see UserCollectUiState 唯一对外状态
 */
sealed interface UserCollectIntent {

    // ===== 大类与视图 =====

    /** 切换实体大类 Tab */
    data class SwitchCategory(val category: UserCollectCategory) : UserCollectIntent

    /** 切换周边页网格/列表视图模式 */
    data object ToggleViewMode : UserCollectIntent

    // ===== 周边页：收藏状态 / 排序 / 筛选 =====

    /** 切换周边收藏状态（全部/关注/想买/预定/已入/有过） */
    data class SwitchCollectState(val state: String) : UserCollectIntent

    /** 切换周边排序维度 */
    data class SwitchOrder(val order: String) : UserCollectIntent

    /**
     * 切换周边升降序（1 升序 / 2 降序）。
     *
     * 契约文件里的示意写法为 `data object`，但现有 UI 通过 `onSwitchSortDirection(Int)` 传入目标值，
     * 无字段将无法表达"翻转"，故此处携带升降序取值，语义与重构前一致。
     */
    data class SwitchSortDirection(val sortType: Int) : UserCollectIntent

    /** 选择周边细分子分类（`null` / 10000 均表示"全部"） */
    data class SelectSubCategory(val id: Int?) : UserCollectIntent

    /** 选择/反选周边标签过滤 */
    data class SelectTag(val tag: String?) : UserCollectIntent

    /** 重置周边全部筛选条件 */
    data object ResetFilter : UserCollectIntent

    /** 控制周边筛选抽屉显隐（打开时懒加载标签候选池） */
    data class SetFilterSheetVisible(val visible: Boolean) : UserCollectIntent

    /**
     * 懒加载周边标签候选池。
     *
     * 抽屉打开时由 ViewModel 内部自动触发；[force] 为 true 时无视已有缓存强制重拉。
     */
    data class LoadHobbyTags(val force: Boolean) : UserCollectIntent

    // ===== 各大类排序 =====

    /** 切换相册排序 */
    data class SwitchAlbumOrder(val order: String) : UserCollectIntent

    /** 切换图片排序 */
    data class SwitchPicOrder(val order: String) : UserCollectIntent

    /** 切换通用 4 页（厂商/系列/角色/人物）排序 */
    data class SwitchCommonOrder(val order: String) : UserCollectIntent

    // ===== 作品页 =====

    /** 切换作品收藏状态（1 想看 / 2 在看 / 3 看过） */
    data class SwitchWorksState(val state: Int) : UserCollectIntent

    /** 切换作品页收藏时间升降序 */
    data class SwitchWorksOrderAsc(val asc: Boolean) : UserCollectIntent

    /** 选择/反选作品标签过滤 */
    data class SelectWorksTag(val tag: String?) : UserCollectIntent

    /** 重置作品标签过滤 */
    data object ResetWorksTag : UserCollectIntent

    /** 控制作品筛选弹层显隐（打开时懒加载作品标签候选池） */
    data class SetWorksFilterSheetVisible(val visible: Boolean) : UserCollectIntent

    /** 懒加载作品页标签候选池（[force] 为 true 时无视已有缓存强制重拉） */
    data class LoadWorksTags(val force: Boolean) : UserCollectIntent

    // ===== 分页装载 =====

    /** 装载指定大类的第一页（首次进入 / 重试） */
    data class LoadFirstPage(val category: UserCollectCategory) : UserCollectIntent

    /** 下拉刷新指定大类 */
    data class Refresh(val category: UserCollectCategory) : UserCollectIntent

    /** 加载指定大类的下一页 */
    data class LoadNextPage(val category: UserCollectCategory) : UserCollectIntent

    /** 清除指定大类的错误提示 */
    data class DismissError(val category: UserCollectCategory) : UserCollectIntent

    // ===== 多选与批量取消 =====

    /** 进入/退出指定大类的多选模式（仅周边页与相册页有效） */
    data class ToggleSelectionMode(val category: UserCollectCategory) : UserCollectIntent

    /** 选中/反选单条（入参为外层收藏节点 id） */
    data class ToggleSelect(val id: Long) : UserCollectIntent

    /** 全选指定大类已加载的条目 */
    data class SelectAll(val category: UserCollectCategory) : UserCollectIntent

    /** 清空指定大类的选中集合并退出多选模式 */
    data class ClearSelection(val category: UserCollectCategory) : UserCollectIntent

    /** 提交批量取消收藏 */
    data object ConfirmBatchCancel : UserCollectIntent

    /** 清除批量取消收藏的错误提示 */
    data object DismissBatchCancelError : UserCollectIntent

    /**
     * 页面恢复感知：比对画廊写操作计数（脏位），有变化则静默刷新图片大类。
     *
     * 由 `UserCollectScreen` 的 `LifecycleEventEffect(ON_RESUME)` 驱动（跳过首次进入）。
     */
    data object OnResume : UserCollectIntent
}
