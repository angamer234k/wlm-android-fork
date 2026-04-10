package com.yourapp.wlm.domain.repository

import com.yourapp.wlm.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(email: String, password: String, rememberMe: Boolean): Result<Unit>
    suspend fun logout()
    suspend fun autoLogin(): Result<Unit>
    fun isLoggedIn(): Flow<Boolean>
    fun getUserProfile(): Flow<UserProfile?>
    suspend fun clearSession()
}
