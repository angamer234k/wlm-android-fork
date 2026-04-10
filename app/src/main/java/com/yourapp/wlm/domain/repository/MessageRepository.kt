package com.yourapp.wlm.domain.repository

import com.yourapp.wlm.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getMessagesForConversation(contactEmail: String): Flow<List<Message>>
    suspend fun sendMessage(contactEmail: String, body: String): Result<Unit>
    suspend fun sendNudge(contactEmail: String): Result<Unit>
    suspend fun sendTyping(contactEmail: String): Result<Unit>
    suspend fun saveIncomingMessage(message: Message)
    suspend fun saveOutgoingMessage(message: Message)
    suspend fun markMessagesAsRead(contactEmail: String)
    suspend fun loadOfflineMessages(): Result<Unit>
    suspend fun getUnreadCount(contactEmail: String): Int
    fun observeIncomingMessages(): Flow<Message>
}
