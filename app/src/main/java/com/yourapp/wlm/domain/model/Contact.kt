package com.yourapp.wlm.domain.model

data class Contact(
    val email: String,
    val displayName: String,
    val personalMessage: String = "",
    val status: PresenceStatus = PresenceStatus.OFFLINE,
    val groupIds: List<String> = emptyList(),
    val avatarUrl: String? = null,
    val isBlocked: Boolean = false,
    val listFlags: Int = 0,
    val lastSeen: Long = 0L,
    val capabilities: Long = 0L,
    val unreadCount: Int = 0
)
