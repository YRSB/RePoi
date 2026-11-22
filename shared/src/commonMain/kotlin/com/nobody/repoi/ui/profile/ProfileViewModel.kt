package com.nobody.repoi.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.local.ThemeManager
import com.nobody.repoi.data.local.ThemeMode
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.CheckInStatusPayload
import com.nobody.repoi.data.remote.model.CollectTopItem
import com.nobody.repoi.data.remote.model.FellowCountData
import com.nobody.repoi.data.remote.model.OrderCount
import com.nobody.repoi.data.remote.model.OrderFinanceSummary
import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.data.repository.FriendChangeEvent
import com.nobody.repoi.data.repository.MessageRepository
import com.nobody.repoi.data.repository.FollowRepository
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.data.session.SessionState
import com.nobody.repoi.data.session.UserSession
import com.nobody.repoi.platform.nowMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class ProfileUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val profile: UserProfile? = null,
    val fellow: FellowCountData? = null,
    val order: OrderCount? = null,
    val finance: OrderFinanceSummary? = null,
    val preorderItems: List<CollectTopItem> = emptyList(),
    val buyItems: List<CollectTopItem> = emptyList(),
    val careItems: List<CollectTopItem> = emptyList(),
    val userAlbums: List<AlbumListItem> = emptyList(),
    val checkInStatus: CheckInStatusPayload? = null,
    val userMp: Int = 0,
    val isCheckingIn: Boolean = false,
    val hasUnreadNotice: Boolean = false
) {
    val hasNoPreorderItems: Boolean get() = preorderItems.isEmpty()
    val hasNoBuyItems: Boolean get() = buyItems.isEmpty()
    val hasNoCareItems: Boolean get() = careItems.isEmpty()
    val hasNoUserAlbums: Boolean get() = userAlbums.isEmpty()

    val isSignedInToday: Boolean
        @OptIn(ExperimentalTime::class)
        get() {
            val lastTime = checkInStatus?.checkInLastTime ?: return false
            if (lastTime <= 0L) return false

            val timeMillis = if (lastTime < 100_000_000_000L) lastTime * 1000L else lastTime
            val tz = TimeZone.currentSystemDefault()
            val last = Instant.fromEpochMilliseconds(timeMillis).toLocalDateTime(tz).date
            val today = Clock.System.now().toLocalDateTime(tz).date
            return last == today
        }

    val checkInDays: Int get() = checkInStatus?.checkInContinuityDays ?: 0
}

class ProfileViewModel(
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val themeManager: ThemeManager,
    private val userSession: UserSession,
    private val followRepository: FollowRepository
) : ViewModel() {

    private var loadedToken: String? = null

    private val _uiState = MutableStateFlow(
        ProfileUiState(
            isLoggedIn = userSession.isAuthenticated,
            profile = (userSession.state.value as? SessionState.Authenticated)?.profile
        )
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userSession.state.collect { state ->
                when (state) {
                    is SessionState.Authenticated -> {
                        _uiState.update {
                            it.copy(isLoggedIn = true, profile = state.profile ?: it.profile)
                        }
                        if (loadedToken != state.token) {
                            loadedToken = state.token
                            loadProfile(isRefresh = false)
                        }
                    }
                    SessionState.Unauthenticated -> {
                        loadedToken = null
                        _uiState.value = ProfileUiState(isLoggedIn = false)
                    }
                    SessionState.Prewarming -> Unit
                }
            }
        }

        viewModelScope.launch {
            followRepository.friendChangeEvent.collect { event ->
                _uiState.update { state ->
                    val curFellow = state.fellow ?: return@update state
                    val curFriendCount = curFellow.friendCount
                    val updatedFriendCount = if (event.isFollow) {
                        curFriendCount + 1
                    } else {
                        (curFriendCount - 1).coerceAtLeast(0)
                    }
                    state.copy(fellow = curFellow.copy(friendCount = updatedFriendCount))
                }

                val currentUid = userSession.currentUserId
                if (currentUid > 0L) {
                    followRepository.getFellowCount(currentUid).onSuccess { freshFellow ->
                        _uiState.update { it.copy(fellow = freshFellow) }
                    }
                }
            }
        }
    }

    fun refresh() {
        loadProfile(isRefresh = true)
    }

    fun loadProfile(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!userSession.isAuthenticated) return@launch

            _uiState.update {
                it.copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    errorMessage = null
                )
            }

            val profileResult = if (isRefresh) {
                userSession.refreshProfile()
            } else {
                (userSession.state.value as? SessionState.Authenticated)?.profile
                    ?.let { Result.success(it) }
                    ?: userSession.refreshProfile()
            }
            val profile = profileResult.getOrElse { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = e.message ?: "获取用户信息失败"
                    )
                }
                return@launch
            }

            launch {
                messageRepository.getUnreadPushCount()
                    .onSuccess { pushCategory ->
                        val hasUnread = pushCategory.totalCount > 0
                        _uiState.update { it.copy(hasUnreadNotice = hasUnread) }
                    }
            }

            val result = userRepository.loadFullProfile(profile)
            result.onSuccess { data ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        profile = data.profile,
                        fellow = data.fellow,
                        order = data.order,
                        finance = data.finance,
                        preorderItems = data.preorderItems,
                        buyItems = data.buyItems,
                        careItems = data.careItems,
                        userAlbums = data.userAlbums,
                        checkInStatus = data.checkInStatus,
                        userMp = data.userMp,
                        errorMessage = null
                    )
                }
            }.onFailure { e ->

                if (!userSession.isAuthenticated) return@onFailure
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = e.message ?: "加载数据失败"
                    )
                }
            }
        }
    }

    fun toggleTheme(isDarkCurrently: Boolean) {
        viewModelScope.launch {
            val nextMode = if (isDarkCurrently) ThemeMode.LIGHT else ThemeMode.DARK
            themeManager.setThemeMode(nextMode)
        }
    }

    fun checkIn() {
        if (!userSession.isAuthenticated || _uiState.value.isCheckingIn || _uiState.value.isSignedInToday) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingIn = true) }
            val result = userRepository.checkIn()
            result.onSuccess {
                _uiState.update { state ->
                    val nextDays = state.checkInDays + 1
                    state.copy(
                        isCheckingIn = false,
                        checkInStatus = CheckInStatusPayload(
                            checkInLastTime = nowMillis(),
                            checkInContinuityDays = nextDays
                        ),
                        infoMessage = "签到成功！已连续签到 ${nextDays} 天"
                    )
                }

                val updatedMp = userRepository.getUserMp().getOrNull()
                if (updatedMp != null) {
                    _uiState.update { it.copy(userMp = updatedMp) }
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isCheckingIn = false,
                        infoMessage = e.message ?: "已完成今日签到"
                    )
                }
            }
        }
    }

    fun clearInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun logout() {
        viewModelScope.launch {
            userSession.logout()
            loadedToken = null
            _uiState.value = ProfileUiState(isLoggedIn = false)
        }
    }
}
