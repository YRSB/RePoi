package com.nobody.repoi.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KtorLogFormatterTest {

    @Test
    fun formatRequest_extractsMethodAndUrl() {
        val rawRequest = """
            REQUEST: https://www.hpoi.net.cn/api/user/self
            METHOD: POST
            COMMON HEADERS
            -> Accept: application/json
            -> Referer: https://www.hpoi.net/android
            -> User-Agent: okhttp/4.12.0
            CONTENT HEADERS
            -> Content-Length: 121
            -> Content-Type: application/x-www-form-urlencoded; charset=UTF-8
        """.trimIndent()

        val formatted = KtorLogFormatter.format(rawRequest)
        assertEquals("--> POST https://www.hpoi.net.cn/api/user/self", formatted)
    }

    @Test
    fun formatResponse_extractsStatusAndUrl() {
        val rawResponse = """
            RESPONSE: 200 
            METHOD: GET
            FROM: https://www.hpoi.net.cn/api/user-action/list-v3?categoryId=0&pageSize=20
            COMMON HEADERS
            -> access-control-allow-credentials: true
            -> connection: keep-alive
            -> content-type: application/json;charset=utf-8
            -> date: Wed, 04 Nov 2026 02:56:31 GMT
        """.trimIndent()

        val formatted = KtorLogFormatter.format(rawResponse)
        assertEquals("<-- 200 https://www.hpoi.net.cn/api/user-action/list-v3?categoryId=0&pageSize=20", formatted)
    }

    @Test
    fun formatBody_extractsJsonContent() {
        val rawBody = """
            BODY Content-Type: application/json;charset=utf-8
            BODY START
            {"status":1,"datas":[{"id":123,"title":"手办"}]}
            BODY END
        """.trimIndent()

        val formatted = KtorLogFormatter.format(rawBody)
        assertEquals("""[Body] {"status":1,"datas":[{"id":123,"title":"手办"}]}""", formatted)
    }

    @Test
    fun formatBody_truncatesOversizedBody() {
        val longJson = "a".repeat(5000)
        val rawBody = "BODY START\n$longJson\nBODY END"

        val formatted = KtorLogFormatter.format(rawBody)
        assertTrue(formatted != null && formatted.startsWith("[Body] "))
        assertTrue(formatted.contains("... (共 5000 字符，已截断)"))
    }

    @Test
    fun formatHeaders_filtersOutNoise() {
        assertNull(KtorLogFormatter.format("COMMON HEADERS\n-> Accept: application/json"))
        assertNull(KtorLogFormatter.format("CONTENT HEADERS\n-> Content-Length: 0"))
        assertNull(KtorLogFormatter.format("RESPONSE HEADERS\n-> server: nginx"))
        assertNull(KtorLogFormatter.format("-> connection: keep-alive"))
        assertNull(KtorLogFormatter.format("BODY Content-Type: image/png"))
        assertNull(KtorLogFormatter.format("   "))
    }

    @Test
    fun formatException_preservesFailure() {
        val errorMsg = "REQUEST https://www.hpoi.net.cn/api/user/self failed with exception: java.net.SocketTimeoutException: timeout"
        assertEquals(errorMsg, KtorLogFormatter.format(errorMsg))
    }
}
