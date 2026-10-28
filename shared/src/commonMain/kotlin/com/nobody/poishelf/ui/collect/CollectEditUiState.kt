package com.nobody.poishelf.ui.collect

import com.nobody.poishelf.data.remote.model.CollectConstants
import com.nobody.poishelf.data.remote.model.CollectCountData
import com.nobody.poishelf.data.remote.model.CollectData
import com.nobody.poishelf.data.remote.model.CollectTagItem

/**
 * 码点长度（`String.codePointCount(0, length)` 的 common 等价实现）。
 *
 * 代理对计 1（跳过低代理），与原版截断口径一致。
 */
internal fun String.codePointLength(): Int {
    var count = 0
    var i = 0
    while (i < length) {
        val c = this[i++]
        if (c.isHighSurrogate() && i < length && this[i].isLowSurrogate()) i++
        count++
    }
    return count
}

/**
 * 收藏管理与编辑界面 UI 状态机。
 * 遵循单向数据流 (UDF)，所有字段具有默认值。
 */
data class CollectEditUiState(
    val itemId: Long = 0L,
    val nodeId: Long = 0L,
    val name: String? = null,
    val cover: String? = null,

    // 页面整体加载与保存状态
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    val saveSuccess: Boolean = false,
    val deleteSuccess: Boolean = false,

    // 初始收藏状态数据（保留原始信息以便取消收藏时提交）
    val initialCollectData: CollectData? = null,
    val isAlreadyCollected: Boolean = false,

    // 5 种分类人数统计数据
    val countData: CollectCountData = CollectCountData(),

    // 备选推荐标签池
    val suggestedTags: List<String> = emptyList(),

    // ── 当前表单编辑态 ──
    val selectedState: String = CollectConstants.STATE_CARE, // care, want, preorder, buy, resell
    val rating: Int = 0, // 0..5
    val orderTime: String = "", // yyyy-MM-dd
    val orderWay: String = "", // 途径
    val orderMoney: String = "", // 金额
    val orderMoneyCurrency: String = "rmb", // rmb, yen, dollar, euro, hkd
    val buyNum: Int = 1, // 数量
    val orderForm: Int = 1, // 1: 预定, 2: 现货, 3: 二手, 4: 赠品, 0: 其他
    val myTags: List<String> = emptyList(),
    val comment: String = "",

    // 控制 UI 弹窗
    val showDatePicker: Boolean = false,
    val showAddTagDialog: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val tagToDelete: String? = null,
    val showDeleteTagDialog: Boolean = false
) {
    // 按码点计数，与输入截断口径保持一致（common 无 `String.codePointCount`，手动跳过低代理）。
    val commentLength: Int
        get() = comment.codePointLength()

    // 金额仅在预定/已入/有过态校验，空白视为未填
    val isOrderMoneyValid: Boolean
        get() {
            if (selectedState != CollectConstants.STATE_PREORDER &&
                selectedState != CollectConstants.STATE_BUY &&
                selectedState != CollectConstants.STATE_RESELL
            ) return true
            if (orderMoney.isBlank()) return true
            return orderMoney.toDoubleOrNull() != null
        }
    /** 转换为提交给 API 的 CollectData，根据 5 种状态严格清洗入库字段，避免脏数据提交 */
    fun toCollectData(): CollectData {
        val base = (initialCollectData ?: CollectData()).copy(
            state = selectedState,
            rating = rating,
            comment = comment.trim().ifBlank { null },
            textTags = myTags.map { CollectTagItem(it) }
        )

        return when (selectedState) {
            CollectConstants.STATE_CARE, CollectConstants.STATE_WANT -> {
                base.copy(
                    orderTime = null,
                    orderWay = null,
                    orderMoney = null,
                    orderMoneyCurrency = null,
                    buyNum = null,
                    orderForm = 0
                )
            }
            CollectConstants.STATE_PREORDER -> {
                base.copy(
                    orderTime = if (orderTime.isNotBlank()) "$orderTime 00:00:00" else null,
                    orderWay = orderWay.trim().ifBlank { null },
                    orderMoney = orderMoney.toDoubleOrNull(),
                    orderMoneyCurrency = orderMoneyCurrency,
                    buyNum = null,
                    orderForm = 0
                )
            }
            CollectConstants.STATE_BUY -> {
                base.copy(
                    orderTime = if (orderTime.isNotBlank()) "$orderTime 00:00:00" else null,
                    orderWay = orderWay.trim().ifBlank { null },
                    orderMoney = orderMoney.toDoubleOrNull(),
                    orderMoneyCurrency = orderMoneyCurrency,
                    buyNum = buyNum.coerceAtLeast(1),
                    orderForm = orderForm
                )
            }
            CollectConstants.STATE_RESELL -> {
                base.copy(
                    orderTime = if (orderTime.isNotBlank()) "$orderTime 00:00:00" else null,
                    orderWay = orderWay.trim().ifBlank { null },
                    orderMoney = orderMoney.toDoubleOrNull(),
                    orderMoneyCurrency = orderMoneyCurrency,
                    buyNum = null,
                    orderForm = orderForm
                )
            }
            else -> base
        }
    }
}
