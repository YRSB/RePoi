package com.nobody.repoi.test

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.formUrlEncode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Repository 测试用的 MockWebServer 等价替身（Ktor MockEngine 封装）。
 *
 * 老项目 21 个 Repository 测试基于 MockWebServer + Retrofit；
 * KMP 侧统一改为 MockEngine + 真实 `HpoiApi`/`ExHobbyApi`。为最小化改写，
 * 此 helper 复刻 MockWebServer 的核心交互面：
 * - [enqueue]：对应 `server.enqueue(MockResponse().setBody(...))`，按序消费；
 * - [takeRequest]：对应 `server.takeRequest()`，按消费顺序返回请求；
 * - [lastRequest]：快捷取最后一条请求；
 * - [HttpRequestData.recordedPath]：对应 `RecordedRequest.path`（含 query）；
 * - [HttpRequestData.recordedBody]：对应 `RecordedRequest.body.readUtf8()`，
 *   表单体经 [formUrlEncode] 还原为 `k=v&...` 编码串（`parseForm` 零改动）。
 *
 * 多端点测试（老 `Dispatcher` 形态）改用 [respondWhen] 注册 path 规则，
 * 或 [dynamicResponder] 按请求内容动态返回。
 *
 * @param dispatcher MockEngine 的执行调度器。Ktor 默认 `engine.dispatcher = Dispatchers.IO`
 * （真线程）：请求 handler 在 IO 真线程执行，真线程的恢复不受 `advanceUntilIdle()` 驱动——
 * Repository 测试（直接 await）不受影响，但 ViewModel 测试（launch + advanceUntilIdle）
 * 里请求会永远 pending。因此所有 ViewModel 测试必须传入
 * `TestDispatchers.engineDispatcher`（与 testDispatcher 同 scheduler 的虚拟调度器），
 * 使请求 handler 跑在虚拟时钟上（本地 H1/J1 实验证实）。
 */
class MockRepoEngine(
    dispatcher: CoroutineDispatcher? = null
) {
    private data class PlannedResponse(val status: HttpStatusCode, val body: String)

    private val queue = ArrayDeque<PlannedResponse>()
    private val rules = mutableListOf<Pair<Regex, PlannedResponse>>()

    /**
     * 动态响应器（对应“按请求内容动态返回”的老 Dispatcher 形态）。
     * 非空时优先于规则与队列；返回 null 则回落到规则/队列。
     */
    var dynamicResponder: ((HttpRequestData) -> Pair<String, HttpStatusCode>?)? = null
    private var cursor = 0

    val engine = if (dispatcher == null) {
        MockEngine { request -> respondPlanned(request) }
    } else {
        val config = MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler { request -> respondPlanned(request) }
        }
        MockEngine(config)
    }

    private suspend fun io.ktor.client.engine.mock.MockRequestHandleScope.respondPlanned(
        request: HttpRequestData
    ): io.ktor.client.request.HttpResponseData {
        val planned = dispatch(request)
        return respond(
            content = ByteReadChannel(planned.body),
            status = planned.status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    /** 按序入队一个响应（对应 `server.enqueue(...)`）。 */
    fun enqueue(body: String, status: HttpStatusCode = HttpStatusCode.OK) {
        queue.addLast(PlannedResponse(status, body))
    }

    /** 注册 path 正则 → 响应规则（对应老 `Dispatcher`，优先于队列匹配）。 */
    fun respondWhen(pathRegex: Regex, body: String, status: HttpStatusCode = HttpStatusCode.OK) {
        rules.add(pathRegex to PlannedResponse(status, body))
    }

    /** 按消费顺序取出第 N 个请求（对应 `server.takeRequest()`）。 */
    fun takeRequest(): HttpRequestData = engine.requestHistory[cursor++]

    /** 最后一条请求。 */
    fun lastRequest(): HttpRequestData = engine.requestHistory.last()

    /** 已收到的请求总数（对应 `server.requestCount`）。 */
    val requestCount: Int get() = engine.requestHistory.size

    private fun dispatch(request: HttpRequestData): PlannedResponse {
        dynamicResponder?.invoke(request)?.let { (body, status) ->
            return PlannedResponse(status, body)
        }
        val path = request.url.encodedPath
        for ((regex, planned) in rules) {
            if (regex.containsMatchIn(path)) return planned
        }
        check(queue.isNotEmpty()) { "MockRepoEngine: 响应队列已空，无规则命中 $path" }
        return queue.removeFirst()
    }
}

/** 对应 `RecordedRequest.path`：encodedPath + query。 */
val HttpRequestData.recordedPath: String
    get() = url.encodedPathAndQuery

/** 对应 `RecordedRequest.body.readUtf8()`：表单体重编码为 `k=v&...`；空 body 返回空串。 */
fun HttpRequestData.recordedBody(): String {
    val content = body as? FormDataContent ?: return ""
    return content.formData.formUrlEncode()
}
