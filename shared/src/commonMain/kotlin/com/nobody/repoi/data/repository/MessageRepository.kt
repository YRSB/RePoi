package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.BlockItem
import com.nobody.repoi.data.remote.model.ChatMessagePayload
import com.nobody.repoi.data.remote.model.MessageContactItem
import com.nobody.repoi.data.remote.model.PushCountCategory
import com.nobody.repoi.data.remote.model.PushMessageItem

/**
 * 消息与通知模块仓储实现。
 *
 * 封装分类通知、未读数统计、私信联系人会话、1v1 聊天记录与长轮询、黑名单管理。
 * 鉴权与 4001 令牌失效自愈均由底层网络管道统一处理。
 */
class MessageRepository(
    private val api: HpoiApi,
) {

    /**
     * 获取未读通知消息分类统计计数 (api/unread/push-message/count-v2)。
     */
    suspend fun getUnreadPushCount(): Result<PushCountCategory> = runCatching {
        api.getUnreadPushCount()?.map?.push ?: PushCountCategory()
    }

    /**
     * 获取私信联系人会话列表 (api/message/contact/get)。
     */
    suspend fun getMessageContacts(page: Int = 1): Result<List<MessageContactItem>> = runCatching {
        api.getMessageContacts(page = page)?.contact ?: emptyList()
    }

    /**
     * 更新私信会话置顶状态 (api/message/contact/top/upd)。
     *
     * @param id 会话 ID
     * @param state 1: 置顶, 0: 取消置顶
     */
    suspend fun updateContactTop(id: Long, state: Int): Result<Unit> = runCatching {
        api.updateContactTop(id = id, state = state)
    }

    /**
     * 删除私信会话 (api/message/contact/del)。
     */
    suspend fun deleteContact(id: Long, userNodeId: Long): Result<Unit> = runCatching {
        api.deleteContact(id = id, userNodeId = userNodeId)
    }

    /**
     * 获取指定分类的通知消息列表 (api/push/{type})。
     *
     * @param type 分类标识："system", "reply", "praise", "recommend", "follow", "commodity"
     */
    suspend fun getPushMessages(
        type: String,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<PushMessageItem>> = runCatching {
        api.getPushMessages(type = type, page = page, pageSize = pageSize)?.pushMessage ?: emptyList()
    }

    /**
     * 标记通知消息已读 (api/push/read)。
     *
     * 防坑契约：type 语义分三种形态——单条已读传「消息条目自带 type」（官方 app 各通知
     * Adapter 的做法，勿用消息中心入口 typeNumber 替代）；缺 messageId 表示整类清零；
     * 传 page/pageSize 表示仅清当前页（官方仅对系统通知 type=1 在浏览时使用）。
     *
     * @param type 消息分类类型数值 (0:全部, 1:系统, 2:回复, 3:赞, 4:推荐, 5:关注, 6:商品)
     * @param messageId 单项消息 ID；为 null 时表示将该分类下所有消息标记为已读
     * @param page 仅清当前页时的页码
     * @param pageSize 仅清当前页时的单页容量
     */
    suspend fun markPushRead(type: Int, messageId: Long? = null, page: Int? = null, pageSize: Int? = null): Result<Unit> = runCatching {
        api.markPushRead(type = type, messageId = messageId, page = page, pageSize = pageSize)
    }

    /**
     * 删除单条通知消息 (api/push/del)。
     */
    suspend fun deletePushMessage(messageId: Long, pushType: Int): Result<Unit> = runCatching {
        api.deletePushMessage(messageId = messageId, pushType = pushType)
    }

    /**
     * 批量删除通知消息 (api/push/batch/del)。
     */
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

    /**
     * 获取与指定用户的 1v1 聊天记录 (api/message/get-v2)。
     */
    suspend fun getChatMessages(
        fromId: Long,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<ChatMessagePayload> = runCatching {
        api.getChatMessages(fromId = fromId, page = page, pageSize = pageSize) ?: ChatMessagePayload()
    }

    /**
     * 发送文本/表情私信消息 (api/message/add-v2)。
     */
    suspend fun sendTextMessage(
        toId: Long,
        content: String,
        emoticon: String? = null
    ): Result<Unit> = runCatching {
        api.sendTextMessage(toId = toId, content = content, emoticon = emoticon)
    }

    /**
     * 发送二手/条目卡片私信 (api/message/add/resale-v2)。
     */
    suspend fun sendResaleMessage(
        toId: Long,
        resaleId: Long,
        content: String? = null
    ): Result<Unit> = runCatching {
        api.sendResaleMessage(toId = toId, resaleId = resaleId, content = content)
    }

    /**
     * 上传图片并发送私信图片消息 (api/message/upload-v2)。
     *
     * Ktor 版以字节 + 文件名入参（FileKit 跨平台）；原 OkHttp `File` 版语义保持
     * （`image/jpeg`，`file` part + `toId` 文本 part）。
     */
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

    /**
     * 撤回私信消息 (api/message/del)。
     */
    suspend fun withdrawMessage(messageId: Long): Result<Unit> = runCatching {
        api.withdrawMessage(messageId = messageId)
    }

    /**
     * 获取最新消息 ID，用于长轮询判断是否有增量私信 (api/message/latestMsgId/get)。
     */
    suspend fun getLatestMsgId(userId: Long): Result<Long> = runCatching {
        api.getLatestMsgId(userId = userId)?.latestMsgId ?: 0L
    }

    /**
     * 获取黑名单列表 (api/block/list)。
     */
    suspend fun getBlockList(page: Int = 1, pageSize: Int = 50): Result<List<BlockItem>> = runCatching {
        api.getBlockList(page = page, pageSize = pageSize)?.userBlock ?: emptyList()
    }

    /**
     * 更新用户黑名单屏蔽状态 (api/block/upd)。
     *
     * @param blockState 1: 屏蔽, 0: 解除屏蔽
     */
    suspend fun updateUserBlock(userId: Long, blockState: Int): Result<Unit> = runCatching {
        api.updateUserBlock(userId = userId, blockState = blockState)
    }
}

