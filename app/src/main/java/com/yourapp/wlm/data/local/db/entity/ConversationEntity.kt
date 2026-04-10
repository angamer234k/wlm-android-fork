package com.yourapp.wlm.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val contactEmail: String,
    val lastMessageBody: String = "",
    val lastMessageTimestamp: Long = 0L,
    val unreadCount: Int = 0
)
