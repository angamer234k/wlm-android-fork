package com.yourapp.wlm.domain.model

data class Message(
    val id: Long = 0,
    val conversationId: String,
    val senderEmail: String,
    val senderDisplayName: String = "",
    val body: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val isOffline: Boolean = false,
    val messageType: MessageType = MessageType.TEXT,
    val isDelivered: Boolean = true
)

enum class MessageType {
    TEXT, NUDGE, FILE
}
