package com.nobody.poishelf.data.local

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Token 管理器测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `TokenManagerTest`（逐用例；JUnit4 → kotlin.test，
 * `runBlocking` → `runTest`，Robolectric `ApplicationProvider` →
 * [TestDataStore] 真实 DataStore 隔离目录）。
 */
class TokenManagerTest {

    private fun manager(): TokenManager = TokenManager(TestDataStore.userPrefs())

    @Test
    fun saveToken_updatesMemoryAndFlowAndResetsUserId() = runTest {
        val manager = manager()
        manager.tokenFlow.first()
        manager.clearToken()
        manager.saveCurrentUser(userId = 9L, userName = "旧用户")
        manager.saveToken("token-1", userName = "新用户")

        assertEquals("token-1", manager.currentToken)
        assertEquals(0L, manager.currentUserId)
        assertEquals("新用户", manager.currentUserName)
        assertEquals("token-1", manager.tokenFlow.first())
    }

    @Test
    fun saveToken_withoutUserName_keepsExistingName() = runTest {
        val manager = manager()
        manager.tokenFlow.first()
        manager.clearToken()
        manager.saveToken("token-1", userName = "胶友")
        manager.saveToken("token-2")

        assertEquals("token-2", manager.currentToken)
        assertEquals("胶友", manager.currentUserName)
        assertEquals("token-2", manager.tokenFlow.first())
    }

    @Test
    fun saveCurrentUser_updatesIdAndName() = runTest {
        val manager = manager()
        manager.tokenFlow.first()
        manager.clearToken()
        manager.saveCurrentUser(userId = 42L, userName = "收藏家")

        assertEquals(42L, manager.currentUserId)
        assertEquals("收藏家", manager.currentUserName)
    }

    @Test
    fun clearToken_resetsMemoryAndPersistedFlow() = runTest {
        val manager = manager()
        manager.tokenFlow.first()
        manager.clearToken()
        manager.saveToken("token-1", userName = "胶友")
        manager.saveCurrentUser(userId = 7L)

        manager.clearToken()

        assertEquals("", manager.currentToken)
        assertEquals(0L, manager.currentUserId)
        assertEquals("", manager.currentUserName)
        assertTrue(manager.tokenFlow.first().isEmpty())
    }
}
