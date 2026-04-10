package com.yourapp.wlm.domain.repository

import com.yourapp.wlm.domain.model.PresenceStatus
import kotlinx.coroutines.flow.Flow

interface PresenceRepository {
    suspend fun changeStatus(status: PresenceStatus, capabilities: Long): Result<Unit>
    suspend fun setPersonalMessage(message: String): Result<Unit>
    fun getCurrentStatus(): Flow<PresenceStatus>
    fun getContactPresence(email: String): Flow<PresenceStatus>
    fun isTyping(contactEmail: String): Flow<Boolean>
    suspend fun startConnection(): Result<Unit>
    suspend fun stopConnection()
    fun getConnectionState(): Flow<ConnectionState>
}

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING
}
