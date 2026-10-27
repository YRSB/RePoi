package com.nobody.repoi.data.local

import com.nobody.repoi.data.remote.model.CategoryNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CategoryItem(
    val id: Int,
    val name: String,
    val parentId: Int = 0
)

/**
 * 分类管理服务：内置离线基线分类树 + 异步后台拉取缓存。
 * 保证界面启动即刻秒开显示内置分类，无等待白屏。
 *
 * Wave 1 依赖 [CategoryApi] 桩；Wave 3 改接真实 `HpoiApi`。
 */
class CategoryManager(
    private val apiService: CategoryApi
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val defaultPrimaryCategories = listOf(
        CategoryItem(id = 10000, name = "全部"),
        CategoryItem(id = 100, name = "手办"),
        CategoryItem(id = 200, name = "动漫模型"),
        CategoryItem(id = 500, name = "真实模型"),
        CategoryItem(id = 300, name = "娃娃人偶"),
        CategoryItem(id = 400, name = "毛绒布偶")
    )

    private val defaultSubCategories = mapOf(
        10000 to listOf(
            CategoryItem(10000, "全部周边", 10000),
            CategoryItem(100, "手办", 10000),
            CategoryItem(200, "动漫模型", 10000),
            CategoryItem(500, "真实模型", 10000),
            CategoryItem(300, "娃娃人偶", 10000),
            CategoryItem(400, "毛绒布偶", 10000),
            CategoryItem(900, "动漫周边", 10000)
        ),
        100 to listOf(
            CategoryItem(1, "比例人形", 100),
            CategoryItem(3, "Q版人形", 100),
            CategoryItem(4, "盒蛋/扭蛋", 100),
            CategoryItem(8, "配件", 100),
            CategoryItem(9, "场景", 100),
            CategoryItem(10, "其它", 100),
            CategoryItem(11, "生物/幻灵", 100),
            CategoryItem(12, "仿真人物", 100),
            CategoryItem(13, "拼装人形", 100)
        ),
        200 to listOf(
            CategoryItem(101, "其它", 200),
            CategoryItem(102, "机甲/重器", 200),
            CategoryItem(103, "特摄英雄", 200),
            CategoryItem(104, "特摄怪兽", 200),
            CategoryItem(106, "装甲人形", 200),
            CategoryItem(108, "载具/驱动模型", 200),
            CategoryItem(110, "扭蛋/玩具", 200)
        ),
        500 to listOf(
            CategoryItem(401, "完成品", 500),
            CategoryItem(402, "拼装", 500),
            CategoryItem(403, "人形", 500),
            CategoryItem(404, "场景", 500),
            CategoryItem(405, "工具材料", 500),
            CategoryItem(406, "配件", 500)
        ),
        300 to listOf(
            CategoryItem(201, "Doll完成品", 300)
        ),
        400 to listOf(
            CategoryItem(301, "人形毛绒", 400),
            CategoryItem(302, "拟人形毛绒", 400),
            CategoryItem(303, "动植物毛绒", 400)
        ),
        900 to listOf(
            CategoryItem(801, "立牌/摆件", 900),
            CategoryItem(802, "徽章/挂件", 900),
            CategoryItem(803, "海报/装饰画", 900),
            CategoryItem(804, "抱枕/家纺", 900),
            CategoryItem(806, "箱包鞋服", 900),
            CategoryItem(807, "穿戴配饰", 900),
            CategoryItem(808, "日用/文具", 900),
            CategoryItem(809, "数码/配件", 900),
            CategoryItem(810, "拼图/纸模/卡牌", 900),
            CategoryItem(820, "其它周边", 900)
        )
    )

    private val _categoryMap = MutableStateFlow<Map<Int, List<CategoryItem>>>(defaultSubCategories)
    val categoryMap: StateFlow<Map<Int, List<CategoryItem>>> = _categoryMap.asStateFlow()

    init {
        refreshCategoriesAsync()
    }

    fun getSubCategories(parentId: Int): List<CategoryItem> {
        return _categoryMap.value[parentId] ?: defaultSubCategories[parentId] ?: listOf(
            CategoryItem(parentId, "全部", parentId)
        )
    }

    fun getParentId(childId: Int): Int? {
        for (map in listOf(_categoryMap.value, defaultSubCategories)) {
            for ((parentId, children) in map) {
                if (parentId != childId && children.any { it.id == childId }) return parentId
            }
        }
        return null
    }

    fun refreshCategoriesAsync() {
        scope.launch {
            try {
                val response = apiService.getAllCategories()
                if (response.success && response.data?.list != null) {
                    val nodes = response.data.list
                    val newMap = mutableMapOf<Int, MutableList<CategoryItem>>()

                    // 构建二级分类树（注意用业务 categoryId，不用数据库行 id）
                    for (node in nodes) {
                        val pId = node.parentId
                        val list = newMap.getOrPut(pId) { mutableListOf() }
                        list.add(CategoryItem(id = node.categoryId, name = node.name ?: "", parentId = pId))

                        if (!node.children.isNullOrEmpty()) {
                            val subList = newMap.getOrPut(node.categoryId) { mutableListOf() }
                            for (child in node.children) {
                                subList.add(CategoryItem(id = child.categoryId, name = child.name ?: "", parentId = node.categoryId))
                            }
                        }
                    }

                    if (newMap.isNotEmpty()) {
                        // 合并保留默认基准
                        val merged = defaultSubCategories.toMutableMap()
                        newMap.forEach { (k, v) ->
                            if (v.isNotEmpty()) {
                                merged[k] = v
                            }
                        }
                        _categoryMap.value = merged
                    }
                }
            } catch (_: Exception) {
                // 网络异常保持内置默认配置，确保离线高可用
            }
        }
    }
}
