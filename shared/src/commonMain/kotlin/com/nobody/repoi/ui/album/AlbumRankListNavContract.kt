package com.nobody.repoi.ui.album

import kotlinx.serialization.Serializable

@Serializable
enum class RankPeriod {
    WEEK,
    MONTH,
    YEAR
}


@Serializable
data class AlbumRankListNavParam(
    val title: String,
    val releaseStart: String? = null,
    val releaseEnd: String? = null,
    val praiseType: String? = null,
    val period: RankPeriod? = null
)
