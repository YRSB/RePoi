package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.BlockItem
import com.nobody.repoi.data.remote.model.ChatMessagePayload
import com.nobody.repoi.data.remote.model.MessageContactItem
import com.nobody.repoi.data.remote.model.PushCountCategory
import com.nobody.repoi.data.remote.model.PushMessageItem

class MessageRepository(
    private val api: HpoiApi,
) {

    suspend fun getUnreadPushCount(): Result<PushCountCategory> = runCatching {
        api.getUnreadPushCount()?.map?.push ?: PushCountCategory()
    }

    suspend fun getMessageContacts(page: Int = 1): Result<List<MessageContactItem>> = runCatching {
        api.getMessageContacts(page = page)?.contact ?: emptyList()
    }

    suspend fun updateContactTop(id: Long, state: Int): Result<Unit> = runCatching {
        api.updateContactTop(id = id, state = state)
    }

    suspend fun deleteContact(id: Long, userNodeId: Long): Result<Unit> = runCatching {
        api.deleteContact(id = id, userNodeId = userNodeId)
    }

    suspend fun getPushMessages(
        type: String,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<PushMessageItem>> = runCatching {
        api.getPushMessages(type = type, page = page, pageSize = pageSize)?.pushMessage ?: emptyList()
    }

    suspend fun markPushRead(type: Int, messageId: Long? = null, page: Int? = null, pageSize: Int? = null): Result<Unit> = runCatching {
        api.markPushRead(type = type, messageId = messageId, page = page, pageSize = pageSize)
    }

    suspend fun deletePushMessage(messageId: Long, pushType: Int): Result<Unit> = runCatching {
        api.deletePushMessage(messageId = messageId, pushType = pushType)
    }

    suspend fun batchDeletePushMessages(
        pushType: Int,
        isAll: Boolean,
        ids: List<Long>? = null,
        readCount: Int? = null
    ): Result<Unit> = runCatching {
        val idsString = if (!isAll && ids != null) ids.toString() else null
        api.batchDeletePushMessages(
            pushType = pushType,
            isAll = isAll,
            ids = idsString,
            read = readCount
        )
    }

    suspend fun getChatMessages(
        fromId: Long,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<ChatMessagePayload> = runCatching {
        api.getChatMessages(fromId = fromId, page = page, pageSize = pageSize) ?: ChatMessagePayload()
    }

    suspend fun sendTextMessage(
        toId: Long,
        content: String,
        emoticon: String? = null
    ): Result<Unit> = runCatching {
        api.sendTextMessage(toId = toId, content = content, emoticon = emoticon)
    }

    suspend fun sendResaleMessage(
        toId: Long,
        resaleId: Long,
        content: String? = null
    ): Result<Unit> = runCatching {
        api.sendResaleMessage(toId = toId, resaleId = resaleId, content = content)
    }

    suspend fun uploadChatMessagePicture(
        toId: Long,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String = "image/jpeg",
    ): Result<Unit> = runCatching {
        api.uploadChatMessagePicture(
            toId = toId,
            fileName = fileName,
            fileBytes = fileBytes,
            mimeType = mimeType,
        )
    }

    suspend fun withdrawMessage(messageId: Long): Result<Unit> = runCatching {
        api.withdrawMessage(messageId = messageId)
    }

    suspend fun getLatestMsgId(userId: Long): Result<Long> = runCatching {
        api.getLatestMsgId(userId = userId)?.latestMsgId ?: 0L
    }

    suspend fun getBlockList(page: Int = 1, pageSize: Int = 50): Result<List<BlockItem>> = runCatching {
        api.getBlockList(page = page, pageSize = pageSize)?.userBlock ?: emptyList()
    }

    suspend fun updateUserBlock(userId: Long, blockState: Int): Result<Unit> = runCatching {
        api.updateUserBlock(userId = userId, blockState = blockState)
    }
}
