package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ApiException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class MockApiTest {

    @Test
    fun 令牌失效通知_按在途快照收集并断言() = runTest {
        val mock = MockApi()
        mock.route("api/user/self", mock.failure(4001, "登录已过期"))
        val api = mock.api(tokenProvider = { "tok-42" })

        assertFailsWith<ApiException> { api.getUserSelf() }

        mock.assertTokenExpiredNotified("tok-42")
    }

    @Test
    fun 成功响应_不产生令牌失效通知() = runTest {
        val mock = MockApi()
        mock.route("api/user/self", mock.ok("""{"user":{"id":1}}"""))
        val api = mock.api(tokenProvider = { "tok-42" })

        api.getUserSelf()

        mock.assertNoTokenExpiredNotified()
    }

    @Test
    fun 候选验权旁路_失败也不产生令牌失效通知() = runTest {
        val mock = MockApi()
        mock.route("api/user/self", mock.failure(4001, "登录凭证已过期"))
        val api = mock.api(tokenProvider = { "active-token" })

        assertFailsWith<ApiException> { api.getUserSelfWithToken("candidate-token") }

        mock.assertNoTokenExpiredNotified()
    }
}
