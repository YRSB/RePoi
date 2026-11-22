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
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import com.nobody.repoi.data.remote.plugin.installCommonParams
import kotlinx.serialization.json.Json
import kotlin.test.assertTrue

class MockApi {

    private data class Queued(val status: HttpStatusCode, val body: String)

    private val bodies = ArrayDeque<Queued>()

    private val expiredTokens = mutableListOf<String>()
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

    fun route(pathFragment: String, body: String, status: HttpStatusCode = HttpStatusCode.OK) {
        routes.add(pathFragment to Queued(status, body))
    }

    fun replaceRoute(pathFragment: String, body: String, status: HttpStatusCode = HttpStatusCode.OK) {
        routes.removeAll { (fragment, _) -> fragment == pathFragment }
        routes.add(pathFragment to Queued(status, body))
    }

    inline fun <reified T> routeJson(
        pathFragment: String,
        data: T,
        status: HttpStatusCode = HttpStatusCode.OK
    ) {
        route(pathFragment, json.encodeToString(data), status)
    }

    inline fun <reified T> replaceRouteJson(
        pathFragment: String,
        data: T,
        status: HttpStatusCode = HttpStatusCode.OK
    ) {
        replaceRoute(pathFragment, json.encodeToString(data), status)
    }

    inline fun <reified T> okJson(data: T, msg: String = "ok", code: Int = 200): String {
        return """{"success":true,"code":$code,"msg":"$msg","data":${json.encodeToString(data)}}"""
    }

    inline fun <reified T> routeCommonResponse(
        pathFragment: String,
        data: T,
        msg: String = "ok",
        code: Int = 200,
        status: HttpStatusCode = HttpStatusCode.OK
    ) {
        route(pathFragment, okJson(data, msg, code), status)
    }

    inline fun <reified T> replaceRouteCommonResponse(
        pathFragment: String,
        data: T,
        msg: String = "ok",
        code: Int = 200,
        status: HttpStatusCode = HttpStatusCode.OK
    ) {
        replaceRoute(pathFragment, okJson(data, msg, code), status)
    }

    fun routeWhen(
        predicate: (path: String, form: Map<String, String>, query: Map<String, String>) -> Boolean,
        body: (path: String, form: Map<String, String>, query: Map<String, String>) -> String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) {
        predicateRoutes.add(Triple(predicate, body, status))
    }

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

    fun api(
        dispatcher: CoroutineDispatcher? = null,
        tokenProvider: (() -> String)? = null,
        onTokenExpired: (suspend (String) -> Unit)? = null,
    ): HpoiApi {
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
        val client = HttpClient(mock) {
            expectSuccess = false
        }
        if (tokenProvider != null) client.installCommonParams(tokenProvider)
        return HpoiApi(client, json) { token ->
            expiredTokens.add(token)
            onTokenExpired?.invoke(token)
        }
    }

    fun assertTokenExpiredNotified(expectedToken: String) {
        assertTrue(expectedToken in expiredTokens, "期望收到令牌 $expectedToken 的失效通知，实际为 $expiredTokens")
    }

    fun assertNoTokenExpiredNotified() {
        assertTrue(expiredTokens.isEmpty(), "不应触发令牌失效通知，实际为 $expiredTokens")
    }

    private fun formOf(request: HttpRequestData): Map<String, String> {
        val content = request.body as? FormDataContent ?: return emptyMap()
        return content.formData.entries().associate { (key, values) -> key to (values.firstOrNull().orEmpty()) }
    }

    fun takeRequest(): RecordedHttpRequest =
        RecordedHttpRequest(requests.getAndUpdate { it.drop(1) }.first())

    fun lastRequest(): RecordedHttpRequest =
        RecordedHttpRequest(requests.value.last())

    val requestCount: Int get() = requests.value.size
}

class RecordedHttpRequest internal constructor(private val request: HttpRequestData) {

    val method: String
        get() = request.method.value

    val path: String?
        get() {
            val p = request.url.encodedPath
            val q = request.url.encodedQuery
            return (if (q.isEmpty()) p else "$p?$q").ifEmpty { null }
        }

    val encodedPath: String?
        get() = request.url.encodedPath.ifEmpty { null }

    fun queryParameter(name: String): String? = request.url.parameters[name]

    val headers: Headers
        get() = request.headers

    fun header(name: String): String? = request.headers[name]

    fun formEntries(): Map<String, String> {
        val content = request.body as? FormDataContent ?: return emptyMap()
        return content.formData.entries().associate { (key, values) -> key to (values.firstOrNull().orEmpty()) }
    }

    fun formBodyText(): String {
        val content = request.body as? FormDataContent ?: return ""
        return content.formData.entries().joinToString("&") { (key, values) ->
            values.joinToString("&") { value -> "$key=$value" }
        }
    }

    fun bodyContains(fragment: String): Boolean = formBodyText().contains(fragment)
}
