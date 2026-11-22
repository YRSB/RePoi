package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentNotFoundException
import com.nobody.repoi.data.remote.model.PersonDetailResult
import com.nobody.repoi.data.remote.model.PersonHobbyEntry
import com.nobody.repoi.data.remote.model.PersonWorksEntry

class PersonDetailRepository(
    private val api: HpoiApi,
    private val collectRepository: CollectRepository
) {

    suspend fun getPersonDetail(personId: Long): Result<PersonDetailResult> = runCatching {
        api.getPersonDetail(personId = personId).result
    }

    suspend fun getPersonWorks(personId: Long): Result<List<PersonWorksEntry>> = runCatching {
        api.getPersonWorks(personId = personId).worksList
    }

    suspend fun getPersonCharacter(personId: Long): Result<List<PersonWorksEntry>> = runCatching {
        api.getPersonCharacter(personId = personId).characterList
    }

    suspend fun getPersonHobby(personId: Long): Result<List<PersonHobbyEntry>> = runCatching {
        api.getPersonHobby(personId = personId).hobby
    }

    suspend fun getComments(
        personNodeId: Long,
        type: String? = null,
        page: Int = 1,
        pageSize: Int = 10
    ): Result<List<CommentItem>> = runCatching {
        api.getComments(
            nodeId = personNodeId,
            type = type,
            page = page,
            pageSize = pageSize
        ).list.orEmpty()
    }.recoverCatching { error ->
        if (error is CommentNotFoundException) emptyList() else throw error
    }

    suspend fun getCollectState(personNodeId: Long): Result<Boolean> =
        collectRepository.refreshCollectState(personNodeId).map { it?.isCollected == true }
}
