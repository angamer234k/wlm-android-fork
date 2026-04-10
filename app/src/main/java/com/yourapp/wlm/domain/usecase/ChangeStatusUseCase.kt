package com.yourapp.wlm.domain.usecase

import com.yourapp.wlm.domain.model.PresenceStatus
import com.yourapp.wlm.domain.repository.PresenceRepository
import javax.inject.Inject

class ChangeStatusUseCase @Inject constructor(
    private val presenceRepository: PresenceRepository
) {
    suspend operator fun invoke(status: PresenceStatus): Result<Unit> {
        val capabilities = calculateCapabilities(status)
        return presenceRepository.changeStatus(status, capabilities)
    }

    private fun calculateCapabilities(status: PresenceStatus): Long {
        return when (status) {
            PresenceStatus.ONLINE -> 0L
            PresenceStatus.AWAY -> 1L shl 0
            PresenceStatus.BUSY -> 1L shl 1
            PresenceStatus.BE_RIGHT_BACK -> 1L shl 2
            PresenceStatus.ON_THE_PHONE -> 1L shl 3
            PresenceStatus.OUT_TO_LUNCH -> 1L shl 4
            PresenceStatus.APPEAR_OFFLINE -> 1L shl 5
            PresenceStatus.IDLE -> 1L shl 6
            PresenceStatus.OFFLINE -> 0L
        }
    }
}
