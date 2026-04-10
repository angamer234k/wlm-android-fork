package com.yourapp.wlm.domain.usecase

import com.yourapp.wlm.domain.repository.ContactRepository
import javax.inject.Inject

class RemoveContactUseCase @Inject constructor(
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(email: String): Result<Unit> {
        return contactRepository.removeContact(email)
    }
}
