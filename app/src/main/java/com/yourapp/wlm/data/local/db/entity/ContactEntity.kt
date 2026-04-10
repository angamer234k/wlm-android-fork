package com.yourapp.wlm.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val email: String,
    val displayName: String,
    val personalMessage: String = "",
    val status: String = "FLN",
    val groupIds: String = "",
    val avatarUrl: String? = null,
    val avatarCacheKey: String? = null,
    val isBlocked: Boolean = false,
    val listFlags: Int = 0,
    val lastSeen: Long = 0L,
    val capabilities: Long = 0L
)
