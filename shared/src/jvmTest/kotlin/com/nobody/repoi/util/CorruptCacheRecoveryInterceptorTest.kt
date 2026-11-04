package com.nobody.repoi.util

import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.size.Size
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CorruptCacheRecoveryInterceptorTest {

    private class FakeDiskCache : DiskCache {
        val removedKeys = mutableListOf<String>()

        override val size: Long get() = 0L
        override val maxSize: Long get() = 0L
        override val directory: Path get() = "/tmp".toPath()
        override val fileSystem: FileSystem get() = FileSystem.SYSTEM

        override fun openSnapshot(key: String): DiskCache.Snapshot? = null
        override fun openEditor(key: String): DiskCache.Editor? = null
        override fun remove(key: String): Boolean {
            removedKeys.add(key)
            return true
        }
        override fun clear() {
            removedKeys.clear()
        }
        override fun shutdown() {}
    }

    private class FakeChain(
        initialRequest: ImageRequest,
        private val results: MutableList<(ImageRequest) -> ImageResult>
    ) : Interceptor.Chain {
        var currentRequest: ImageRequest = initialRequest
        val executedRequests = mutableListOf<ImageRequest>()

        override val request: ImageRequest get() = currentRequest
        override val size: Size get() = Size.ORIGINAL

        override fun withRequest(request: ImageRequest): Interceptor.Chain {
            currentRequest = request
            return this
        }

        override fun withSize(size: Size): Interceptor.Chain = this

        override suspend fun proceed(): ImageResult {
            executedRequests.add(currentRequest)
            val action = results.removeFirstOrNull()
                ?: error("No more mock results configured for proceed()")
            return action(currentRequest)
        }
    }

    @Test
    fun intercept_whenDecodeFailsOnDiskCache_clearsKeyAndRetriesNetwork() = runTest {
        val fakeDiskCache = FakeDiskCache()
        val interceptor = CorruptCacheRecoveryInterceptor(fakeDiskCache)

        val context = PlatformContext.INSTANCE
        val testUrl = "https://rfx.hpoi.net/gk/cover/sr/corrupt_test.png"
        val request = ImageRequest.Builder(context)
            .data(testUrl)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()

        val mockDecodeError = ErrorResult(
            image = null,
            request = request,
            throwable = RuntimeException("Failed to create image decoder with message 'unimplemented'Input contained an error.")
        )
        val mockRetryResult = ErrorResult(
            image = null,
            request = request,
            throwable = RuntimeException("Network retry success placeholder")
        )

        val chain = FakeChain(
            initialRequest = request,
            results = mutableListOf(
                { mockDecodeError },
                { req -> mockRetryResult.copy(request = req) }
            )
        )

        val finalResult = interceptor.intercept(chain)

        // 1. 坏缓存 key 必须被立即从磁盘缓存抹除
        assertEquals(listOf(testUrl), fakeDiskCache.removedKeys)
        // 2. 链必须执行了 2 次（初次 + 1 次自愈重试）
        assertEquals(2, chain.executedRequests.size)
        // 3. 重试请求必须跳过读磁盘缓存（强制重走网络）
        val retryRequest = chain.executedRequests[1]
        assertFalse(retryRequest.diskCachePolicy.readEnabled)
        assertTrue(retryRequest.diskCachePolicy.writeEnabled)
        // 4. 最终结果为重试步骤的返回结果
        assertEquals("Network retry success placeholder", (finalResult as? ErrorResult)?.throwable?.message)
    }

    @Test
    fun intercept_whenNoErrorOrNotCorrupt_doesNotTouchDiskCacheOrRetry() = runTest {
        val fakeDiskCache = FakeDiskCache()
        val interceptor = CorruptCacheRecoveryInterceptor(fakeDiskCache)

        val context = PlatformContext.INSTANCE
        val testUrl = "https://rfx.hpoi.net/gk/cover/sr/normal.png"
        val request = ImageRequest.Builder(context)
            .data(testUrl)
            .build()

        // 常规网络 404 等业务错误，不属于坏缓存解码异常，不触发自愈重试
        val normalError = ErrorResult(
            image = null,
            request = request,
            throwable = RuntimeException("HTTP 404 Not Found")
        )

        val chain = FakeChain(
            initialRequest = request,
            results = mutableListOf({ normalError })
        )

        val finalResult = interceptor.intercept(chain)

        assertTrue(fakeDiskCache.removedKeys.isEmpty())
        assertEquals(1, chain.executedRequests.size)
        assertEquals("HTTP 404 Not Found", (finalResult as? ErrorResult)?.throwable?.message)
    }
}
