package com.yourapp.wlm.domain.usecase

import com.yourapp.wlm.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String, rememberMe: Boolean): Result<Unit> {
        if (email.isBlank()) return Result.failure(IllegalArgumentException("Email is required"))
        if (password.isBlank()) return Result.failure(IllegalArgumentException("Password is required"))
        return authRepository.login(email.trim(), password, rememberMe)
    }
}
