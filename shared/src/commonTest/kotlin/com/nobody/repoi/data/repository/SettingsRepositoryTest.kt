package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.session.FakeUserSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsRepositoryTest {

    private fun setup(token: String = "utoken-test"): Pair<MockApi, SettingsRepository> {
        val mock = MockApi()
        return mock to SettingsRepository(mock.api(), FakeUserSession(token))
    }

    @Test
    fun addMask_successPostsItemNodeId() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("{}"))
        val result = repository.addMask(itemNodeId = 123L)
        assertTrue(result.isSuccess)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/mask/add", recorded.encodedPath)
        assertEquals("123", recorded.formEntries()["itemNodeId"])
    }

    @Test
    fun deleteMask_successPostsItemNodeId() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("{}"))
        val result = repository.deleteMask(itemNodeId = 456L)
        assertTrue(result.isSuccess)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/mask/del", recorded.encodedPath)
        assertEquals("456", recorded.formEntries()["itemNodeId"])
    }

    @Test
    fun getMaskList_successReturnsItems() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            mock.ok("""{"blockList":[{"id":7,"itemType":"hobby","name":"测试周边"}]}""")
        )
        val result = repository.getMaskList(itemType = "hobby")
        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(1, items.size)
        assertEquals(7L, items[0].id)
        assertEquals("hobby", items[0].itemType)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/mask/list", recorded.encodedPath)
        val form = recorded.formEntries()
        assertEquals("1", form["page"])
        assertEquals("20", form["pageSize"])
        assertEquals("hobby", form["itemType"])
    }

    @Test
    fun getMysqlSettings_successMapsUserState() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            mock.ok("""{"userState":{"setting_collect_hobby":1,"setting_collect_album":2,"setting_r18":0}}""")
        )
        val result = repository.getMysqlSettings()
        assertTrue(result.isSuccess)
        val state = result.getOrThrow()
        assertEquals(1, state["setting_collect_hobby"])
        assertEquals(2, state["setting_collect_album"])
        assertEquals(0, state["setting_r18"])

        val recorded = mock.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/setting/mysql/get", recorded.encodedPath)
    }

    @Test
    fun updateMysqlSetting_successPostsKeyAndValue() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("{}"))
        val result = repository.updateMysqlSetting(key = "setting_r18", value = 1)
        assertTrue(result.isSuccess)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/setting/mysql/upd", recorded.encodedPath)
        val form = recorded.formEntries()
        assertEquals("setting_r18", form["key"])
        assertEquals("1", form["val"])
    }

    @Test
    fun getRiakSettings_successMapsUserState() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            mock.ok("""{"userState":{"setting_own_action":1,"setting_stranger_message":2}}""")
        )
        val result = repository.getRiakSettings()
        assertTrue(result.isSuccess)
        val state = result.getOrThrow()
        assertEquals(1, state["setting_own_action"])
        assertEquals(2, state["setting_stranger_message"])

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/setting/riak/get", recorded.encodedPath)
        assertEquals("privacy", recorded.formEntries()["type"])
    }

    @Test
    fun updateRiakSetting_successPostsKeyAndValue() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("{}"))
        val result = repository.updateRiakSetting(key = "setting_stranger_message", value = 2)
        assertTrue(result.isSuccess)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/setting/riak/upd", recorded.encodedPath)
        val form = recorded.formEntries()
        assertEquals("setting_stranger_message", form["key"])
        assertEquals("2", form["val"])
    }

    @Test
    fun checkAccount_successReturnsBindingFlags() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            mock.ok("""{"result":{"verify":true,"password":true,"phone":true,"email":false,"wechat":false}}""")
        )
        val result = repository.checkAccount()
        assertTrue(result.isSuccess)
        val check = result.getOrThrow()
        assertTrue(check.password)
        assertTrue(check.phone)
        assertEquals(false, check.email)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/user/check/account", recorded.encodedPath)
    }

    @Test
    fun getUserPrivacy_successReturnsMaskedContacts() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("""{"phone":"138****8000","email":"abc****@hpoi.com"}"""))
        val result = repository.getUserPrivacy()
        assertTrue(result.isSuccess)
        val privacy = result.getOrThrow()
        assertEquals("138****8000", privacy.phone)
        assertEquals("abc****@hpoi.com", privacy.email)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/user/privacy", recorded.encodedPath)
        assertEquals("all", recorded.formEntries()["type"])
    }

    @Test
    fun apiError_throwsApiException() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(403, "无权访问"))
        val result = repository.addMask(itemNodeId = 1L)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is ApiException)
        assertEquals("无权访问", exception.message)
    }
}
