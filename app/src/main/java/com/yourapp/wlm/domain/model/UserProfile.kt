package com.yourapp.wlm.domain.model

data class UserProfile(
    val email: String,
    val displayName: String,
    val personalMessage: String = "",
    val status: PresenceStatus = PresenceStatus.ONLINE,
    val avatarUrl: String? = null
)
