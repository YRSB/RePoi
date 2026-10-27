package com.nobody.repoi.ui.works

import kotlinx.serialization.Serializable

@Serializable
data class WorksNavParam(
    val worksId: Long,
    val initialName: String? = null,
    val initialCover: String? = null
)
