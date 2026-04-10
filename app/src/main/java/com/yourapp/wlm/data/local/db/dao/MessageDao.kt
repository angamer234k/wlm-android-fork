package com.yourapp.wlm.data.local.db.dao

import androidx.room.*
import com.yourapp.wlm.data.local.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND isOutgoing = 0 AND isDelivered = 0")
    suspend fun getUnreadCount(conversationId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("UPDATE messages SET isDelivered = 1 WHERE conversationId = :conversationId AND isOutgoing = 0")
    suspend fun markMessagesAsRead(conversationId: String)

    @Query("SELECT * FROM messages WHERE isOffline = 1 ORDER BY timestamp ASC")
    suspend fun getOfflineMessages(): List<MessageEntity>

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: String)
}
