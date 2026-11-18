package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.StickerPackData

class StickerRepository(
    private val api: HpoiApi
) {
    private var cachedPacks: List<StickerPackData>? = null
    private var cachedBuyTime: String? = null

    suspend fun getCommentPacks(): Result<List<StickerPackData>> = runCatching {
        val serverBuyTime = api.getStickerBuyTime()?.buyTime
        val cached = cachedPacks
        if (!cached.isNullOrEmpty() && serverBuyTime != null && serverBuyTime == cachedBuyTime) {
            return@runCatching cached
        }
        val packsPayload = api.getStickerPacks()
        val packs = packsPayload.list.orEmpty()
            .filter { it.applyLocation <= 0 || it.applyLocation == 1 }
        cachedPacks = packs
        cachedBuyTime = packsPayload.buyTime ?: serverBuyTime
        packs
    }
}
