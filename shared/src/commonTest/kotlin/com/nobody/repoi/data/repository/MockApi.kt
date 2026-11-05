package com.nobody.repoi.data.repository

import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.remote.api.HpoiApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

/**
 * commonTest MockEngine 脚手架：等价替代 Retrofit `MockWebServer`。
 *
 * - `enqueue(vararg bodies)`：按序返回 JSON 响应体（`Content-Type: application/json`）。
 * - `takeRequest()`：返回按序捕获的请求；[RecordedHttpRequest] 提供
 *   `encodedPath` / `queryParameter` / `formEntries` / `bodyContains` 断言面，
 *   与原 MockWebServer `RecordedRequest` 用法一一对应。
 * - 响应解码走真实 [HpoiApi.decodeRefAware]（无需 ContentNegotiation 插件）。
 * - `api(dispatcher)`：引擎默认跑真实线程（`Dispatchers.IO`），`runTest` 的虚拟时间推不动，
 *   ViewModel 测试必须传入 `testDispatcher`，否则请求零发出且间歇性挂起；纯仓储测试可省略。
 */
class MockApi {

    private data class Queued(val status: HttpStatusCode, val body: String)

    private val bodies = ArrayDeque<Queued>()
    private val requests = MutableStateFlow<List<HttpRequestData>>(emptyList())
    private val routes = mutableListOf<Pair<String, Queued>>()
    private val predicateRoutes = mutableListOf<
        Triple<(path: String, form: Map<String, String>, query: Map<String, String>) -> Boolean, (path: String, form: Map<String, String>, query: Map<String, String>) -> String, HttpStatusCode>
        >()
    private val suspendPredicateRoutes = mutableListOf<
        Triple<(path: String, form: Map<String, String>, query: Map<String, String>) -> Boolean, suspend (path: String, form: Map<String, String>, query: Map<String, String>) -> String, HttpStatusCode>
        >()

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun enqueue(vararg responseBodies: String) {
        bodies.addAll(responseBodies.map { Queued(HttpStatusCode.OK, it) })
    }

    fun enqueue(status: HttpStatusCode, body: String) {
        bodies.add(Queued(status, body))
    }

    /**
     * 按请求路径片段路由（等价 `MockWebServer.dispatcher`）：
     * 若 routes 非空且命中，按命中响应；否则回落 FIFO 队列。
     */
    fun route(pathFragment: String, body: String, status: HttpStatusCode = HttpStatusCode.OK) {
        routes.add(pathFragment to Queued(status, body))
    }

    /**
     * 替换同路径片段路由：首条命中的旧响应会被移除，测试中途切换服务端行为用此方法。
     * 直接二次 `route` 不生效（`firstOrNull` 永远命中首条）。
     */
    fun replaceRoute(pathFragment: String, body: String, status: HttpStatusCode = HttpStatusCode.OK) {
        routes.removeAll { (fragment, _) -> fragment == pathFragment }
        routes.add(pathFragment to Queued(status, body))
    }

    /**
     * 按请求谓词路由（等价“按请求体内容分发”的 Dispatcher 用法）：
     * 命中时返回对应响应；未命中回落 path 路由/FIFO。
     *
     * M7 fix-2：新增 `query` 第三谓词/响应入参（GET 查询串；`request.url.parameters`
     * 单值映射）。既有 2 入参调用点经默认值保持编译兼容；GET 路由改用 `query[...]`
     * 匹配（`encodedPath` 不含查询串，`formOf` 对 GET 恒为空）。
     */
    fun routeWhen(
        predicate: (path: String, form: Map<String, String>, query: Map<String, String>) -> Boolean,
        body: (path: String, form: Map<String, String>, query: Map<String, String>) -> String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) {
        predicateRoutes.add(Triple(predicate, body, status))
    }

    /** 2 入参兼容重载：POST 表单谓词（GET 查询恒为空，调用方无需改动）。 */
    fun routeWhen(
        predicate: (path: String, form: Map<String, String>) -> Boolean,
        body: (path: String, form: Map<String, String>) -> String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) {
        predicateRoutes.add(
            Triple(
                { path, form, _ -> predicate(path, form) },
                { path, form, _ -> body(path, form) },
                status
            )
        )
    }

