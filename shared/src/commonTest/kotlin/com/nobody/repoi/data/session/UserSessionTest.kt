package com.nobody.repoi.data.session

import com.nobody.repoi.data.repository.FakeTokenManager
import com.nobody.repoi.data.repository.MockApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 用户会话核心契约测试：只断言外部可观测的状态流与存储结果，不窥探私有字段 / 私有协程。
 *
 * 调度器三件套：`Dispatchers.setMain(testDispatcher)`、`runTest(testDispatcher)`、
 * `mock.api(testDispatcher)`，保证内部预热协程、后台档案刷新与 MockEngine 响应落在同一虚拟时间轴。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserSessionTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun sessionOf(
        mock: MockApi,
        tokenManager: FakeTokenManager = FakeTokenManager(),
    ): UserSessionImpl = UserSessionImpl(mock.api(testDispatcher), tokenManager, testDispatcher)

    @Test
    fun 冷启动恢复本地有效凭据_立即进入已认证且不阻塞网络刷新() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val mock = MockApi()
        mock.routeWhenSuspend(
            predicate = { path, _, _ -> path == "/api/user/self" },
            body = { _, _, _ ->
                gate.await()
                mock.ok("""{"user":{"id":7,"nickname":"胶友","userName":"glue"}}""")
            },
        )
        val tokenManager = FakeTokenManager(token = "tok-1", userId = 7L, userName = "缓存昵称")

        val session = sessionOf(mock, tokenManager)
        // 构造不阻塞：预热完成前维持 Prewarming。
        assertEquals(SessionState.Prewarming, session.state.value)
        advanceUntilIdle()

        // 凭据已就绪即已认证，网络档案仍在后台等待。
        val restored = session.state.value as SessionState.Authenticated
        assertEquals(7L, restored.userId)
        assertEquals("tok-1", restored.token)
        assertNull(restored.profile)
        assertFalse(restored.isProfileLoaded)
        assertTrue(session.isAuthenticated)
        assertEquals("tok-1", session.exportActiveToken())
        assertEquals(1, tokenManager.awaitReadyCalls)

        gate.complete(Unit)
        advanceUntilIdle()

        val refreshed = session.state.value as SessionState.Authenticated
        assertEquals(7L, refreshed.userId)
        assertEquals("tok-1", refreshed.token)
        assertEquals("胶友", refreshed.profile?.nickname)
        assertTrue(refreshed.isProfileLoaded)
        assertEquals(1, mock.requestCount)
    }

    @Test
    fun 冷启动无凭据_进入未认证且不发起网络请求() = runTest(testDispatcher) {
        val mock = MockApi()

        val session = sessionOf(mock)
        advanceUntilIdle()

        assertEquals(SessionState.Unauthenticated, session.state.value)
        assertFalse(session.isAuthenticated)
        assertEquals("", session.currentToken)
        assertEquals(0L, session.currentUserId)
        assertEquals("", session.exportActiveToken())
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun 令牌失效通知_原子清空存储与内存档案且幂等() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/user/self", mock.ok("""{"user":{"id":9,"nickname":"胶友","userName":"glue"}}"""))
        val tokenManager = FakeTokenManager(token = "tok-9", userId = 9L, userName = "缓存昵称")
        val session = sessionOf(mock, tokenManager)
        advanceUntilIdle()
        assertEquals("胶友", (session.state.value as SessionState.Authenticated).profile?.nickname)

        session.notifyTokenExpired()

        assertEquals(SessionState.Unauthenticated, session.state.value)
        assertEquals("", tokenManager.currentToken)
        assertEquals(0L, tokenManager.currentUserId)
        assertEquals("", tokenManager.currentUserName)
        assertEquals(1, tokenManager.clearTokenCalls)
        assertFalse(session.isAuthenticated)
        assertEquals("", session.exportActiveToken())

        // 幂等：重复通知不再重复清理存储。
        session.notifyTokenExpired()
        assertEquals(SessionState.Unauthenticated, session.state.value)
        assertEquals(1, tokenManager.clearTokenCalls)
    }

    @Test
    fun 令牌失效通知_携带不匹配凭据时忽略清理_避免误伤新会话() = runTest(testDispatcher) {
        val mock = MockApi()
        val tokenManager = FakeTokenManager(token = "new-token", userId = 10L, userName = "新用户")
        val session = sessionOf(mock, tokenManager)
        advanceUntilIdle()

        // 旧在途请求携带 old-token 收到 4001
        session.notifyTokenExpired(triggeredToken = "old-token")

        // 当前会话保持 Authenticated，存储未被清空
        assertTrue(session.isAuthenticated)
        assertEquals("new-token", session.currentToken)
        assertEquals(0, tokenManager.clearTokenCalls)

        // 匹配当前 token 的失效通知则正常清理
        session.notifyTokenExpired(triggeredToken = "new-token")
        assertFalse(session.isAuthenticated)
        assertEquals(1, tokenManager.clearTokenCalls)
    }

    @Test
    fun 账号登录成功_切换已认证并持久化凭据() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.routeWhen(
            predicate = { path, form -> path == "/api/user/loginV2" && form["account"] == "alice" },
            body = { _, _ ->
                mock.ok("""{"utoken":"token-a","user":{"id":31,"nickname":"爱丽丝","userName":"alice"}}""")
            },
        )
        mock.route("api/user/self", mock.ok("""{"user":{"id":31,"nickname":"爱丽丝","userName":"alice"}}"""))
        val tokenManager = FakeTokenManager()
        val session = sessionOf(mock, tokenManager)
        advanceUntilIdle()
        assertEquals(SessionState.Unauthenticated, session.state.value)

        val result = session.login("alice", "pwd-123")

        assertTrue(result.isSuccess)
        assertEquals("token-a", result.getOrThrow().utoken)
        assertEquals("token-a", tokenManager.currentToken)
        assertEquals(31L, tokenManager.currentUserId)
        val authenticated = session.state.value as SessionState.Authenticated
        assertEquals(31L, authenticated.userId)
        assertEquals("token-a", authenticated.token)
        assertEquals("token-a", session.exportActiveToken())

        // 后台静默刷新补齐档案，不影响登录结果本身。
        advanceUntilIdle()
        assertEquals("爱丽丝", (session.state.value as SessionState.Authenticated).profile?.displayName)
    }

    @Test
    fun 账号登录失败_不改动现有会话与存储() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/user/self", mock.ok("""{"user":{"id":5,"nickname":"旧用户","userName":"old"}}"""))
        mock.route("api/user/loginV2", mock.failure(1001, "账号或密码错误"))
        val tokenManager = FakeTokenManager(token = "old-token", userId = 5L)
        val session = sessionOf(mock, tokenManager)
        advanceUntilIdle()
        val before = session.state.value as SessionState.Authenticated

        val result = session.login("alice", "wrong")

        assertTrue(result.isFailure)
        assertEquals("账号或密码错误", result.exceptionOrNull()?.message)
        assertEquals(before, session.state.value)
        assertEquals("old-token", tokenManager.currentToken)
        assertEquals(5L, tokenManager.currentUserId)
    }

    @Test
    fun Token登录成功_持久化凭据与档案并切换已认证() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.enqueue(mock.ok("""{"user":{"id":88888,"nickname":"资深胶佬","userName":"collector888"}}"""))
        val tokenManager = FakeTokenManager()
        val session = sessionOf(mock, tokenManager)
        advanceUntilIdle()

        val result = session.loginWithToken("candidate-token")

        assertTrue(result.isSuccess)
        assertEquals(88888L, result.getOrThrow().id)
        assertEquals("candidate-token", tokenManager.currentToken)
        assertEquals(88888L, tokenManager.currentUserId)
        assertEquals("资深胶佬", tokenManager.currentUserName)
        val authenticated = session.state.value as SessionState.Authenticated
        assertEquals(88888L, authenticated.userId)
        assertEquals("candidate-token", authenticated.token)
        assertEquals(88888L, authenticated.profile?.id)
        val request = mock.takeRequest()
        assertEquals("/api/user/self", request.encodedPath)
        assertEquals("candidate-token", request.formEntries()["utoken"])
    }

    @Test
    fun Token登录空令牌_失败且零请求零落盘() = runTest(testDispatcher) {
        val mock = MockApi()
        val tokenManager = FakeTokenManager()
        val session = sessionOf(mock, tokenManager)
        advanceUntilIdle()
        val before = session.state.value

        val result = session.loginWithToken("   \n ")

        assertTrue(result.isFailure)
        assertEquals("请输入登录令牌", result.exceptionOrNull()?.message)
        assertEquals(before, session.state.value)
        assertEquals(0, mock.requestCount)
        assertEquals("", tokenManager.currentToken)
        assertEquals(0, tokenManager.saveTokenCalls)
    }

    @Test
    fun Token登录4001_返回友好错误且保留旧会话不落盘() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.routeWhen(
            predicate = { path, form -> path == "/api/user/self" && form["utoken"] == "expired-token" },
            body = { _, _ -> mock.failure(4001, "登录凭证已过期") },
        )
        mock.route("api/user/self", mock.ok("""{"user":{"id":5,"nickname":"旧用户","userName":"old"}}"""))
        val tokenManager = FakeTokenManager(token = "old-token", userId = 5L)
        val session = sessionOf(mock, tokenManager)
        advanceUntilIdle()
        val before = session.state.value

        val result = session.loginWithToken("expired-token")

        assertTrue(result.isFailure)
        assertEquals("登录令牌已失效，请重新获取", result.exceptionOrNull()?.message)
        assertEquals(before, session.state.value)
        assertEquals("old-token", tokenManager.currentToken)
        assertEquals(0, tokenManager.saveTokenCalls)
        assertEquals(0, tokenManager.clearTokenCalls)
    }

    @Test
    fun Token登录网络异常_映射友好错误且保留旧会话不落盘() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.routeWhenSuspend(
            predicate = { path, form, _ -> path == "/api/user/self" && form["utoken"] == "broken-token" },
            body = { _, _, _ -> throw IllegalStateException("连接被重置") },
        )
        mock.route("api/user/self", mock.ok("""{"user":{"id":5,"nickname":"旧用户","userName":"old"}}"""))
        val tokenManager = FakeTokenManager(token = "old-token", userId = 5L)
        val session = sessionOf(mock, tokenManager)
        advanceUntilIdle()
        val before = session.state.value

        val result = session.loginWithToken("broken-token")

        assertTrue(result.isFailure)
        assertEquals("网络连接异常，请检查后重试", result.exceptionOrNull()?.message)
        assertEquals(before, session.state.value)
        assertEquals("old-token", tokenManager.currentToken)
        assertEquals(0, tokenManager.saveTokenCalls)
    }

    @Test
    fun Token登录业务错误_透传服务端消息且保留旧会话不落盘() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.routeWhen(
            predicate = { path, form -> path == "/api/user/self" && form["utoken"] == "unknown-token" },
            body = { _, _ -> mock.failure(503, "服务暂不可用") },
        )
        mock.route("api/user/self", mock.ok("""{"user":{"id":5,"nickname":"旧用户","userName":"old"}}"""))
        val tokenManager = FakeTokenManager(token = "old-token", userId = 5L)
        val session = sessionOf(mock, tokenManager)
        advanceUntilIdle()
        val before = session.state.value

        val result = session.loginWithToken("unknown-token")

        assertTrue(result.isFailure)
        assertEquals("服务暂不可用", result.exceptionOrNull()?.message)
        assertEquals(before, session.state.value)
        assertEquals("old-token", tokenManager.currentToken)
        assertEquals(0, tokenManager.saveTokenCalls)
    }

    @Test
    fun 登出_原子清空存储与内存状态() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/user/self", mock.ok("""{"user":{"id":8,"nickname":"胶友","userName":"glue"}}"""))
        val tokenManager = FakeTokenManager(token = "tok-8", userId = 8L, userName = "缓存昵称")
        val session = sessionOf(mock, tokenManager)
        advanceUntilIdle()
        assertTrue(session.isAuthenticated)

        session.logout()

        assertEquals(SessionState.Unauthenticated, session.state.value)
        assertEquals("", tokenManager.currentToken)
        assertEquals(0L, tokenManager.currentUserId)
        assertEquals("", tokenManager.currentUserName)
        assertEquals(1, tokenManager.clearTokenCalls)
        assertEquals("", session.exportActiveToken())
    }

    @Test
    fun 刷新档案遇4001_触发失效自愈并清空会话() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/user/self", mock.ok("""{"user":{"id":6,"nickname":"旧用户","userName":"old"}}"""))
        val tokenManager = FakeTokenManager(token = "tok-6", userId = 6L)
        val session = sessionOf(mock, tokenManager)
        advanceUntilIdle()
        assertTrue(session.isAuthenticated)

        mock.replaceRoute("api/user/self", mock.failure(4001, "登录已过期"))
        val result = session.refreshProfile()

        assertTrue(result.isFailure)
        assertEquals(SessionState.Unauthenticated, session.state.value)
        assertEquals("", tokenManager.currentToken)
        assertEquals(1, tokenManager.clearTokenCalls)
    }

    @Test
    fun 刷新档案_解析全部扩展字段并写回已认证状态() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route(
            "api/user/self",
            """{"success": true,"code": 200,"msg": "ok","data": {"user": {"id": 88888,"nickname": "资深胶佬","userName": "collector888","sign": "入坑十年","intro": "专注比例手办与雕像收藏","header": "header/collector.jpg","bnr": "banner/collector.jpg","level": 6,"sex": 1,"birth": "1995-08-18","city": "上海","regTime": "2016-04-20 12:00:00","addTime": "2016-04-20","verify": "知名手办摄影师"}}}"""
        )
        val tokenManager = FakeTokenManager(token = "tok-1", userId = 1L)
        val session = sessionOf(mock, tokenManager)
        advanceUntilIdle()

        val result = session.refreshProfile()

        assertTrue(result.isSuccess, result.exceptionOrNull()?.toString())
        val profile = result.getOrThrow()
        assertEquals(88888L, profile.id)
        assertEquals("资深胶佬", profile.nickname)
        assertEquals("collector888", profile.userName)
        assertEquals("资深胶佬", profile.displayName)
        assertEquals("入坑十年", profile.sign)
        assertEquals("专注比例手办与雕像收藏", profile.intro)
        assertEquals("header/collector.jpg", profile.header)
        assertEquals("banner/collector.jpg", profile.bnr)
        assertEquals(6, profile.level)
        assertEquals(1, profile.sex)
        assertEquals("男", profile.genderText)
        assertEquals("1995-08-18", profile.birth)
        assertEquals("上海", profile.city)
        assertEquals("2016-04-20 12:00:00", profile.regTime)
        assertEquals("2016-04-20", profile.addTime)
        assertEquals("2016-04-20 12:00:00", profile.displayRegisterTime)
        assertEquals("知名手办摄影师", profile.verify)
        assertTrue(profile.isVerified)

        // 性别的另外取值与未认证兜底
        val femaleProfile = profile.copy(sex = 0, verify = null)
        assertEquals("女", femaleProfile.genderText)
        assertFalse(femaleProfile.isVerified)

        val secretProfile = profile.copy(sex = 2, verify = "")
        assertEquals("保密", secretProfile.genderText)
        assertFalse(secretProfile.isVerified)

        val unsetProfile = profile.copy(sex = null)
        assertEquals("未设置", unsetProfile.genderText)

        // 注册时间回退到 addTime
        val addTimeOnlyProfile = profile.copy(regTime = null)
        assertEquals("2016-04-20", addTimeOnlyProfile.displayRegisterTime)

        // 档案已写回会话状态
        val authenticated = session.state.value as SessionState.Authenticated
        assertEquals(88888L, authenticated.userId)
        assertEquals(profile, authenticated.profile)
        assertEquals(88888L, tokenManager.currentUserId)
    }
}
