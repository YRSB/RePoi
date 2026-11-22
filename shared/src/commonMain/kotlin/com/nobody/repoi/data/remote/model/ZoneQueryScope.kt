package com.nobody.repoi.data.remote.model

sealed interface ZoneQueryScope {

    data object All : ZoneQueryScope

    data class Follow(val zoneIds: List<Long>) : ZoneQueryScope

    data class Single(val nodeId: Long) : ZoneQueryScope

    val isFollowEmpty: Boolean
        get() = this is Follow && zoneIds.isEmpty()

    fun zonesParam(): String? = when (this) {
        All -> null
        is Follow -> zoneIds.takeIf { it.isNotEmpty() }?.joinToString(",")
        is Single -> nodeId.toString()
    }

    fun zoneNodeId(): Long? = (this as? Single)?.nodeId

    fun stateKey(): String = when (this) {
        All -> "all"
        is Follow -> "follow:" + zoneIds.distinct().sorted().joinToString(",")
        is Single -> "z$nodeId"
    }
}