    /**
     * 挂起谓词路由：body 可 `await()` 门控，确定性复现慢响应后到竞态。
     * 同步 `routeWhen` 内用 `runBlocking` 等门会堵死测试调度线程导致 hang，严禁。
     * 优先级高于同步谓词路由与 path 路由。
     */
    fun routeWhenSuspend(
        predicate: (path: String, form: Map<String, String>, query: Map<String, String>) -> Boolean,
        body: suspend (path: String, form: Map<String, String>, query: Map<String, String>) -> String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) {
        suspendPredicateRoutes.add(Triple(predicate, body, status))
    }

    fun ok(data: String): String =
        """{"success":true,"code":200,"msg":"ok","data":$data}"""

    fun failure(code: Int, msg: String): String =
        """{"success":false,"code":$code,"msg":"$msg","data":null}"""

    fun api(dispatcher: CoroutineDispatcher? = null): HpoiApi {
        val handler: MockRequestHandler = { request ->
            requests.update { it + request }
            val path = request.url.encodedPath
            val form = formOf(request)
            val query = request.url.parameters.entries().associate { (key, values) -> key to (values.firstOrNull().orEmpty()) }
            val suspended = suspendPredicateRoutes.firstOrNull { (predicate, _, _) -> predicate(path, form, query) }
            val predicated = predicateRoutes.firstOrNull { (predicate, _, _) -> predicate(path, form, query) }
            val next = if (suspended != null) {
                Queued(suspended.third, suspended.second(path, form, query))
            } else if (predicated != null) {
                Queued(predicated.third, predicated.second(path, form, query))
            } else {
                val routed = routes.firstOrNull { (fragment, _) -> path.contains(fragment) }?.second
                routed
                    ?: if (bodies.isEmpty()) Queued(HttpStatusCode.OK, failure(500, "MockApi: no enqueued body"))
                    else bodies.removeFirst()
            }
            respond(
                content = next.body,
                status = next.status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val mock = if (dispatcher == null) {
            MockEngine(handler)
        } else {
            MockEngine(MockEngineConfig().apply {
                this.dispatcher = dispatcher
                requestHandlers.add(handler)
            })
        }
        return HpoiApi(HttpClient(mock) {
            expectSuccess = false
        }, json)
    }

    private fun formOf(request: HttpRequestData): Map<String, String> {
        val content = request.body as? FormDataContent ?: return emptyMap()
        return content.formData.entries().associate { (key, values) -> key to (values.firstOrNull().orEmpty()) }
    }

    fun takeRequest(): RecordedHttpRequest =
        RecordedHttpRequest(requests.getAndUpdate { it.drop(1) }.first())

    val requestCount: Int get() = requests.value.size
}

class RecordedHttpRequest internal constructor(private val request: HttpRequestData) {

    val method: String
        get() = request.method.value

    /** 原始 path 含查询串（MockWebServer `request.path` 语义；无查询时不带 `?`）。 */
    val path: String?
        get() {
            val p = request.url.encodedPath
            val q = request.url.encodedQuery
            return (if (q.isEmpty()) p else "$p?$q").ifEmpty { null }
        }

    val encodedPath: String?
        get() = request.url.encodedPath.ifEmpty { null }

    fun queryParameter(name: String): String? = request.url.parameters[name]

    /** POST form 字段（`FormDataContent.formData` 还原；非 form 请求返回空 map）。 */
    fun formEntries(): Map<String, String> {
        val content = request.body as? FormDataContent ?: return emptyMap()
        return content.formData.entries().associate { (key, values) -> key to (values.firstOrNull().orEmpty()) }
    }

    /**
     * 原生 form 编码正文（含百分号转义，如 `releaseStart=2026-09-01+00%3A00%3A00`）。
     * 供需要验证 URL 编码形态的断言使用。
     */
    fun formBodyText(): String {
        val content = request.body as? FormDataContent ?: return ""
        return content.formData.entries().joinToString("&") { (key, values) ->
            values.joinToString("&") { value -> "$key=$value" }
        }
    }

    fun bodyContains(fragment: String): Boolean = formBodyText().contains(fragment)
}
