package com.nobody.repoi.ui.common.comment

import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDataStores
import io.ktor.client.HttpClient

/**
 * 评论交互委托测试的真仓储装配器（KMP 侧用真实仓储 + [MockRepoEngine] 替代 Mockito）。
 *
 * - 分页测试只需要 delegate 本体（`fetch` lambda 自给），仓储永不触网，
 *   因此 [pagingOnly] 只需构造出合法实例，调用方可不关闭任何资源；
 * - 交互测试需要断言请求与广播，调用方用自己的 engine/真仓储装配，
 *   此处仅提供 engine→delegate 的统一装配入口 [wired]。
 */
internal object FakeCommentDelegates {

    private val json = appJson()

    fun pagingOnly(): CommentInteractionDelegate {
        val stores = TestDataStores()
        val engine = MockRepoEngine()
        val client = HttpClient(engine.engine) { expectSuccess = true }
        val api = HpoiApi(client, json)
        val tokenManager = TokenManager(stores.createDataStore("paging-only"))
        return CommentInteractionDelegate(
            CommentRepository(api, tokenManager),
            StickerRepository(api),
            tokenManager,
            UserRepository(api, tokenManager)
        )
    }

    fun wired(engine: MockRepoEngine, client: HttpClient, tokenManager: TokenManager): CommentInteractionDelegate {
        val api = HpoiApi(client, json)
        return CommentInteractionDelegate(
            CommentRepository(api, tokenManager),
            StickerRepository(api),
            tokenManager,
            UserRepository(api, tokenManager)
        )
    }
}
