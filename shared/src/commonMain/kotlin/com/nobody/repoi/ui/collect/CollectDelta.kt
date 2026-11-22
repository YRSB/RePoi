package com.nobody.repoi.ui.collect

fun collectDeltaOf(originalCollected: Boolean, currentCollected: Boolean): Int = when {
    originalCollected && !currentCollected -> -1
    !originalCollected && currentCollected -> 1
    else -> 0
}
