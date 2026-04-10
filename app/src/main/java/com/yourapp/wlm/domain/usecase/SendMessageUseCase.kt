package com.yourapp.wlm.domain.usecase

import com.yourapp.wlm.domain.repository.MessageRepository
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(contactEmail: String, body: String): Result<Unit> {
        if (contactEmail.isBlank()) return Result.failure(IllegalArgumentException("Contact email is required"))
        if (body.isBlank()) return Result.failure(IllegalArgumentException("Message body is required"))
        return messageRepository.sendMessage(contactEmail, body)
    }
}
