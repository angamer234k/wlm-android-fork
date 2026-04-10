package com.yourapp.wlm.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val senderEmail: String,
    val body: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val isOffline: Boolean = false,
    val messageType: String = "TEXT",
    val isDelivered: Boolean = true
)
