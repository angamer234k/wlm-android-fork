package com.yourapp.wlm.domain.usecase

import com.yourapp.wlm.domain.repository.PresenceRepository
import javax.inject.Inject

class SetPersonalMessageUseCase @Inject constructor(
    private val presenceRepository: PresenceRepository
) {
    suspend operator fun invoke(message: String): Result<Unit> {
        return presenceRepository.setPersonalMessage(message)
    }
}
