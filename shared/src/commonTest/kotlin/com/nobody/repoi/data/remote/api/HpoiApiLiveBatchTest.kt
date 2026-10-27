package com.nobody.repoi.data.remote.api

import com.nobody.repoi.network.appJson
import com.nobody.repoi.network.createHpoiClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Hpoi 真实网络 API 巡检（移植自老项目 `HpoiApiLiveBatchTest`）。
 *
 * 打真网、不进 CI：默认 `@Ignore`，本地手动去掉注解后跑 jvmTest。
 * 表格输出用纯 Kotlin 填充（commonTest 无 `String.format`），计时用 `Clock`。
 */
@OptIn(ExperimentalTime::class)
class HpoiApiLiveBatchTest {

    private data class ApiResult(
        val name: String,
        val endpoint: String,
        val success: Boolean,
        val code: Int,
        val elapsedMs: Long,
        val summary: String,
        val errorMessage: String? = null
    )

    @Ignore("手动巡检：打真网，不进 CI")
    @Test
    fun batchVerifyAllLiveHpoiApis() = runTest {
        val json = appJson()
        val client = createHpoiClient(json = json, tokenProvider = { "" })
        val api = HpoiApi(client, json)
        try {
            println("\n==================== Hpoi 真实网络 API 巡检测试报告 ====================")

            val results = mutableListOf<ApiResult>()

            // 1. 手办库查询
            results.add(testEndpoint("手办库查询", "POST api/hobby/query-v2") {
                val res = api.getHobbyList(page = 1, categoryId = 10000)
                val itemCount = res.data?.list?.size ?: 0
                val firstItem = res.data?.list?.firstOrNull()?.displayName ?: "无"
                Triple(res.success, res.code, "数据量: $itemCount | 首项: $firstItem")
            })

            // 1.1 多维手办库查询 (预订未来窗)
            results.add(testEndpoint("多维手办库查询(预订)", "POST api/hobby/query-v2") {
                val params = mapOf(
                    "order" to "hits7Day",
                    "page" to "1",
                    "pageSize" to "6",
                    "category" to "10000",
                    "hasShopItem" to "1",
                    "releaseStart" to "2026-11-05"
                )
                val res = api.queryHobbyList(params)
                val count = res.data?.list?.size ?: 0
                val first = res.data?.list?.firstOrNull()
                val firstName = first?.displayName ?: "无"
                val firstPrice = first?.displayPriceText ?: "无"
                Triple(res.success, res.code, "预订条目数: $count | 首项: $firstName | 定价: $firstPrice")
            })

            // 1.2 全部分类树
            results.add(testEndpoint("全部分类树", "POST api/category/web/all") {
                val res = api.getAllCategories()
                val count = res.data?.list?.size ?: 0
                Triple(res.success, res.code, "主分类数: $count")
            })

            // 2. 首页动态
            results.add(testEndpoint("首页动态流", "GET api/user-action/list-v3") {
                val res = api.getActionList(categoryId = 0, subType = "recommend", pageSize = 20)
                val count = res.data?.list?.size ?: 0
                val first = res.data?.list?.firstOrNull()?.relateItem?.name ?: "无"
                Triple(res.success, res.code, "数据量: $count 条 | 首动态条目: $first")
            })

            // 3. 手办详情
            results.add(testEndpoint("手办详情", "GET api/item/get-v2") {
                val res = api.getHobbyDetail(itemId = 59570, itemType = "hobby")
                val name = res.data?.itemData?.name ?: "未知"
                Triple(res.success, res.code, "手办名: $name")
            })

            // 4. 结构化属性
            results.add(testEndpoint("结构化属性", "POST api/item/infoList") {
                val res = api.getInfoList(nodeId = 59570, itemType = "hobby")
                val count = res.data?.list?.size ?: 0
                Triple(res.success, res.code, "属性规格数: $count")
            })

            // 5. 相关图片
            results.add(testEndpoint("相关图片", "POST api/pic/list/relate-v2") {
                val res = api.getPicList(itemId = 59570, itemType = "hobby", picSubType = "official")
                val count = res.data?.list?.size ?: 0
                Triple(res.success, res.code, "关联图片数: $count")
            })

            // 6. 相册详情
            results.add(testEndpoint("相册详情", "POST api/album/detail") {
                val res = api.getAlbumDetail(albumId = 100)
                val albumName = res.data?.album?.name ?: "响应正常(特殊Code:${res.code})"
                Triple(res.success || res.code == -9001, res.code, "状态: $albumName")
            })

            // 7. 个人信息
            results.add(testEndpoint("个人信息", "POST api/user/self") {
                val res = api.getUserSelf()
                Triple(res.success || res.code == 4001, res.code, "响应: ${res.msg ?: "需登录"}")
            })

            // 输出格式化报告
            println(
                padEnd("接口名称", 12) + " | " + padEnd("Endpoint", 28) + " | " +
                    padEnd("状态", 6) + " | " + padEnd("Code", 6) + " | " +
                    padEnd("耗时", 8) + " | 数据摘要"
            )
            println("-".repeat(90))

            var passedCount = 0
            results.forEach { r ->
                val statusTag = if (r.success) {
                    passedCount++
                    "PASS"
                } else "FAIL"

                println(
                    padEnd(r.name, 12) + " | " + padEnd(r.endpoint, 28) + " | " +
                        padEnd(statusTag, 6) + " | " + padEnd(r.code.toString(), 6) + " | " +
                        padEnd("${r.elapsedMs}ms", 8) + " | " +
                        r.summary + (r.errorMessage?.let { " [Err: $it]" } ?: "")
                )
            }

            println("=======================================================================")
            println("巡检汇总: 共测试 ${results.size} 个接口 | 成功: $passedCount | 失败: ${results.size - passedCount}")
            println("=======================================================================\n")

            assertTrue(passedCount == results.size, "全部核心 API 均应响应成功(code=200)")
        } finally {
            client.close()
        }
    }

    private suspend fun testEndpoint(
        name: String,
        endpoint: String,
        block: suspend () -> Triple<Boolean, Int, String>
    ): ApiResult {
        val start = Clock.System.now()
        return try {
            val (success, code, summary) = block()
            val elapsed = Clock.System.now() - start
            ApiResult(name, endpoint, success, code, elapsed.inWholeMilliseconds, summary)
        } catch (e: Exception) {
            val elapsed = Clock.System.now() - start
            ApiResult(
                name,
                endpoint,
                false,
                -1,
                elapsed.inWholeMilliseconds,
                "异常: ${e::class.simpleName}",
                e.message
            )
        }
    }

    /** `String.format("%-Ns")` 的纯 Kotlin 等价实现（commonTest 无 JVM format）。 */
    private fun padEnd(s: String, width: Int): String =
        if (s.length >= width) s else s + " ".repeat(width - s.length)
}
