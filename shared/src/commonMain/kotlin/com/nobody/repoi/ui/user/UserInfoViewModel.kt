package com.nobody.repoi.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.CategoryMoneyGroup
import com.nobody.repoi.data.remote.model.FellowCountData
import com.nobody.repoi.data.remote.model.OrderStaticData
import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.data.repository.FriendChangeEvent
import com.nobody.repoi.data.repository.FollowRepository
import com.nobody.repoi.data.repository.FriendState
import com.nobody.repoi.data.repository.UserFinanceRepository
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.data.session.SessionState
import com.nobody.repoi.data.session.UserSession
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.nobody.repoi.util.PriceFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nobody.repoi.data.remote.model.ActionItem

data class UserInfoUiState(
    val profile: UserProfile? = null,
    val isSelf: Boolean = true,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccessMessage: String? = null,
    val errorMessage: String? = null,
    val isEditing: Boolean = false,
    val fellowCount: FellowCountData? = null,
    val orderStatic: OrderStaticData? = null,
    val totalMoneyText: String = "¥0",
    val historyMoneyGroups: List<CategoryMoneyGroup> = emptyList(),
    val similarity: Int = 0,
    val isFollowed: Boolean = false,
    val isMutual: Boolean = false,
    val selectedTab: Int = 1,
    val selectedAlbumSubTab: Int = 0,
    val albums: List<AlbumListItem> = emptyList(),
    val actions: List<ActionItem> = emptyList(),
    val isTabLoading: Boolean = false,
    val isIntroExpanded: Boolean = false,
    val showMoneyDialog: Boolean = false
)

