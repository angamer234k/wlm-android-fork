package com.yourapp.wlm.domain.usecase

import com.yourapp.wlm.domain.repository.ContactRepository
import javax.inject.Inject

class AddContactUseCase @Inject constructor(
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(email: String, groupId: String? = null): Result<Unit> {
        if (email.isBlank()) return Result.failure(IllegalArgumentException("Email is required"))
        return contactRepository.addContact(email.trim(), groupId)
    }
}
