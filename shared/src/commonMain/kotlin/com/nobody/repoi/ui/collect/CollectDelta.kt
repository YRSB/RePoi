package com.nobody.repoi.ui.collect

/**
 * 详情页「收藏人数」增量计算。
 *
 * 语义：增量恒为「当前收藏态相对服务端原始收藏态的差值」，**不做逐次累加**，
 * 因此重复订阅状态流、失败回滚都能得到同一个 0 / ±1，不会把计数越算越远。
 *
 * @param originalCollected 服务端返回的原始收藏态（基线）
 * @param currentCollected 界面当前展示的收藏态
 * @return 已收藏 1、已取消 -1、无变化 0
 */
fun collectDeltaOf(originalCollected: Boolean, currentCollected: Boolean): Int = when {
    originalCollected && !currentCollected -> -1
    !originalCollected && currentCollected -> 1
    else -> 0
}
