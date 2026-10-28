package com.nobody.poishelf.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.poishelf.data.remote.model.AlbumListItem
import com.nobody.poishelf.data.remote.model.CategoryMoneyGroup
import com.nobody.poishelf.data.remote.model.FellowCountData
import com.nobody.poishelf.data.remote.model.OrderStaticData
import com.nobody.poishelf.data.remote.model.UserProfile
import com.nobody.poishelf.data.repository.FriendChangeEvent
import com.nobody.poishelf.data.repository.FriendState
import com.nobody.poishelf.data.repository.UserRepository
import com.nobody.poishelf.util.PriceFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nobody.poishelf.data.remote.model.ActionItem

/**
 * 用户资料页面 UI 状态。
 */
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
    val selectedTab: Int = 1, // 0=相册, 1=动态, 2=二手（原版默认动态为主）
    val selectedAlbumSubTab: Int = 0, // 0=TA的相册, 1=收藏相册, 2=作品
    val albums: List<AlbumListItem> = emptyList(),
    val actions: List<ActionItem> = emptyList(),
    val isTabLoading: Boolean = false,
    val isIntroExpanded: Boolean = false,
    val showMoneyDialog: Boolean = false
)

/**
 * 个人资料展示与编辑 ViewModel。
 */
class UserInfoViewModel(
    private val userRepository: UserRepository,
    userId: Long = 0L,
) : ViewModel() {

    private val targetUserId: Long = userId

    private var initializedUserId: Long? = null

    private val _uiState = MutableStateFlow(
        UserInfoUiState(
            profile = if (targetUserId <= 0L) userRepository.currentUserProfile.value else null,
            isSelf = targetUserId <= 0L || (userRepository.currentUserId > 0L && targetUserId == userRepository.currentUserId)
        )
    )
    val uiState: StateFlow<UserInfoUiState> = _uiState.asStateFlow()

    init {
        if (targetUserId > 0L) {
            initUserId(targetUserId)
        }

        viewModelScope.launch {
            userRepository.currentUserProfile.collect { profile ->
                if (_uiState.value.isSelf && profile != null) {
                    _uiState.update { it.copy(profile = profile) }
                }
            }
        }

        viewModelScope.launch {
            userRepository.friendChangeEvent.collect { event ->
                val curProfile = _uiState.value.profile ?: return@collect
                if (_uiState.value.isSelf) {
                    // 查看本人主页时：关注/取关他人影响本人的关注数 friendCount
                    val updatedFellowCount = _uiState.value.fellowCount?.let { count ->
                        val curFriendCount = count.friendCount
                        val updatedFriendCount = if (event.isFollow) curFriendCount + 1 else (curFriendCount - 1).coerceAtLeast(0)
                        count.copy(friendCount = updatedFriendCount)
                    }
                    _uiState.update { it.copy(fellowCount = updatedFellowCount) }
                } else if (curProfile.id == event.userId) {
                    // 查看他人主页时：若操作的目标正是当前页面用户，同步更新关注态与粉丝数 fanCount
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

    /**
     * 初始化加载用户资料（具备幂等性，防止屏幕重组或并发 LaunchedEffect 重复触发，避免与默认 0L 产生竞态）。
     * @param userId 目标用户 UID，0L 表示查看当前登录用户自身。
     */
    fun initUserId(userId: Long) {
        if (initializedUserId == userId && (_uiState.value.profile != null || _uiState.value.isLoading)) {
            return
        }
        initializedUserId = userId
        val isSelf = userId <= 0L || (userRepository.currentUserId > 0L && userId == userRepository.currentUserId)
        _uiState.update {
            it.copy(
                isSelf = isSelf,
                profile = if (isSelf) userRepository.currentUserProfile.value else null
            )
        }
        loadProfile(userId)
    }

    /**
     * 加载指定用户的个人资料与附属统计数据。
     * @param userId 目标用户 UID，0L 表示查看当前登录用户自身。
     */
    fun loadProfile(userId: Long = initializedUserId ?: targetUserId) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val isSelf = userId <= 0L || (userRepository.currentUserId > 0L && userId == userRepository.currentUserId)
            val result = if (isSelf) {
                userRepository.getUserProfile()
            } else {
                userRepository.getUserInfo(userId)
            }
            result.onSuccess { profile ->
                val actualIsSelf = isSelf || (userRepository.currentUserId > 0L && profile.id == userRepository.currentUserId)
                val userNodeId = profile.id

                // 并发拉取社交统计、订单统计、相似度与关注状态、下游动态与相册（使用 try-catch 防御 mock 未存根或网络异常）
                val fellowDeferred = async {
                    try {
                        userRepository.getFellowCount(userNodeId).getOrNull()
                    } catch (_: Throwable) {
                        null
                    }
                }
                val orderDeferred = async {
                    try {
                        userRepository.getOrderStaticData(userNodeId).getOrNull()
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
                        userRepository.getFellowType(userNodeId).getOrNull() ?: FriendState.UNKNOWN
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

                // 历史动态头像覆盖/回退机制：Hpoi 历史动态快照中若固化了已在服务端物理删除的旧头像（HTTP 404），
                // 统一以当前用户的最新有效头像覆盖，防止出现空白头像。
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
                    runCatching { userRepository.parseCategoryMoneyGroups(moneyMap) }.getOrDefault(emptyList())
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

    /**
     * 关注/取消关注目标用户（仅非本人主页可用，具备乐观响应与失败回滚）。
     */
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
            val result = userRepository.updateFriend(profile.id, isFollow = newFollow)
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

    /**
     * 更新个性签名与个人简介 (POST api/user/base/upd)。
     */
    fun updateBio(sign: String, intro: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, saveSuccessMessage = null) }

            val signResult = userRepository.updateUserBase("sign", sign, autoRefresh = false)
            if (signResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = signResult.exceptionOrNull()?.message ?: "更新签名失败"
                    )
                }
                return@launch
            }

            val introResult = userRepository.updateUserBase("intro", intro, autoRefresh = false)
            if (introResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = introResult.exceptionOrNull()?.message ?: "更新简介失败"
                    )
                }
                return@launch
            }

            val latest = userRepository.getUserProfile().getOrNull()
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

    /**
     * 更新基础个人资料（昵称、性别、生日、城市）。
     */
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
                val res = userRepository.updateUserBase(key, value, autoRefresh = false)
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

            val latest = userRepository.getUserProfile().getOrNull()
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

    /**
     * 批量更新个人资料（表单综合提交）。
     */
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
                val res = userRepository.updateUserBase(key, value, autoRefresh = false)
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

            val latest = userRepository.getUserProfile().getOrNull()
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
