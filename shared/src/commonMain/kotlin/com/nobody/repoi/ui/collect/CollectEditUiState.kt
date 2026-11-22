package com.nobody.repoi.ui.collect

import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.remote.model.CollectCountData
import com.nobody.repoi.data.remote.model.CollectData
import com.nobody.repoi.data.remote.model.CollectTagItem

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

data class CollectEditUiState(
    val itemId: Long = 0L,
    val nodeId: Long = 0L,
    val name: String? = null,
    val cover: String? = null,

    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    val saveSuccess: Boolean = false,
    val deleteSuccess: Boolean = false,

    val initialCollectData: CollectData? = null,
    val isAlreadyCollected: Boolean = false,

    val countData: CollectCountData = CollectCountData(),

    val suggestedTags: List<String> = emptyList(),

    val selectedState: String = CollectConstants.STATE_CARE,
    val rating: Int = 0,
    val orderTime: String = "",
    val orderWay: String = "",
    val orderMoney: String = "",
    val orderMoneyCurrency: String = "rmb",
    val buyNum: Int = 1,
    val orderForm: Int = 1,
    val myTags: List<String> = emptyList(),
    val comment: String = "",

    val showDatePicker: Boolean = false,
    val showAddTagDialog: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val tagToDelete: String? = null,
    val showDeleteTagDialog: Boolean = false
) {

    val commentLength: Int
        get() = comment.codePointLength()

    val isOrderMoneyValid: Boolean
        get() {
            if (selectedState != CollectConstants.STATE_PREORDER &&
                selectedState != CollectConstants.STATE_BUY &&
                selectedState != CollectConstants.STATE_RESELL
            ) return true
            if (orderMoney.isBlank()) return true
            return orderMoney.toDoubleOrNull() != null
        }

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
