package com.nobody.repoi.data.local

import com.nobody.repoi.data.remote.model.CategoryTreeData
import com.nobody.repoi.data.remote.model.CommonResponse

/**
 * 分类树 API 最小契约（Wave 1 桩）。
 *
 * `CategoryManager` 对 API 层的唯一依赖是 `getAllCategories()`；
 * Wave 3 用真实 `HpoiApi` 适配（`CategoryApi { hpoiApi.getAllCategories() }`）。
 */
fun interface CategoryApi {
    suspend fun getAllCategories(): CommonResponse<CategoryTreeData>
}
