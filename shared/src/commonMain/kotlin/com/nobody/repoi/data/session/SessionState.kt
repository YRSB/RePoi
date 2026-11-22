package com.nobody.repoi.data.session

import com.nobody.repoi.data.remote.model.UserProfile

sealed interface SessionState {

    data object Prewarming : SessionState

    data object Unauthenticated : SessionState

    data class Authenticated(
        val userId: Long,
        val token: String,
        val profile: UserProfile? = null,
    ) : SessionState {

        val isProfileLoaded: Boolean get() = profile != null
    }
}