class UserInfoViewModel(
    private val userRepository: UserRepository,
    private val userSession: UserSession,
    private val followRepository: FollowRepository,
    private val userFinanceRepository: UserFinanceRepository,
    userId: Long = 0L,
) : ViewModel() {

    private val targetUserId: Long = userId

    private var initializedUserId: Long? = null

    private val _uiState = MutableStateFlow(
        UserInfoUiState(
            profile = if (targetUserId <= 0L) (userSession.state.value as? SessionState.Authenticated)?.profile else null,
            isSelf = targetUserId <= 0L || (userSession.currentUserId > 0L && targetUserId == userSession.currentUserId)
        )
    )
    val uiState: StateFlow<UserInfoUiState> = _uiState.asStateFlow()

    init {
        if (targetUserId > 0L) {
            initUserId(targetUserId)
        }

        viewModelScope.launch {
            userSession.state
                .map { (it as? SessionState.Authenticated)?.profile }
                .distinctUntilChanged()
                .collect { profile ->
                    if (_uiState.value.isSelf && profile != null) {
                        _uiState.update { it.copy(profile = profile) }
                    }
                }
        }

        viewModelScope.launch {
            followRepository.friendChangeEvent.collect { event ->
                val curProfile = _uiState.value.profile ?: return@collect
                if (_uiState.value.isSelf) {

                    val updatedFellowCount = _uiState.value.fellowCount?.let { count ->
                        val curFriendCount = count.friendCount
                        val updatedFriendCount = if (event.isFollow) curFriendCount + 1 else (curFriendCount - 1).coerceAtLeast(0)
                        count.copy(friendCount = updatedFriendCount)
                    }
                    _uiState.update { it.copy(fellowCount = updatedFellowCount) }
                } else if (curProfile.id == event.userId) {

                    if (_uiState.value.isFollowed != event.isFollow) {
                        val newMutual = if (!event.isFollow) false else _uiState.value.isMutual
                        val updatedFellowCount = _uiState.value.fellowCount?.let { count ->
                            val curFanCount = count.fanCount
                            val updatedFanCount = if (event.isFollow) curFanCount + 1 else (curFanCount - 1).coerceAtLeast(0)
                            count.copy(fanCount = updatedFanCount)
                        }
                        _uiState.update {
                            it.copy(
                                isFollowed = event.isFollow,
                                isMutual = newMutual,
                                fellowCount = updatedFellowCount
                            )
                        }
                    }
                }
            }
        }
    }

    fun initUserId(userId: Long) {
        if (initializedUserId == userId && (_uiState.value.profile != null || _uiState.value.isLoading)) {
            return
        }
        initializedUserId = userId
        val isSelf = userId <= 0L || (userSession.currentUserId > 0L && userId == userSession.currentUserId)
        _uiState.update {
            it.copy(
                isSelf = isSelf,
                profile = if (isSelf) (userSession.state.value as? SessionState.Authenticated)?.profile else null
            )
        }
        loadProfile(userId)
    }

    fun loadProfile(userId: Long = initializedUserId ?: targetUserId) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val isSelf = userId <= 0L || (userSession.currentUserId > 0L && userId == userSession.currentUserId)
            val result = if (isSelf) {
                userSession.refreshProfile()
            } else {
                userRepository.getUserInfo(userId)
            }
            result.onSuccess { profile ->
                val actualIsSelf = isSelf || (userSession.currentUserId > 0L && profile.id == userSession.currentUserId)
                val userNodeId = profile.id

                val fellowDeferred = async {
                    try {
                        followRepository.getFellowCount(userNodeId).getOrNull()
                    } catch (_: Throwable) {
                        null
                    }
                }
                val orderDeferred = async {
                    try {
                        userFinanceRepository.getOrderStaticData(userNodeId).getOrNull()
                    } catch (_: Throwable) {
                        null
                    }
                }
                val similarityDeferred = if (!actualIsSelf) async {
                    try {
                        val res = userRepository.getCollectSimilarity(userNodeId)
                        (res.getOrNull() as? Number)?.toInt() ?: 0
                    } catch (_: Throwable) {
                        0
                    }
                } else null
                val fellowTypeDeferred = if (!actualIsSelf) async {
                    try {
                        followRepository.getFellowType(userNodeId).getOrNull() ?: FriendState.UNKNOWN
                    } catch (_: Throwable) {
                        FriendState.UNKNOWN
                    }
                } else null
                val albumDeferred = async {
                    try {
                        userRepository.getUserAlbumList(userNodeId, page = 1, pageSize = 20).getOrNull() ?: emptyList()
                    } catch (_: Throwable) {
                        emptyList()
                    }
                }
                val actionDeferred = async {
                    try {
                        userRepository.getUserActionList(userNodeId, pageSize = 20).getOrNull() ?: emptyList()
                    } catch (_: Throwable) {
                        emptyList()
                    }
                }

                val fellowData = fellowDeferred.await()
                val orderData = orderDeferred.await()
                val simVal = similarityDeferred?.await() ?: 0
                val fType = fellowTypeDeferred?.await() ?: FriendState.UNKNOWN
                val albumList = albumDeferred.await()
                val actionList = actionDeferred.await()

                val currentLatestAvatar = profile.header
                val normalizedActions = if (!currentLatestAvatar.isNullOrBlank()) {
                    actionList.map { action ->
                        val itemUser = action.user
                        if (itemUser != null) {
                            action.copy(user = itemUser.copy(header = currentLatestAvatar))
                        } else {
                            action
                        }
                    }
                } else {
                    actionList
                }

                val historyGroups = orderData?.allOrderMoney?.let { moneyMap ->
                    runCatching { userFinanceRepository.parseCategoryMoneyGroups(moneyMap) }.getOrDefault(emptyList())
                } ?: emptyList()
                val totalRmb = historyGroups.sumOf { it.totalRmb }
                val totalMoneyText = if (totalRmb >= 10000.0) {
                    "¥" + PriceFormatter.formatFixed(totalRmb / 10000.0, 1).removeSuffix(".0") + "万"
                } else if (totalRmb > 0.0) {
                    "¥${totalRmb.toInt()}"
                } else {
                    "¥0"
                }

                val initialFollowed = profile.isFollowed || fType.isFollowed
                val initialMutual = profile.isMutual || fType == FriendState.MUTUAL

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        profile = profile,
                        isSelf = actualIsSelf,
                        fellowCount = fellowData,
                        orderStatic = orderData,
                        totalMoneyText = totalMoneyText,
                        historyMoneyGroups = historyGroups,
                        similarity = if (simVal > 0) simVal else profile.similarity,
                        isFollowed = initialFollowed,
                        isMutual = initialMutual,
                        albums = albumList,
                        actions = normalizedActions,
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "获取个人资料失败"
                    )
                }
            }
        }
    }

    fun toggleFollow() {
        val profile = _uiState.value.profile ?: return
        if (_uiState.value.isSelf) return

        val prevFollow = _uiState.value.isFollowed
        val prevMutual = _uiState.value.isMutual
        val prevFellowCount = _uiState.value.fellowCount
        val newFollow = !prevFollow
        val newMutual = if (!newFollow) false else prevMutual

        val newFellowCount = prevFellowCount?.let { count ->
            val curFanCount = count.fanCount
            val updatedFanCount = if (newFollow) curFanCount + 1 else (curFanCount - 1).coerceAtLeast(0)
            count.copy(fanCount = updatedFanCount)
        }

        _uiState.update {
            it.copy(
                isFollowed = newFollow,
                isMutual = newMutual,
                fellowCount = newFellowCount
            )
        }

        viewModelScope.launch {
            val result = followRepository.updateFriend(profile.id, isFollow = newFollow)
            result.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isFollowed = prevFollow,
                        isMutual = prevMutual,
                        fellowCount = prevFellowCount,
                        errorMessage = error.message ?: "操作失败，请重试"
                    )
                }
            }
        }
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun selectAlbumSubTab(subTab: Int) {
        _uiState.update { it.copy(selectedAlbumSubTab = subTab) }
    }

    fun toggleIntroExpanded() {
        _uiState.update { it.copy(isIntroExpanded = !it.isIntroExpanded) }
    }

    fun setShowMoneyDialog(show: Boolean) {
        _uiState.update { it.copy(showMoneyDialog = show) }
    }

    fun updateBio(sign: String, intro: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, saveSuccessMessage = null) }

            val signResult = userRepository.updateUserBase("sign", sign)
            if (signResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = signResult.exceptionOrNull()?.message ?: "更新签名失败"
                    )
                }
                return@launch
            }

            val introResult = userRepository.updateUserBase("intro", intro)
            if (introResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = introResult.exceptionOrNull()?.message ?: "更新简介失败"
                    )
                }
                return@launch
            }

            val latest = userSession.refreshProfile().getOrNull()
                ?: _uiState.value.profile?.copy(sign = sign, intro = intro)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    isEditing = false,
                    profile = latest,
                    saveSuccessMessage = "个人简介已保存",
                    errorMessage = null
                )
            }
        }
    }

    fun updateBasicInfo(nickname: String, sex: Int, birthday: String, city: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, saveSuccessMessage = null) }

            val fields = listOf(
                "nickname" to nickname,
                "sex" to sex.toString(),
                "birth" to birthday,
                "city" to city
            )

            for ((key, value) in fields) {
                val res = userRepository.updateUserBase(key, value)
                if (res.isFailure) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = res.exceptionOrNull()?.message ?: "更新资料失败"
                        )
                    }
                    return@launch
                }
            }

            val latest = userSession.refreshProfile().getOrNull()
                ?: _uiState.value.profile?.copy(
                    nickname = nickname,
                    sex = sex,
                    birth = birthday,
                    city = city
                )
            _uiState.update {
                it.copy(
                    isSaving = false,
                    isEditing = false,
                    profile = latest,
                    saveSuccessMessage = "基础资料已保存",
                    errorMessage = null
                )
            }
        }
    }

    fun updateAll(
        nickname: String,
        sign: String,
        intro: String,
        sex: Int = 2,
        birthday: String = "",
        city: String = ""
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, saveSuccessMessage = null) }

            val currentProfile = _uiState.value.profile
            val fields = mutableListOf<Pair<String, String>>()
            if (nickname.isNotBlank() && (currentProfile == null || nickname != currentProfile.nickname)) {
                fields.add("nickname" to nickname)
            }
            if (currentProfile == null || sign != currentProfile.sign.orEmpty()) {
                fields.add("sign" to sign)
            }
            if (currentProfile == null || intro != currentProfile.intro.orEmpty()) {
                fields.add("intro" to intro)
            }
            if (currentProfile == null || sex != (currentProfile.sex ?: 2)) {
                fields.add("sex" to sex.toString())
            }
            if (birthday.isNotBlank() && (currentProfile == null || birthday != currentProfile.birth.orEmpty())) {
                fields.add("birth" to birthday)
            }
            if (city.isNotBlank() && (currentProfile == null || city != currentProfile.city.orEmpty())) {
                fields.add("city" to city)
            }

            if (fields.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isEditing = false,
                        saveSuccessMessage = "资料修改成功",
                        errorMessage = null
                    )
                }
                return@launch
            }

            for ((key, value) in fields) {
                val res = userRepository.updateUserBase(key, value)
                if (res.isFailure) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = res.exceptionOrNull()?.message ?: "更新资料失败"
                        )
                    }
                    return@launch
                }
            }

            val latest = userSession.refreshProfile().getOrNull()
                ?: _uiState.value.profile?.copy(
                    nickname = nickname.ifBlank { _uiState.value.profile?.nickname },
                    sign = sign,
                    intro = intro,
                    sex = sex,
                    birth = birthday.ifBlank { _uiState.value.profile?.birth },
                    city = city.ifBlank { _uiState.value.profile?.city }
                )
            _uiState.update {
                it.copy(
                    isSaving = false,
                    isEditing = false,
                    profile = latest,
                    saveSuccessMessage = "资料修改成功",
                    errorMessage = null
                )
            }
        }
    }

    fun startEditing() {
        _uiState.update { it.copy(isEditing = true) }
    }

    fun dismissEditing() {
        _uiState.update { it.copy(isEditing = false) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(saveSuccessMessage = null) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
